package org.windy.guildshelter.platform;

import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.adapter.bukkit.world.WaterBiomeSampler;
import org.windy.guildshelter.domain.port.ManorMover;
import org.windy.guildshelter.domain.port.ModDataMoverRegistry;
import org.windy.guildshelter.domain.port.TerrainPreparer;

/**
 * 载体接缝（PLAN_MODULES.md §4）：把抽象引导 {@code GuildShelterPlugin} 里所有"按载体分流"的决策收口到此。
 *
 * <p>两实现：{@code BukkitPlatformBindings}（bukkit-common，纯 Bukkit 默认）与 {@code NeoForge262Bindings}
 * （neoforge_26_2 模块，直接 new 原生类、无反射门控）。bukkit-common <b>不依赖</b> net.neoforged，故本接口
 * 只暴露平台中立的端口类型（{@link TerrainPreparer}/{@link ManorMover}/{@link WaterBiomeSampler}）。
 */
public interface PlatformBindings {

    /** 是否混合端（NeoForge/Forge 在场）。用于 UI auto 选择与启动日志。 */
    boolean isHybrid();

    /** 载体显示名（启动横幅用），如 "Bukkit 普通版" / "NeoForge 26.2 增强版"。 */
    String carrierName();

    /** 整地器：混合端原生（认模组方块、原生重光照）/ 纯 Bukkit（高度图）。构造参数同两实现的旧逻辑。 */
    TerrainPreparer terrain(JavaPlugin plugin, String roadBlock, String bridgeBlock, String bridgeRail,
                            boolean wallEnabled, String wallBlock, int wallHeight);

    /** 搬家用方块搬运器：混合端 chunk 级原生复制 / Bukkit WorldEdit clipboard。 */
    ManorMover manorMover(JavaPlugin plugin);

    /** 注册该载体特有的 mod 数据搬运器（如 RS2，仅混合端有）。纯 Bukkit 无操作。 */
    void registerModDataMovers(ModDataMoverRegistry registry, JavaPlugin plugin);

    /**
     * 注册原生保护监听（混合端 NeoForge 事件总线）。返回 {@code true} = 已注册原生，引导层据此
     * <b>跳过</b> Bukkit 的 ManorProtectionListener/ManorEntityListener/ManorFlagListener。
     * 纯 Bukkit 返回 {@code false}（由引导层注册 Bukkit 监听）。
     *
     * <p>调用时机：必须在 {@code GuildShelterPlugin} 已装配好 claimGuard/manorLookup/interactionPolicy
     * 之后——原生 hooks 经 {@code GuildShelterPlugin} 的静态访问器取用它们。
     */
    boolean registerNativeProtection(JavaPlugin plugin);

    /** 群系水域采样器（注入 WorldManager，零生成避开混合端装饰越界崩）。纯 Bukkit 返回 {@code null}（高度图兜底）。 */
    WaterBiomeSampler biomeSampler();
}
