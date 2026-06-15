package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.configuration.file.FileConfiguration;
import org.windy.guildshelter.domain.layout.LayoutConfig;
import org.windy.guildshelter.domain.model.TerrainPrepMode;
import org.windy.guildshelter.domain.rule.LevelRules;

/** 把 Bukkit 的 config.yml 解析成 domain 的配置对象。 */
public record GuildShelterConfig(LayoutConfig layout, LevelRules levels, TerrainPrepMode terrainPrep) {

    public static GuildShelterConfig from(FileConfiguration cfg) {
        LayoutConfig layout = new LayoutConfig(
                cfg.getInt("layout.plot-chunks", 4),
                cfg.getInt("layout.road-chunks", 1),
                cfg.getInt("layout.main-city-half-cells", 1),
                cfg.getInt("layout.plot-default-chunks", 2),
                cfg.getInt("layout.plot-chunks-per-level", 1),
                cfg.getInt("layout.base-y", 64),
                cfg.getInt("layout.margin-chunks", 2));

        LevelRules levels = new LevelRules(
                cfg.getInt("levels.max-guild-level", 10),
                cfg.getInt("levels.manor-levels-per-guild-level", 1),
                cfg.getInt("levels.manor-max-level-cap", 10));

        TerrainPrepMode prep;
        try {
            prep = TerrainPrepMode.valueOf(cfg.getString("terrain-prep", "CLEAR_VEGETATION").toUpperCase());
        } catch (IllegalArgumentException e) {
            prep = TerrainPrepMode.CLEAR_VEGETATION;
        }
        return new GuildShelterConfig(layout, levels, prep);
    }
}
