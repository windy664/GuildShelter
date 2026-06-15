package org.windy.guildshelter.domain.layout;

/**
 * 公会世界布局参数，全部以 <b>chunk</b> 为单位（管理员配置，世界边界等由此派生）。
 *
 * @param plotChunks        单个地皮边长（chunk），即满级 slot 大小
 * @param roadChunks        地皮之间的间距/路宽（chunk）
 * @param mainCityHalfCells 主城占中心网格格的"半径"（格）：主城 = (2*half+1)² 个格，half≥0
 * @param plotDefaultChunks 地皮初始实占边长（chunk，1 级庄园），≤ plotChunks
 * @param plotChunksPerLevel 庄园每升一级实占边长增加多少 chunk
 * @param baseY             地面高度（生成器用；放这里方便统一配置）
 * @param marginChunks      世界边界在已分配范围外额外留的余量（chunk）
 */
public record LayoutConfig(
        int plotChunks,
        int roadChunks,
        int mainCityHalfCells,
        int plotDefaultChunks,
        int plotChunksPerLevel,
        int baseY,
        int marginChunks
) {

    public LayoutConfig {
        if (plotChunks < 1) {
            throw new IllegalArgumentException("plotChunks 必须 ≥1");
        }
        if (roadChunks < 0) {
            throw new IllegalArgumentException("roadChunks 必须 ≥0");
        }
        if (mainCityHalfCells < 0) {
            throw new IllegalArgumentException("mainCityHalfCells 必须 ≥0");
        }
        if (plotDefaultChunks < 1 || plotDefaultChunks > plotChunks) {
            throw new IllegalArgumentException("plotDefaultChunks 必须在 [1, plotChunks]");
        }
        if (plotChunksPerLevel < 0) {
            throw new IllegalArgumentException("plotChunksPerLevel 必须 ≥0");
        }
    }

    /** 网格节距：一个地皮 + 一条路。 */
    public int pitchChunks() {
        return plotChunks + roadChunks;
    }

    /** 主城占据的中心螺旋 slot 数 = (2*half+1)²；成员 slot 从这个 base 之后开始。 */
    public int mainCityBase() {
        return SpiralIndex.filledCount(mainCityHalfCells);
    }

    /** 给定庄园等级（1 起）的实占边长（chunk），封顶在 plotChunks。 */
    public int plotChunksByLevel(int manorLevel) {
        if (manorLevel < 1) {
            throw new IllegalArgumentException("manorLevel 必须 ≥1");
        }
        long size = (long) plotDefaultChunks + (long) (manorLevel - 1) * plotChunksPerLevel;
        return (int) Math.min(size, plotChunks);
    }

    /** 一份合理的默认配置：地皮 4×4 chunk、路 1 chunk、主城 3×3 格、初始 2×2 庄园每级 +1。 */
    public static LayoutConfig defaults() {
        return new LayoutConfig(4, 1, 1, 2, 1, 64, 2);
    }
}
