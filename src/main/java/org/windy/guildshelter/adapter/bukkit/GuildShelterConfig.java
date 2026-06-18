package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.configuration.file.FileConfiguration;
import org.windy.guildshelter.domain.layout.LayoutConfig;
import org.windy.guildshelter.domain.model.TerrainPrepMode;
import org.windy.guildshelter.domain.rule.LevelRules;
import org.windy.guildshelter.persistence.StorageSettings;

/** 把 Bukkit 的 config.yml 解析成 domain 的配置对象。 */
public record GuildShelterConfig(LayoutConfig layout, LevelRules levels, TerrainPrepMode terrainPrep,
                                 StorageSettings storage, String proxyType, String serverName,
                                 PerformanceConfig performance, MoveConfig move) {

    /** 搬家配置。 */
    public record MoveConfig(boolean enabled, double cost, int cooldownDays) {}


    /** 性能优化配置。 */
    public record PerformanceConfig(
            int maxDroppedItems, int maxTileEntities, int limitCheckSeconds, boolean dropCleanMode,
            boolean optimizeEnabled, String optimizeMode, int optimizeInactiveMinutes, int optimizeCheckSeconds, boolean keepSpawnLoaded,
            boolean statsEnabled, int statsBroadcastSeconds, int statsTopCount,
            double weightTileTick, double weightEntityTick, double weightDropTick, double weightChunkTick,
            boolean chunkUnloadEnabled, int chunkUnloadInactiveMinutes, int chunkUnloadCheckSeconds, boolean chunkUnloadKeepRoad
    ) {}

    public static GuildShelterConfig from(FileConfiguration cfg) {
        int plotInitial = cfg.getInt("member-plot.initial-chunks", 6);
        int plotMax = cfg.getInt("member-plot.max-chunks", 15);
        int plotGrow = cfg.getInt("member-plot.grow-per-level", 1);

        LayoutConfig layout = new LayoutConfig(
                plotMax,                                       // 地皮满级边长
                cfg.getInt("road-chunks", 1),
                cfg.getInt("main-city.initial-cells", 1),
                cfg.getInt("main-city.max-cells", 2),
                plotInitial,                                   // 地皮初始边长
                plotGrow,
                cfg.getInt("advanced.base-y", 64),
                cfg.getInt("advanced.margin-chunks", 2));

        // 庄园物理满级 = 从初始长到满级所需级数，由地皮尺寸自动推导（避免与尺寸脱节）。
        int manorMaxLevel = plotGrow > 0 ? (plotMax - plotInitial) / plotGrow + 1 : 1;
        LevelRules levels = new LevelRules(
                cfg.getInt("guild.max-level", 5),
                cfg.getInt("guild.members-per-level", 5),
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

        PerformanceConfig perf = new PerformanceConfig(
                cfg.getInt("performance.limits.max-dropped-items", 500),
                cfg.getInt("performance.limits.max-tile-entities", 2000),
                cfg.getInt("performance.limits.check-interval-seconds", 60),
                "clean".equalsIgnoreCase(cfg.getString("performance.limits.drop-cleanup-mode", "clean")),
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

        return new GuildShelterConfig(layout, levels, prep, storage, proxyType, serverName, perf, move);
    }
}
