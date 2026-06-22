package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.configuration.file.FileConfiguration;
import org.windy.guildshelter.domain.layout.LayoutConfig;
import org.windy.guildshelter.domain.model.TerrainPrepMode;
import org.windy.guildshelter.domain.rule.LevelRules;
import org.windy.guildshelter.persistence.StorageSettings;

/** 把 Bukkit 的 config.yml 解析成 domain 的配置对象。 */
public record GuildShelterConfig(LayoutConfig layout, LevelRules levels, TerrainPrepMode terrainPrep,
                                 StorageSettings storage, String proxyType, String serverName,
                                 PerformanceConfig performance, MoveConfig move, OceanReseedConfig oceanReseed,
                                 IrisConfig iris,
                                 CityWallConfig cityWall, CityLimits cityLimits,
                                 HologramSettings holograms) {

    /**
     * 主城悬浮字设置（需 DecentHolograms 软依赖）。
     * @param enabled    是否启用（且 DH 在场才真正生效）
     * @param maxPerGuild 每公会主城悬浮字数量上限
     */
    public record HologramSettings(boolean enabled, int maxPerGuild) {}

    /**
     * 主城围墙：沿<b>最大主城</b>外缘建一圈墙，只立在外侧是成员庄园（非路）的边上（贴路自动留口、不踩庄园）。
     *
     * @param enabled 是否启用（默认 true）
     * @param block   围墙方块 id（默认 {@code minecraft:cobblestone_wall}）
     * @param height  墙高（格，默认 1）
     */
    public record CityWallConfig(boolean enabled, String block, int height) {}

    /** 搬家配置。 */
    public record MoveConfig(boolean enabled, double cost, int cooldownDays) {}

    /**
     * 海洋换种子重建：首建公会营地时若主城网格 footprint 水占比过高，换随机种子重建（最多 maxAttempts 次），
     * 直到拿到陆地为主的世界。避免整片海洋导致铺路架桥成千上万列、压垮混合端区块/光照子系统而崩服。
     *
     * @param enabled       是否启用（默认 true）
     * @param maxWaterRatio 主城 footprint 水占比超过此值则重掷种子（0~1，默认 0.5）
     * @param maxAttempts   最多重建次数（默认 8），超过则接受最后一个并告警
     * @param sampleGrid    footprint 上的采样网格边数（gridN×gridN 个探点，默认 12）
     */
    public record OceanReseedConfig(boolean enabled, double maxWaterRatio, int maxAttempts, int sampleGrid) {}

    /**
     * 自然地形公会世界的生成开关。地貌/地表恒为原版自然生成，但<b>装饰(树/矿/花/结构)恒关</b>
     * ——既符合"家不该有矿"，又绕开混合端地物装饰阶段的 {@code -1} 越界崩，故装饰不做成开关。
     *
     * Iris 地形引擎联动：检测到 Iris 插件在场且 {@code enabled} → 公会自然世界用 Iris 生成（专业地形+高性能）；
     * 否则普通 vanilla normal 世界。软依赖，无需编译期依赖 Iris。
     *
     * @param enabled   是否在 Iris 存在时启用（默认 true）
     * @param dimension Iris 维度包名（默认 {@code overworld}）
     */
    public record IrisConfig(boolean enabled, String dimension) {}


    /**
     * 性能优化配置。
     *
     * @param quotas 统一配额表（优化量 + 机器），随庄园等级缩放 + 管理员增量；解析见 QuotaRegistry。
     */
    public record PerformanceConfig(
            org.windy.guildshelter.domain.rule.quota.QuotaRegistry quotas,
            int limitCheckSeconds, boolean dropCleanMode,
            boolean optimizeEnabled, String optimizeMode, int optimizeInactiveMinutes, int optimizeCheckSeconds, boolean keepSpawnLoaded,
            boolean statsEnabled, int statsBroadcastSeconds, int statsTopCount,
            double weightTileTick, double weightEntityTick, double weightDropTick, double weightChunkTick,
            boolean chunkUnloadEnabled, int chunkUnloadInactiveMinutes, int chunkUnloadCheckSeconds, boolean chunkUnloadKeepRoad
    ) {}

    /** 读等级系统的某个 int：优先 levels.yml 的 {@code key}，缺则回退 config.yml 旧键 {@code legacyKey}，再缺用 {@code def}。 */
    private static int lvl(FileConfiguration lv, String key, FileConfiguration cfg, String legacyKey, int def) {
        if (lv != null && lv.contains(key)) {
            return lv.getInt(key);
        }
        if (cfg.contains(legacyKey)) {
            return cfg.getInt(legacyKey);
        }
        return def;
    }

    /**
     * @param cfg 主配置 config.yml
     * @param lv  等级系统配置 levels.yml（庄园/公会等级独立成文件）；读不到时回退 config.yml 旧键再回退硬默认。
     */
    public static GuildShelterConfig from(FileConfiguration cfg, FileConfiguration lv) {
        int plotInitial = lvl(lv, "manor.initial-chunks", cfg, "member-plot.initial-chunks", 6);
        int plotMax = lvl(lv, "manor.max-chunks", cfg, "member-plot.max-chunks", 15);
        int plotGrow = lvl(lv, "manor.grow-per-level", cfg, "member-plot.grow-per-level", 1);

        LayoutConfig layout = new LayoutConfig(
                plotMax,                                       // 庄园满级边长
                cfg.getInt("road-chunks", 1),
                lvl(lv, "guild.main-city.initial-chunks", cfg, "main-city.initial-chunks", 3),
                lvl(lv, "guild.main-city.max-chunks", cfg, "main-city.max-chunks", 10),
                plotInitial,                                   // 庄园初始边长
                plotGrow,
                cfg.getInt("advanced.base-y", 64),
                cfg.getInt("advanced.margin-chunks", 2));

        // 庄园等级数：服主直接配 manor.max-level（想几级几级）；额度从初始线性涨到满级整块。
        // 缺省(<1)则回退按尺寸推导，兼容旧配置。
        int manorMaxLevel = lvl(lv, "manor.max-level", cfg, "member-plot.max-level", 0);
        if (manorMaxLevel < 1) {
            manorMaxLevel = plotGrow > 0 ? (plotMax - plotInitial) / plotGrow + 1 : 1;
        }
        LevelRules levels = new LevelRules(
                lvl(lv, "guild.max-level", cfg, "guild.max-level", 5),
                lvl(lv, "guild.members-per-level", cfg, "guild.members-per-level", 5),
                Math.max(1, manorMaxLevel));

        TerrainPrepMode prep;
        try {
            prep = TerrainPrepMode.valueOf(cfg.getString("terrain-prep", "CLEAR_VEGETATION").toUpperCase());
        } catch (IllegalArgumentException e) {
            prep = TerrainPrepMode.CLEAR_VEGETATION;
        }

        String proxyType = cfg.getString("proxy", "none").toLowerCase();
        String serverName = cfg.getString("server-name", "");

        // 跨服模式强制 MySQL
        String storageType = cfg.getString("storage.type", "sqlite");
        if (!proxyType.equals("none") && !storageType.equals("mysql")) {
            storageType = "mysql";
        }

        StorageSettings storage = new StorageSettings(
                storageType,
                cfg.getString("storage.mysql.host", "localhost"),
                cfg.getInt("storage.mysql.port", 3306),
                cfg.getString("storage.mysql.database", "guildshelter"),
                cfg.getString("storage.mysql.user", "root"),
                cfg.getString("storage.mysql.password", ""));

        // 庄园等级优化限制：levels.yml 的 manor.limits.<等级>.<维度>=上限（里程碑+向下继承）。
        // 维度 = 优化量枚举 id（drops/tiles/animal/hostile/mob/vehicle）或机器命名空间 id；非枚举者归为机器。
        java.util.Map<String, java.util.TreeMap<Integer, Integer>> perLevel = new java.util.HashMap<>();
        java.util.Set<String> machineIds = new java.util.HashSet<>();
        org.bukkit.configuration.ConfigurationSection limSec =
                lv == null ? null : lv.getConfigurationSection("manor.limits");
        if (limSec != null) {
            for (String lvlKey : limSec.getKeys(false)) {
                int level;
                try {
                    level = Integer.parseInt(lvlKey.trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                org.bukkit.configuration.ConfigurationSection row = limSec.getConfigurationSection(lvlKey);
                if (row == null) continue;
                for (String dim : row.getKeys(false)) {
                    String id = dim.toLowerCase(java.util.Locale.ROOT);
                    perLevel.computeIfAbsent(id, k -> new java.util.TreeMap<>()).put(level, row.getInt(dim, -1));
                    if (org.windy.guildshelter.domain.rule.OptimizationLimit.fromKey(id) == null) {
                        machineIds.add(id); // 非枚举维度 = 机器
                    }
                }
            }
        }
        // 机器展示名（提示文案，与等级无关）：levels.yml manor.machine-names.<id>=名
        java.util.Map<String, String> machineDisplay = new java.util.HashMap<>();
        org.bukkit.configuration.ConfigurationSection nmSec =
                lv == null ? null : lv.getConfigurationSection("manor.machine-names");
        if (nmSec != null) {
            for (String id : nmSec.getKeys(false)) {
                machineDisplay.put(id.toLowerCase(java.util.Locale.ROOT), nmSec.getString(id, id));
            }
        }
        org.windy.guildshelter.domain.rule.quota.QuotaRegistry quotas =
                new org.windy.guildshelter.domain.rule.quota.QuotaRegistry(perLevel, machineDisplay, machineIds);

        PerformanceConfig perf = new PerformanceConfig(
                quotas,
                cfg.getInt("performance.drop-cleanup.check-interval-seconds",
                        cfg.getInt("performance.limits.check-interval-seconds", 60)),
                "clean".equalsIgnoreCase(cfg.getString("performance.drop-cleanup.mode",
                        cfg.getString("performance.limits.drop-cleanup-mode", "clean"))),
                cfg.getBoolean("performance.optimize.enabled", false),
                cfg.getString("performance.optimize.mode", "world"),
                cfg.getInt("performance.optimize.inactive-minutes", 30),
                cfg.getInt("performance.optimize.check-interval-seconds", 300),
                cfg.getBoolean("performance.optimize.keep-spawn-loaded", true),
                cfg.getBoolean("performance.stats.enabled", false),
                cfg.getInt("performance.stats.broadcast-interval-seconds", 1800),
                cfg.getInt("performance.stats.top-count", 5),
                cfg.getDouble("performance.stats.weights.tile-tick", 0.005),
                cfg.getDouble("performance.stats.weights.entity-tick", 0.005),
                cfg.getDouble("performance.stats.weights.drop-tick", 0.0001),
                cfg.getDouble("performance.stats.weights.chunk-tick", 0),
                cfg.getBoolean("performance.chunk-unload.enabled", false),
                cfg.getInt("performance.chunk-unload.inactive-minutes", 15),
                cfg.getInt("performance.chunk-unload.check-interval-seconds", 120),
                cfg.getBoolean("performance.chunk-unload.keep-road-loaded", true));

        MoveConfig move = new MoveConfig(
                cfg.getBoolean("manor-move.enabled", true),
                cfg.getDouble("manor-move.cost", 10000),
                cfg.getInt("manor-move.cooldown-days", 7));

        OceanReseedConfig oceanReseed = new OceanReseedConfig(
                cfg.getBoolean("ocean-reseed.enabled", true),
                cfg.getDouble("ocean-reseed.max-water-ratio", 0.5),
                cfg.getInt("ocean-reseed.max-attempts", 8),
                cfg.getInt("ocean-reseed.sample-grid", 12));

        IrisConfig iris = new IrisConfig(
                cfg.getBoolean("iris.enabled", true),
                cfg.getString("iris.dimension", "overworld"));

        CityWallConfig cityWall = new CityWallConfig(
                cfg.getBoolean("city-wall.enabled", false), // 主城已缩小成中心一格、四面环路，围墙暂关
                cfg.getString("city-wall.block", "minecraft:cobblestone_wall"),
                cfg.getInt("city-wall.height", 1));

        CityLimits cityLimits = new CityLimits(
                cfg.getBoolean("main-city-limits.enabled", false),
                cfg.getInt("main-city-limits.max-dropped-items", -1),
                cfg.getInt("main-city-limits.max-tile-entities", -1),
                cfg.getInt("main-city-limits.max-animals", -1),
                cfg.getInt("main-city-limits.max-hostiles", -1),
                cfg.getInt("main-city-limits.max-mobs", -1),
                cfg.getInt("main-city-limits.max-vehicles", -1));

        HologramSettings holograms = new HologramSettings(
                cfg.getBoolean("main-city-holograms.enabled", true),
                cfg.getInt("main-city-holograms.max-per-guild", 5));

        return new GuildShelterConfig(layout, levels, prep, storage, proxyType, serverName, perf, move, oceanReseed, iris, cityWall, cityLimits, holograms);
    }
}
