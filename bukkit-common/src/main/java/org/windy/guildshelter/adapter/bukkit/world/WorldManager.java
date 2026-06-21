package org.windy.guildshelter.adapter.bukkit.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.windy.guildshelter.adapter.bukkit.GuildShelterConfig.OceanReseedConfig;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.layout.SpiralIndex;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.TerrainPrepMode;
import org.windy.guildshelter.domain.port.WorldControl;
import org.windy.guildshelter.domain.rule.LevelRules;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * {@link WorldControl} 的 Bukkit 实现：每个公会一个<b>普通自然地形</b>世界（随机种子，各异）。
 *
 * <p>不挂自定义生成器——世界是原版自然地形。主城/庄园/路是 {@link LayoutCalculator} 在自然地形上的
 * 逻辑叠加；通过 {@link GuildWorld} 的 origin 偏移把整张网格平移到陆地上，避免主城落在海里。
 *
 * <p>在 Youer 上 {@code Bukkit.createWorld} 内部即 NeoForge 的 addLevel。所有方法须在主线程调用。
 */
public final class WorldManager implements WorldControl {

    /** 锚定陆地时最多探测多少个候选 chunk（螺旋向外）。 */
    private static final int MAX_LAND_PROBES = 200;

    private final LevelRules levels;
    private final OceanReseedConfig oceanReseed;
    private final Logger logger;
    /** 群系水域采样器（混合端注入；null = 纯 Bukkit，回退强制生成看地表液体）。见 {@link WaterBiomeSampler}。 */
    private WaterBiomeSampler biomeSampler;
    private org.windy.guildshelter.domain.port.GuildProvider guildProvider =
            org.windy.guildshelter.domain.port.GuildProvider.NONE; // 宿主人数上限来源，延迟注入

    public WorldManager(LevelRules levels, OceanReseedConfig oceanReseed, Logger logger) {
        this.levels = levels;
        this.oceanReseed = oceanReseed;
        this.logger = logger;
    }

    /** 注入群系水域采样器（混合端载体在装配时调；不调 = 纯 Bukkit 回退）。 */
    public void setBiomeSampler(WaterBiomeSampler sampler) {
        this.biomeSampler = sampler;
    }

    /** 注入宿主公会 provider（边界按宿主人数上限画预留区，与发地容量一致）。 */
    public void setGuildProvider(org.windy.guildshelter.domain.port.GuildProvider provider) {
        this.guildProvider = provider != null ? provider : org.windy.guildshelter.domain.port.GuildProvider.NONE;
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

        TerrainPrepMode mode = gw.terrainMode();
        // 真·首建判定：磁盘上还没有该世界文件夹。卸载后惰性重载时文件夹仍在，那时必须沿用已存档的
        // 种子/origin，绝不能换种子重建（会毁掉玩家已建造的存档）。
        boolean firstCreation = !worldFolderExists(gw.worldName());
        boolean naturalTerrain = mode != TerrainPrepMode.VOID && mode != TerrainPrepMode.FLAT;
        if (oceanReseed.enabled() && firstCreation && naturalTerrain) {
            return createNaturalWithReseed(gw);
        }

        // 修复点 1：获取主世界（通常是列表第一个，下标为0）作为模板，继承其群系和注册表数据。
        // 这是为了防止混合端（NeoForge）在新世界生成自然地形时因为找不到注册表映射而导致 IndexOutOfBoundsException (-1)
        World mainWorld = Bukkit.getWorlds().get(0);

        WorldCreator creator = new WorldCreator(gw.worldName())
                .copy(mainWorld) // <--- 关键修复：继承主世界的注册表
                .environment(World.Environment.NORMAL)
                .seed(gw.seed());

        // 按地形模式选生成器
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
            throw new IllegalStateException("创建公会营地失败: " + gw.worldName());
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
     * 自然地形世界的<b>首建</b>：建好后采样主城网格 footprint 的水占比，超阈值就换随机种子删库重建，
     * 直到拿到陆地为主的世界（或用尽 {@code maxAttempts}）。避免整片海洋让铺路架桥成千上万列、
     * 压垮混合端区块/光照子系统而崩服。返回带<b>最终种子 + 锚定 origin</b> 的记录，调用方负责持久化。
     */
    private GuildWorld createNaturalWithReseed(GuildWorld gw) {
        World mainWorld = Bukkit.getWorlds().get(0); // 继承主世界注册表，见 ensureWorld 修复点 1
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        int maxAttempts = Math.max(1, oceanReseed.maxAttempts());
        World world = null;
        int[] origin = null;
        for (int attempt = 1; ; attempt++) {
            world = Bukkit.createWorld(new WorldCreator(gw.worldName())
                    .copy(mainWorld)
                    .environment(World.Environment.NORMAL)
                    .seed(gw.seed()));
            if (world == null) {
                throw new IllegalStateException("创建公会营地失败: " + gw.worldName());
            }
            origin = anchorOnLand(world, layout);
            double waterRatio = sampleWaterRatio(world, gw.withOrigin(origin[0], origin[1]), layout);
            logger.info("[GuildShelter] " + gw.worldName() + " 首建尝试 " + attempt + "/" + maxAttempts
                    + " seed=" + gw.seed() + " 主城水占比=" + String.format(Locale.ROOT, "%.0f%%", waterRatio * 100));

            if (waterRatio <= oceanReseed.maxWaterRatio() || attempt >= maxAttempts) {
                if (waterRatio > oceanReseed.maxWaterRatio()) {
                    logger.warning("[GuildShelter] " + gw.worldName() + " 重建 " + maxAttempts
                            + " 次仍水占比偏高（" + String.format(Locale.ROOT, "%.0f%%", waterRatio * 100)
                            + "），接受当前世界。可调 ocean-reseed.max-attempts / max-water-ratio。");
                }
                break;
            }
            // 水太多：卸载（不存盘）+ 删世界文件夹 + 换随机种子重建
            logger.info("[GuildShelter] " + gw.worldName() + " 水占比过高，换种子重建…");
            Bukkit.unloadWorld(world, false);
            if (!deleteWorldFolder(world.getWorldFolder())) {
                logger.warning("[GuildShelter] 无法删除世界文件夹 " + world.getWorldFolder()
                        + "（可能文件占用），中止重建并接受当前世界。");
                world = Bukkit.createWorld(new WorldCreator(gw.worldName())
                        .copy(mainWorld).environment(World.Environment.NORMAL).seed(gw.seed()));
                origin = anchorOnLand(world, layout);
                break;
            }
            gw = gw.withSeed(ThreadLocalRandom.current().nextLong());
        }

        GuildWorld anchored = gw.withOrigin(origin[0], origin[1]);
        world.setSpawnLocation(safeSpawn(world, anchored));
        applyBorderTo(world, anchored);
        return anchored;
    }

    /** 公会营地文件夹是否已在磁盘上（用于区分"真·首建"与"卸载后惰性重载"）。 */
    private boolean worldFolderExists(String worldName) {
        return new File(Bukkit.getWorldContainer(), worldName).isDirectory();
    }

    /** 递归删除世界文件夹；全部删净返回 true。须先 {@code unloadWorld} 释放文件句柄。 */
    private boolean deleteWorldFolder(File dir) {
        if (dir == null || !dir.exists()) {
            return true;
        }
        boolean ok = true;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                ok &= child.isDirectory() ? deleteWorldFolder(child) : child.delete();
            }
        }
        return dir.delete() && ok;
    }

    /**
     * 采样主城整格 footprint（{@link LayoutCalculator#mainCityRegion()} = 中心一格 plot 区，含 origin 偏移）
     * 上 {@code gridN×gridN} 个均布探点的<b>水占比</b>。主城是必用核心区、成员庄园从其边缘向外长，
     * 故中心区水占比能代表整张网格是否泡海。
     *
     * <p><b>优先走 NeoForge 群系源采样（零生成）</b>：直接问种子这些列是不是海/河群系，<b>不加载/生成区块</b>，
     * 避开混合端+mod 在"地物装饰"阶段的 {@code -1} 越界崩（见 {@link org.windy.guildshelter.neoforge.NeoForgeBiomeSampler}）。
     * 纯 Bukkit 环境回退到强制生成看地表液体（无该 mod 装饰 bug）。
     */
    private double sampleWaterRatio(World world, GuildWorld gw, LayoutCalculator layout) {
        ChunkRegion city = layout.mainCityRegion();
        int ox = gw.originChunkX() << 4;
        int oz = gw.originChunkZ() << 4;
        int minX = city.minBlockX() + ox, maxX = city.maxBlockX() + ox;
        int minZ = city.minBlockZ() + oz, maxZ = city.maxBlockZ() + oz;
        int gridN = Math.max(2, oceanReseed.sampleGrid());
        int sampleY = layout.config().baseY();

        if (biomeSampler != null) {
            // 混合端只走群系采样：绝不回退到"强制生成"那条会触发地物装饰越界崩的路径。
            try {
                double r = biomeSampler.waterBiomeRatio(world.getName(), minX, maxX, minZ, maxZ, gridN, sampleY);
                if (r >= 0) {
                    return r; // 群系采样成功（零生成）
                }
                logger.warning("[GuildShelter] 群系采样找不到世界 " + world.getName() + "，跳过水占比判定（视为可接受，不重建）。");
            } catch (Throwable t) {
                logger.warning("[GuildShelter] 群系采样异常，跳过水占比判定（不重建）: " + t);
            }
            return 0.0;
        }
        // 纯 Bukkit（无 mod 装饰 bug）：强制生成看地表液体
        int liquid = 0, total = 0;
        for (int i = 0; i < gridN; i++) {
            int x = minX + (int) ((long) (maxX - minX) * i / (gridN - 1));
            for (int j = 0; j < gridN; j++) {
                int z = minZ + (int) ((long) (maxZ - minZ) * j / (gridN - 1));
                world.loadChunk(x >> 4, z >> 4, true);
                if (world.getHighestBlockAt(x, z).isLiquid()) {
                    liquid++;
                }
                total++;
            }
        }
        return total == 0 ? 0.0 : (double) liquid / total;
    }

    /**
     * 把网格原点锚定到陆地：从世界原版出生点所在 chunk 起螺旋向外探测，
     * 找到第一个"主城中心列不是水域"的位置，返回网格 origin 偏移（chunk）。
     *
     * <p>同 {@link #sampleWaterRatio} 优先走 NeoForge 群系采样（零生成），避开混合端"地物装饰"越界崩；
     * 纯 Bukkit 回退强制生成看地表液体。
     */
    private int[] anchorOnLand(World world, LayoutCalculator layout) {
        int layoutCityChunkX = layout.spawnBlockX() >> 4;
        int layoutCityChunkZ = layout.spawnBlockZ() >> 4;
        Location vanilla = world.getSpawnLocation();
        int baseChunkX = vanilla.getBlockX() >> 4;
        int baseChunkZ = vanilla.getBlockZ() >> 4;
        boolean neo = biomeSampler != null;
        int sampleY = layout.config().baseY();

        for (int i = 0; i < MAX_LAND_PROBES; i++) {
            SpiralIndex.GridCell cell = SpiralIndex.toCell(i);
            int cityChunkX = baseChunkX + cell.x();
            int cityChunkZ = baseChunkZ + cell.z();
            int cx = (cityChunkX << 4) + 8;
            int cz = (cityChunkZ << 4) + 8;
            if (!isWaterColumn(world, cx, cz, sampleY, neo)) {
                return new int[]{cityChunkX - layoutCityChunkX, cityChunkZ - layoutCityChunkZ};
            }
        }
        logger.warning("[GuildShelter] " + world.getName()
                + " 探测 " + MAX_LAND_PROBES + " 个 chunk 仍是海，回退到原版出生点。");
        return new int[]{baseChunkX - layoutCityChunkX, baseChunkZ - layoutCityChunkZ};
    }

    /**
     * 某列是不是水域。NeoForge 走零生成群系采样（异常/找不到世界则视为陆地，<b>绝不在混合端强制生成</b>，
     * 那是地物装饰越界崩的路径）；纯 Bukkit 才回退强制生成看地表液体。
     */
    private boolean isWaterColumn(World world, int blockX, int blockZ, int sampleY, boolean neo) {
        if (neo) {
            try {
                return biomeSampler.isWaterColumn(world.getName(), blockX, blockZ, sampleY);
            } catch (Throwable t) {
                return false; // 链接异常：当陆地处理，不强制生成
            }
        }
        world.loadChunk(blockX >> 4, blockZ >> 4, true);
        return world.getHighestBlockAt(blockX, blockZ).isLiquid();
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
        // 自适应边界：按【实际已分配成员】逐环生长 + 1 环缓冲（保证下一个加入者已在界内）。
        // 不再按等级/宿主上限预留满员大方框，世界紧贴实际占用（也更省加载区），且边界计算彻底脱离宿主插件。
        border.setSize(layout.adaptiveBorderSizeBlocks(gw.allocatedSlots(), 1));
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

        // 修复点 2：高版本 API 需要为自定义生成器提供合法的群系，避免读取空注册表抛出越界异常
        @Override
        public org.bukkit.generator.BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
            return new org.bukkit.generator.BiomeProvider() {
                @Override
                public org.bukkit.block.Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                    return org.bukkit.block.Biome.THE_VOID;
                }

                @Override
                public java.util.List<org.bukkit.block.Biome> getBiomes(WorldInfo worldInfo) {
                    return java.util.List.of(org.bukkit.block.Biome.THE_VOID);
                }
            };
        }
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