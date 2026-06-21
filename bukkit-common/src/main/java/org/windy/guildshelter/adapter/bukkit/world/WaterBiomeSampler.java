package org.windy.guildshelter.adapter.bukkit.world;

/**
 * 零生成的群系水域采样接缝（PLAN_MODULES.md §4/§5）。
 *
 * <p>混合端（NeoForge/Forge）能直接问种子某列/某区是不是海/河群系而<b>不加载/生成区块</b>，
 * 避开混合端+mod 在"地物装饰"阶段的 {@code -1} 越界崩。纯 Bukkit 无此能力，{@link WorldManager}
 * 持空引用并回退到强制生成看地表液体。具体实现（如 {@code NeoForgeBiomeSampler} 的包装）由载体模块注入，
 * 故 bukkit-common 不直接引用 net.neoforged。
 */
public interface WaterBiomeSampler {

    /**
     * 群系采样某矩形区域（方块坐标，含端点）的水占比，{@code gridN×gridN} 均布探点。
     * @return [0,1] 水占比；找不到世界返回 &lt;0（调用方据此跳过判定）。
     */
    double waterBiomeRatio(String world, int minX, int maxX, int minZ, int maxZ, int gridN, int sampleY);

    /** 群系采样某列是否水域（零生成）。链接异常时实现可返回 false（当陆地处理）。 */
    boolean isWaterColumn(String world, int blockX, int blockZ, int sampleY);
}
