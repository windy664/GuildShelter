package org.windy.guildshelter.domain.layout;

/**
 * 公会世界布局参数，全部以 <b>chunk</b> 为单位（管理员配置，世界边界等由此派生）。
 *
 * <p><b>主城会随公会等级成长</b>：占中心网格格的"半径"从 {@code mainCityHalfCellsInitial} 长到
 * {@code mainCityHalfCellsMax}。为避免成长时压到已分出的成员地皮，<b>所有预留/边界计算一律按 max 算</b>——
 * 中心永久按最大主城预留，成员 slot 从最大主城<b>之外</b>才开始铺；当前主城在预留区内由 initial 长到 max。
 *
 * @param plotChunks               单个地皮边长（chunk），即满级 slot 大小
 * @param roadChunks               地皮之间的间距/路宽（chunk）
 * @param mainCityHalfCellsInitial 主城初始"半径"（格，1 级公会）：主城 = (2*half+1)² 个格，half≥0
 * @param mainCityHalfCellsMax     主城最大"半径"（格，公会满级达到）：≥ initial；预留与边界按此值算
 * @param plotDefaultChunks        地皮初始实占边长（chunk，1 级庄园），≤ plotChunks
 * @param plotChunksPerLevel       庄园每升一级实占边长增加多少 chunk
 * @param baseY                    地面高度（生成器用；放这里方便统一配置）
 * @param marginChunks             世界边界在已分配范围外额外留的余量（chunk）
 */
public record LayoutConfig(
        int plotChunks,
        int roadChunks,
        int mainCityHalfCellsInitial,
        int mainCityHalfCellsMax,
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
        if (mainCityHalfCellsInitial < 0) {
            throw new IllegalArgumentException("mainCityHalfCellsInitial 必须 ≥0");
        }
        if (mainCityHalfCellsMax < mainCityHalfCellsInitial) {
            throw new IllegalArgumentException("mainCityHalfCellsMax 必须 ≥ initial");
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

    /**
     * 主城<b>按最大尺寸</b>占据的中心螺旋 slot 数 = (2*max+1)²；成员 slot 从这个 base 之后开始。
     * 永远按 max 预留，保证主城长大时不会压到成员地皮。
     */
    public int mainCityBase() {
        return SpiralIndex.filledCount(mainCityHalfCellsMax);
    }

    /**
     * 给定公会等级下当前主城的"半径"（格）：从 initial 线性长到 max，在公会满级 {@code maxGuildLevel} 时达到 max。
     * 与"按 max 预留"无关——这只是当前实际铺出来的主城范围。
     */
    public int cityHalfAtLevel(int guildLevel, int maxGuildLevel) {
        if (mainCityHalfCellsMax <= mainCityHalfCellsInitial || maxGuildLevel <= 1) {
            return mainCityHalfCellsInitial;
        }
        int g = Math.max(1, Math.min(guildLevel, maxGuildLevel));
        int grown = mainCityHalfCellsInitial
                + (int) Math.round((double) (mainCityHalfCellsMax - mainCityHalfCellsInitial)
                * (g - 1) / (maxGuildLevel - 1));
        return Math.min(grown, mainCityHalfCellsMax);
    }

    /** 给定庄园等级（1 起）的实占边长（chunk），封顶在 plotChunks。 */
    public int plotChunksByLevel(int manorLevel) {
        if (manorLevel < 1) {
            throw new IllegalArgumentException("manorLevel 必须 ≥1");
        }
        long size = (long) plotDefaultChunks + (long) (manorLevel - 1) * plotChunksPerLevel;
        return (int) Math.min(size, plotChunks);
    }

    /**
     * 一份合理的默认配置：地皮满级 15 chunk(240×240)、路 1 chunk、
     * 主城初始 3×3 格(half=1)成长到最大 5×5 格(half=2)、地皮初始 6 chunk(96×96)每级 +1(共 10 级)。
     */
    public static LayoutConfig defaults() {
        return new LayoutConfig(15, 1, 1, 2, 6, 1, 64, 2);
    }
}
