package org.windy.guildshelter.domain.layout;

/**
 * 公会营地布局参数，全部以 <b>chunk</b> 为单位（管理员配置，世界边界等由此派生）。
 *
 * <p><b>主城和成员庄园同款算法</b>：主城就是<b>中心那一格(cell 0)</b>里的一块"庄园"，按 chunk 配置、
 * 角落锚定、随公会等级从 {@code mainCityInitialChunks} 长到 {@code mainCityMaxChunks}（封顶 ≤ plotChunks，
 * 以容于单格）。成员 slot 从 cell 0 之外的螺旋格（1,2,…）开始铺；预留/边界按 max 算，升级随之外扩。
 *
 * @param plotChunks            单个庄园边长（chunk），即满级 slot 大小
 * @param roadChunks            庄园之间的间距/路宽（chunk）
 * @param mainCityInitialChunks 主城初始边长（chunk，1 级公会），∈ [1, mainCityMaxChunks]
 * @param mainCityMaxChunks     主城最大边长（chunk，公会满级达到），∈ [initial, plotChunks]
 * @param plotDefaultChunks     庄园初始实占边长（chunk，1 级庄园），≤ plotChunks
 * @param plotChunksPerLevel    庄园每升一级实占边长增加多少 chunk
 * @param baseY                 地面高度（生成器用；放这里方便统一配置）
 * @param marginChunks          世界边界在已分配范围外额外留的余量（chunk）
 */
public record LayoutConfig(
        int plotChunks,
        int roadChunks,
        int mainCityInitialChunks,
        int mainCityMaxChunks,
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
        if (mainCityInitialChunks < 1) {
            throw new IllegalArgumentException("mainCityInitialChunks 必须 ≥1");
        }
        if (mainCityMaxChunks < mainCityInitialChunks) {
            throw new IllegalArgumentException("mainCityMaxChunks 必须 ≥ initial");
        }
        if (mainCityMaxChunks > plotChunks) {
            throw new IllegalArgumentException("mainCityMaxChunks 必须 ≤ plotChunks（主城须容于中心单格）");
        }
        if (plotDefaultChunks < 1 || plotDefaultChunks > plotChunks) {
            throw new IllegalArgumentException("plotDefaultChunks 必须在 [1, plotChunks]");
        }
        if (plotChunksPerLevel < 0) {
            throw new IllegalArgumentException("plotChunksPerLevel 必须 ≥0");
        }
    }

    /** 网格节距：一个庄园 + 一条路。 */
    public int pitchChunks() {
        return plotChunks + roadChunks;
    }

    /**
     * 主城占据的中心螺旋 slot 数。主城就是<b>中心一格(cell 0)</b>，故恒为 1；成员 slot 从 cell 1 起。
     */
    public int mainCityBase() {
        return 1;
    }

    /**
     * 给定公会等级下当前主城的实占边长（chunk）：从 initial 线性长到 max，在公会满级 {@code maxGuildLevel} 时达到 max。
     * 与"按 max 预留"无关——这只是当前实际铺出来的主城范围。
     */
    public int cityChunksAtLevel(int guildLevel, int maxGuildLevel) {
        if (mainCityMaxChunks <= mainCityInitialChunks || maxGuildLevel <= 1) {
            return mainCityInitialChunks;
        }
        int g = Math.max(1, Math.min(guildLevel, maxGuildLevel));
        int grown = mainCityInitialChunks
                + (int) Math.round((double) (mainCityMaxChunks - mainCityInitialChunks)
                * (g - 1) / (maxGuildLevel - 1));
        return Math.min(grown, mainCityMaxChunks);
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
     * 给定庄园等级的<b>解锁额度上限</b>（可拥有多少个已解锁 chunk）。等级数由配置 {@code member-plot.max-level}
     * 直接决定（{@code maxLevel} 传入），额度从<b>初始额度</b>（初始解锁正方形面积 = {@code plotDefaultChunks²}）
     * <b>线性均摊到满级整块</b>（{@code plotChunks²}）：等级越多每级涨得越细，满级正好够铺满整块。
     *
     * @param manorLevel 当前庄园等级（1 起）
     * @param maxLevel   庄园最高等级（来自 {@code LevelRules.manorMaxLevel()}）
     */
    public int quotaAtLevel(int manorLevel, int maxLevel) {
        int initial = plotDefaultChunks * plotDefaultChunks; // 初始额度
        int cap = plotChunks * plotChunks;                   // 满级整块封顶（解锁不能超出 plotRegion）
        if (maxLevel <= 1 || manorLevel >= maxLevel) {
            return cap;
        }
        int l = Math.max(1, manorLevel);
        long q = initial + Math.round((double) (cap - initial) * (l - 1) / (maxLevel - 1));
        return (int) Math.min(Math.max(q, initial), cap);
    }

    /** 庄园初始（1 级）解锁的正方形边长 = {@code plotDefaultChunks}。首次分配即解锁这块。 */
    public int initialUnlockSide() {
        return plotDefaultChunks;
    }

    /**
     * 主城<b>解锁额度上限</b>（会长能解锁多少个主城 chunk）：随<b>公会等级</b>从初始额度
     * （{@code mainCityInitialChunks²}）线性均摊到满级整块（{@code mainCityMaxChunks²}，主城单格上限）。
     * 与庄园 {@link #quotaAtLevel} 同构，但口径是公会等级。
     */
    public int cityQuotaAtLevel(int guildLevel, int maxGuildLevel) {
        int initial = mainCityInitialChunks * mainCityInitialChunks;
        int cap = mainCityMaxChunks * mainCityMaxChunks;
        if (maxGuildLevel <= 1 || guildLevel >= maxGuildLevel) {
            return cap;
        }
        int l = Math.max(1, guildLevel);
        long q = initial + Math.round((double) (cap - initial) * (l - 1) / (maxGuildLevel - 1));
        return (int) Math.min(Math.max(q, initial), cap);
    }

    /** 主城建会时初始解锁的角落正方形边长 = {@code mainCityInitialChunks}。 */
    public int initialCityUnlockSide() {
        return mainCityInitialChunks;
    }

    /**
     * 一份合理的默认配置：庄园满级 15 chunk(240×240)、路 1 chunk、
     * 主城初始 6 chunk(96×96)成长到最大 15 chunk(240×240)、庄园初始 6 chunk(96×96)每级 +1(共 10 级)。
     */
    public static LayoutConfig defaults() {
        return new LayoutConfig(15, 1, 6, 15, 6, 1, 64, 2);
    }
}
