package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.configuration.file.FileConfiguration;
import org.windy.guildshelter.domain.layout.LayoutConfig;
import org.windy.guildshelter.domain.model.TerrainPrepMode;
import org.windy.guildshelter.domain.rule.LevelRules;
import org.windy.guildshelter.persistence.StorageSettings;

/** 把 Bukkit 的 config.yml 解析成 domain 的配置对象。 */
public record GuildShelterConfig(LayoutConfig layout, LevelRules levels, TerrainPrepMode terrainPrep,
                                 StorageSettings storage, String proxyType, String serverName) {

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

        return new GuildShelterConfig(layout, levels, prep, storage, proxyType, serverName);
    }
}
