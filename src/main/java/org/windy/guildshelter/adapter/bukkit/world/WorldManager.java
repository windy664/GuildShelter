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
import org.windy.guildshelter.domain.port.WorldControl;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
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

    private final LayoutCalculator layout;
    private final Logger logger;

    public WorldManager(LayoutCalculator layout, Logger logger) {
        this.layout = layout;
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
        World world = Bukkit.createWorld(new WorldCreator(gw.worldName())
                .environment(World.Environment.NORMAL)
                .seed(gw.seed()));
        if (world == null) {
            throw new IllegalStateException("创建公会世界失败: " + gw.worldName());
        }
        int[] origin = anchorOnLand(world);
        GuildWorld anchored = gw.withOrigin(origin[0], origin[1]);
        world.setSpawnLocation(safeSpawn(world, anchored));
        applyBorderTo(world, anchored);
        return anchored;
    }

    /**
     * 把网格原点锚定到陆地：从世界原版出生点所在 chunk 起螺旋向外探测，
     * 找到第一个"主城中心列不是液体"的位置，返回网格 origin 偏移（chunk）。
     */
    private int[] anchorOnLand(World world) {
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
        WorldBorder border = world.getWorldBorder();
        double cx = layout.borderCenterBlockX() + (gw.originChunkX() << 4) + 0.5;
        double cz = layout.borderCenterBlockZ() + (gw.originChunkZ() << 4) + 0.5;
        border.setCenter(cx, cz);
        border.setSize(layout.borderSizeBlocks(gw.allocatedSlots(), gw.guildLevel()));
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
