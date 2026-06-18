package org.windy.guildshelter.adapter.bukkit.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.layout.SpiralIndex;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.TerrainPrepMode;
import org.windy.guildshelter.domain.port.WorldControl;
import org.windy.guildshelter.domain.rule.LevelRules;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * {@link WorldControl} 的 Bukkit 实现：每个公会一个<b>普通自然地形</b>世界（随机种子，各异）。
 *
 * <p>不挂自定义生成器——世界是原版自然地形。主城/地皮/路是 {@link LayoutCalculator} 在自然地形上的
 * 逻辑叠加；通过 {@link GuildWorld} 的 origin 偏移把整张网格平移到陆地上，避免主城落在海里。
 *
 * <p>在 Youer 上 {@code Bukkit.createWorld} 内部即 NeoForge 的 addLevel。所有方法须在主线程调用。
 */
public final class WorldManager implements WorldControl {

    /** 锚定陆地时最多探测多少个候选 chunk（螺旋向外）。 */
    private static final int MAX_LAND_PROBES = 200;

    private final LevelRules levels;
    private final Logger logger;

    public WorldManager(LevelRules levels, Logger logger) {
        this.levels = levels;
        this.logger = logger;
    }

    @Override
    public String worldName(GuildId guild) {
        String safe = guild.value().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return "guild_" + safe;
    }

    @Override
    public GuildWorld ensureWorld(GuildWorld gw) {
        World existing = Bukkit.getWorld(gw.worldName());
        if (existing != null) {
            applyBorderTo(existing, gw);
            return gw;
        }
        WorldCreator creator = new WorldCreator(gw.worldName())
                .environment(World.Environment.NORMAL)
                .seed(gw.seed());

        // 按地形模式选生成器
        TerrainPrepMode mode = gw.terrainMode();
        if (mode == TerrainPrepMode.VOID) {
            creator.generator(new VoidChunkGenerator());
            logger.info("[GuildShelter] 创建虚空世界: " + gw.worldName());
        } else if (mode == TerrainPrepMode.FLAT) {
            // 超平坦：基岩+泥土x2+草方块，与 SelfHomeMain 的默认 NormalType=2 一致
            creator.generatorSettings("minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block;minecraft:plains");
            logger.info("[GuildShelter] 创建超平坦世界: " + gw.worldName());
        }

        World world = Bukkit.createWorld(creator);
        if (world == null) {
            throw new IllegalStateException("创建公会世界失败: " + gw.worldName());
        }

        // VOID/FLAT 模式不需要锚定陆地（世界已可控），直接用原点
        int[] origin;
        if (mode == TerrainPrepMode.VOID) {
            origin = new int[]{0, 0};
            // 虚空世界：在主城位置铺一层草方块作为出生平台
            LayoutCalculator layout = new LayoutCalculator(gw.layout());
            int sx = layout.spawnBlockX();
            int sz = layout.spawnBlockZ();
            int platformSize = 8; // 8x8 出生平台
            for (int x = sx - platformSize; x <= sx + platformSize; x++) {
                for (int z = sz - platformSize; z <= sz + platformSize; z++) {
                    world.getBlockAt(x, 63, z).setType(org.bukkit.Material.GRASS_BLOCK, false);
                }
            }
            world.setSpawnLocation(new Location(world, sx + 0.5, 64, sz + 0.5));
        } else if (mode == TerrainPrepMode.FLAT) {
            origin = new int[]{0, 0};
            world.setSpawnLocation(new Location(world, 0.5, 4, 0.5));
        } else {
            origin = anchorOnLand(world, new LayoutCalculator(gw.layout()));
            world.setSpawnLocation(safeSpawn(world, gw.withOrigin(origin[0], origin[1])));
        }

        GuildWorld anchored = gw.withOrigin(origin[0], origin[1]);
        applyBorderTo(world, anchored);
        return anchored;
    }

    /**
     * 把网格原点锚定到陆地：从世界原版出生点所在 chunk 起螺旋向外探测，
     * 找到第一个"主城中心列不是液体"的位置，返回网格 origin 偏移（chunk）。
     */
    private int[] anchorOnLand(World world, LayoutCalculator layout) {
        int layoutCityChunkX = layout.spawnBlockX() >> 4;
        int layoutCityChunkZ = layout.spawnBlockZ() >> 4;
        Location vanilla = world.getSpawnLocation();
        int baseChunkX = vanilla.getBlockX() >> 4;
        int baseChunkZ = vanilla.getBlockZ() >> 4;

        for (int i = 0; i < MAX_LAND_PROBES; i++) {
            SpiralIndex.GridCell cell = SpiralIndex.toCell(i);
            int cityChunkX = baseChunkX + cell.x();
            int cityChunkZ = baseChunkZ + cell.z();
            int cx = (cityChunkX << 4) + 8;
            int cz = (cityChunkZ << 4) + 8;
            world.loadChunk(cityChunkX, cityChunkZ, true); // 强制生成后判断
            if (!world.getHighestBlockAt(cx, cz).isLiquid()) {
                return new int[]{cityChunkX - layoutCityChunkX, cityChunkZ - layoutCityChunkZ};
            }
        }
        logger.warning("[GuildShelter] " + world.getName()
                + " 探测 " + MAX_LAND_PROBES + " 个 chunk 仍是海，回退到原版出生点。");
        return new int[]{baseChunkX - layoutCityChunkX, baseChunkZ - layoutCityChunkZ};
    }

    /** 主城中心列的安全出生位置（含 origin 偏移）：强制生成所在区块后取地表最高点上方一格。 */
    public Location safeSpawn(World world, GuildWorld gw) {
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        int sx = (layout.spawnBlockX()) + (gw.originChunkX() << 4);
        int sz = (layout.spawnBlockZ()) + (gw.originChunkZ() << 4);
        world.loadChunk(sx >> 4, sz >> 4, true);
        int sy = world.getHighestBlockYAt(sx, sz) + 1;
        return new Location(world, sx + 0.5, sy, sz + 0.5);
    }

    @Override
    public void applyBorder(GuildWorld gw) {
        World world = Bukkit.getWorld(gw.worldName());
        if (world != null) {
            applyBorderTo(world, gw);
        }
    }

    private void applyBorderTo(World world, GuildWorld gw) {
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        WorldBorder border = world.getWorldBorder();
        double cx = layout.borderCenterBlockX() + (gw.originChunkX() << 4) + 0.5;
        double cz = layout.borderCenterBlockZ() + (gw.originChunkZ() << 4) + 0.5;
        border.setCenter(cx, cz);
        // 边界圈住 max(已分配, 当前等级名额容量) 个 slot：升级放开更多名额时即可见到预留的空地。
        int reserved = Math.max(gw.allocatedSlots(), levels.maxMembers(gw.guildLevel()));
        border.setSize(layout.borderSizeBlocks(reserved));
    }

    /** 虚空生成器：所有 chunk 为空气，用于 VOID 地形模式。 */
    private static final class VoidChunkGenerator extends ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise() { return false; }
        @Override
        public boolean shouldGenerateSurface() { return false; }
        @Override
        public boolean shouldGenerateBedrock() { return false; }
        @Override
        public boolean shouldGenerateCaves() { return false; }
        @Override
        public boolean shouldGenerateDecorations() { return false; }
        @Override
        public boolean shouldGenerateMobs() { return false; }
        @Override
        public boolean shouldGenerateStructures() { return false; }
    }

    @Override
    public boolean unloadGuild(GuildId guild) {
        World world = Bukkit.getWorld(worldName(guild));
        if (world == null) {
            return true;
        }
        return Bukkit.unloadWorld(world, true);
    }
}
