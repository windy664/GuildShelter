package org.windy.guildshelter.adapter.bukkit.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.windy.guildshelter.adapter.bukkit.GuildShelterConfig.IrisConfig;
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
    private final IrisConfig iris;
    private final Logger logger;
    /** 群系水域采样器（混合端注入；null = 纯 Bukkit，回退强制生成看地表液体）。见 {@link WaterBiomeSampler}。 */
    private WaterBiomeSampler biomeSampler;
    private org.windy.guildshelter.domain.port.GuildProvider guildProvider =
            org.windy.guildshelter.domain.port.GuildProvider.NONE; // 宿主人数上限来源，延迟注入
    /** 插件引用（异步建 Iris 世界的调度器需要；setter 注入，未注入则 Iris 异步路径退化为同步并告警）。 */
    private org.bukkit.plugin.Plugin plugin;

    public WorldManager(LevelRules levels, OceanReseedConfig oceanReseed,
                        IrisConfig iris, Logger logger) {
        this.levels = levels;
        this.oceanReseed = oceanReseed;
        this.iris = iris;
        this.logger = logger;
    }

    /** 注入插件（装配时调；异步建 Iris 世界用其调度器）。 */
    public void setPlugin(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean lazilyGenerated(String worldName) {
        // Iris 异步生成、不预生成区块 → 视为惰性世界，建会后延迟铺路/锚地。
        return irisActive();
    }

    @Override
    public void ensureWorldAsync(GuildWorld gw, java.util.function.Consumer<GuildWorld> onReady, Runnable onError) {
        boolean naturalTerrain = gw.terrainMode() != TerrainPrepMode.VOID && gw.terrainMode() != TerrainPrepMode.FLAT;
        boolean irisFirstBuild = naturalTerrain && irisActive()
                && Bukkit.getWorld(gw.worldName()) == null && !worldFolderExists(gw.worldName());
        if (!irisFirstBuild) {
            // 非 Iris / 已存在世界：同步，行为不变。失败也回调 onError 解除在途，避免卡死建造中。
            final GuildWorld ready;
            try {
                ready = ensureWorld(gw);
            } catch (RuntimeException e) {
                onError.run();
                throw e;
            }
            onReady.accept(ready);
            return;
        }
        if (plugin == null) {
            // 没注入 plugin 无法异步调度；退回同步会在主线程触发 Iris "cannot create on main thread"。明确告警并中止。
            logger.severe("[GuildShelter] 建 Iris 世界需异步调度但未注入 plugin，跳过: " + gw.worldName());
            onError.run();
            return;
        }
        // 照搬 Iris 命令做法：在【异步线程】调 IrisToolbelt...create()（Iris 内部自己 submit 回主线程做 addLevel）。
        // 建好后回主线程设出生点 + 边界 + onReady（建会后续）。绝不在主线程阻塞等待（会与 Iris 的主线程任务死锁）。
        logger.info("[GuildShelter] 使用 Iris 异步管线创建世界(维度 '" + iris.dimension() + "'): " + gw.worldName());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final World w;
            try {
                w = org.windy.guildshelter.adapter.bukkit.world.iris.IrisWorldCreator.create(
                        gw.worldName(), iris.dimension(), gw.seed());
            } catch (Throwable t) {
                logger.severe("[GuildShelter] Iris 建世界失败，建会中止: " + t);
                Bukkit.getScheduler().runTask(plugin, onError); // 回主线程解除在途，允许修好后重试
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                LayoutCalculator layout = new LayoutCalculator(gw.layout());
                // Iris 固定原点 0,0；出生点只存坐标（不加载区块），真正落点由玩家传送时 safeSpawn 处理。
                w.setSpawnLocation(new Location(w, layout.spawnBlockX() + 0.5, 80, layout.spawnBlockZ() + 0.5));
                GuildWorld anchored = gw.withOrigin(0, 0);
                applyBorderTo(w, anchored);
                onReady.accept(anchored);
            });
        });
    }

    /** Iris 是否在场且启用（轻量判断，不构建生成器、不打日志）。用于建世界路径选择与 reseed 门控。 */
    private boolean irisActive() {
        if (!iris.enabled()) {
            return false;
        }
        org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("Iris");
        return p != null && p.isEnabled();
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
        // Iris 在场时绝不走 reseed：Iris 地形是服主选定的维度，不该为主城水占比就删了整个专业世界重建
        // （默认 overworld 用 vanilla 群系带 IS_OCEAN 标签，会真的触发删建最多 maxAttempts 次）。直接建一次。
        if (!irisActive() && oceanReseed.enabled() && firstCreation && naturalTerrain) {
            return createNaturalWithReseed(gw);
        }

        // Iris 首建必须走异步的 ensureWorldAsync（Iris 禁止主线程 create()）。同步 ensureWorld 到这说明调用方
        // 没走异步路径——明确报错而非在主线程调 Iris（那会抛 "cannot create on main thread" 且更难定位）。
        if (naturalTerrain && irisActive() && Bukkit.getWorld(gw.worldName()) == null && !worldFolderExists(gw.worldName())) {
            throw new IllegalStateException("Iris 世界须经 ensureWorldAsync 异步创建，不能同步 ensureWorld: " + gw.worldName());
        }
        World world;
        {
            // 修复点 1：获取主世界（通常是列表第一个，下标为0）作为模板，继承其群系和注册表数据。
            // 防止混合端（NeoForge）在新世界生成自然地形时因找不到注册表映射而 IndexOutOfBoundsException(-1)。
            World mainWorld = Bukkit.getWorlds().get(0);
            WorldCreator creator = new WorldCreator(gw.worldName())
                    .copy(mainWorld) // 继承主世界的注册表
                    .environment(World.Environment.NORMAL)
                    .seed(gw.seed());
            if (mode == TerrainPrepMode.VOID) {
                creator.generator(new VoidChunkGenerator());
                logger.info("[GuildShelter] 创建虚空世界: " + gw.worldName());
            } else if (mode == TerrainPrepMode.FLAT) {
                // 超平坦：基岩+泥土x2+草方块，与 SelfHomeMain 的默认 NormalType=2 一致
                creator.generatorSettings("minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block;minecraft:plains");
                logger.info("[GuildShelter] 创建超平坦世界: " + gw.worldName());
            } else {
                logger.info("[GuildShelter] 创建自然地形世界(普通 normal): " + gw.worldName());
            }
            world = Bukkit.createWorld(creator);
        }
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
        } else if (irisActive()) {
            // Iris：惰性生成，建世界时绝不锚地/强制生成出生点（那会同步生成大批未生成区块）。
            // 用固定原点；出生点只存坐标(setSpawnLocation 不加载区块)，真正落点由玩家传送时的 safeSpawn 处理。
            origin = new int[]{0, 0};
            LayoutCalculator layout = new LayoutCalculator(gw.layout());
            world.setSpawnLocation(new Location(world, layout.spawnBlockX() + 0.5, 80, layout.spawnBlockZ() + 0.5));
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
        // 仅非 Iris 普通 normal 世界到这（Iris 在 ensureWorld 被 !irisActive() 门控挡在外）。
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
                    + " seed=" + gw.seed()
                    + " 主城水占比=" + String.format(Locale.ROOT, "%.0f%%", waterRatio * 100));

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
        // 不存盘卸载：本方法两个调用方(公会解散 / admin delete)都是【丢弃】公会，世界记录都删了，
        // 再 save=true 同步刷全部已加载区块纯属浪费(Iris 大世界尤其卡主线程)。save=false 直接卸。
        // 注：惰性卸载(WorldOptimizer)用自己的 save=true 路径，不经此方法。
        return Bukkit.unloadWorld(world, false);
    }
}