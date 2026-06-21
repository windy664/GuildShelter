package org.windy.guildshelter.platform;

import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.adapter.bukkit.BukkitManorMover;
import org.windy.guildshelter.adapter.bukkit.world.BukkitTerrainPreparer;
import org.windy.guildshelter.adapter.bukkit.world.WaterBiomeSampler;
import org.windy.guildshelter.domain.port.ManorMover;
import org.windy.guildshelter.domain.port.ModDataMoverRegistry;
import org.windy.guildshelter.domain.port.TerrainPreparer;

/**
 * 纯 Bukkit 载体的默认接缝实现（普通版用；也是混合端接缝的兜底语义）。
 * 不触任何 net.neoforged：整地走高度图、搬家走 WorldEdit clipboard、无原生保护、无群系采样、无 mod 搬运器。
 */
public final class BukkitPlatformBindings implements PlatformBindings {

    @Override
    public boolean isHybrid() {
        return false;
    }

    @Override
    public String carrierName() {
        return "Bukkit 普通版";
    }

    @Override
    public TerrainPreparer terrain(JavaPlugin plugin, String roadBlock, String bridgeBlock, String bridgeRail,
                                   boolean wallEnabled, String wallBlock, int wallHeight) {
        return new BukkitTerrainPreparer(plugin, roadBlock, bridgeBlock, bridgeRail,
                wallEnabled, wallBlock, wallHeight);
    }

    @Override
    public ManorMover manorMover(JavaPlugin plugin) {
        return new BukkitManorMover(plugin, plugin.getLogger());
    }

    @Override
    public void registerModDataMovers(ModDataMoverRegistry registry, JavaPlugin plugin) {
        // 纯 Bukkit：无模组数据搬运（RS2 等只在混合端有）。
    }

    @Override
    public boolean registerNativeProtection(JavaPlugin plugin) {
        return false; // 纯 Bukkit：由引导层注册 Bukkit 保护监听。
    }

    @Override
    public WaterBiomeSampler biomeSampler() {
        return null; // 纯 Bukkit：WorldManager 回退高度图。
    }
}
