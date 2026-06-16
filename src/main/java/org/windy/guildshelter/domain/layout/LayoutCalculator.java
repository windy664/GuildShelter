package org.windy.guildshelter.domain.layout;

import org.windy.guildshelter.domain.model.ChunkRegion;

import java.util.OptionalInt;

/**
 * 布局单一真相源：把 {@link LayoutConfig} + {@link SpiralIndex} 的数学集中在这里。
 * 生成器、保护监听、chunk 加载管理三处共用本类，保证"生成的地皮"与"判权限的地皮"永远一致。
 *
 * <p>坐标系：网格格 (gx,gz) 的地皮占 chunk [gx*pitch, gx*pitch+P-1]，其后 R 个 chunk 为路沟。
 * 主城占中心的 (2*half+1)² 个格（整格连续，含路沟），对应螺旋 slot [0, base)；成员地皮用
 * 螺旋 slot base, base+1, ... 紧凑向外铺。全部纯整数，无 GIS。
 */
public final class LayoutCalculator {

    private final LayoutConfig config;
    private final int pitch;
    private final int plot;
    private final int base;

    public LayoutCalculator(LayoutConfig config) {
        this.config = config;
        this.pitch = config.pitchChunks();
        this.plot = config.plotChunks();
        this.base = config.mainCityBase();
    }

    public LayoutConfig config() {
        return config;
    }

    /** 某 chunk 属于 主城 / 某成员地皮 / 路。 */
    public Classification classify(int chunkX, int chunkZ) {
        int gx = Math.floorDiv(chunkX, pitch);
        int gz = Math.floorDiv(chunkZ, pitch);
        int s = SpiralIndex.toIndex(gx, gz);
        if (s < base) {
            return Classification.mainCity();
        }
        int lx = Math.floorMod(chunkX, pitch);
        int lz = Math.floorMod(chunkZ, pitch);
        if (lx < plot && lz < plot) {
            boolean border = lx == 0 || lx == plot - 1 || lz == 0 || lz == plot - 1;
            return Classification.plot(s - base, border);
        }
        return Classification.road();
    }

    /** 便捷：该 chunk 若属于某成员地皮，返回其 slot。 */
    public OptionalInt slotAt(int chunkX, int chunkZ) {
        Classification c = classify(chunkX, chunkZ);
        return c.isPlot() ? OptionalInt.of(c.slot()) : OptionalInt.empty();
    }

    /**
     * 成员 slot 所属网格格的<b>道路条带</b>（该格的右侧竖条 + 底部横条，呈 L 形）。
     * 相邻格的条带彼此拼接成连续路网；用于给道路铺土径。roadChunks=0 时返回空。
     */
    public java.util.List<ChunkRegion> roadStripsFor(int slot) {
        if (config.roadChunks() <= 0) {
            return java.util.List.of();
        }
        ChunkRegion plotR = plotRegion(slot);
        int cellMinX = plotR.minChunkX();
        int cellMinZ = plotR.minChunkZ();
        int plotMaxX = plotR.maxChunkX();
        int plotMaxZ = plotR.maxChunkZ();
        int cellMaxX = cellMinX + pitch - 1;
        int cellMaxZ = cellMinZ + pitch - 1;
        ChunkRegion right = new ChunkRegion(plotMaxX + 1, cellMinZ, cellMaxX, cellMaxZ);
        ChunkRegion bottom = new ChunkRegion(cellMinX, plotMaxZ + 1, plotMaxX, cellMaxZ);
        return java.util.List.of(right, bottom);
    }

    /** 成员 slot → 其满级地皮的整 chunk 范围（P×P）。 */
    public ChunkRegion plotRegion(int slot) {
        if (slot < 0) {
            throw new IllegalArgumentException("slot < 0: " + slot);
        }
        SpiralIndex.GridCell cell = SpiralIndex.toCell(base + slot);
        int ox = cell.x() * pitch;
        int oz = cell.z() * pitch;
        return new ChunkRegion(ox, oz, ox + plot - 1, oz + plot - 1);
    }

    /**
     * 成员 slot + 庄园等级 → 当前实占范围（"半螺旋式"：锚定在满级地皮的<b>最小角</b>，
     * 从角落向 +x/+z 方向逐级向外扩，满级铺满整块；⊆ plotRegion）。
     *
     * <p>角落锚定让玩家地皮的原点固定、只朝一个方向长，规整可预测；避免居中算法的奇偶歪斜。
     */
    public ChunkRegion activeRegion(int slot, int manorLevel) {
        ChunkRegion full = plotRegion(slot);
        int active = config.plotChunksByLevel(manorLevel);
        int ox = full.minChunkX();
        int oz = full.minChunkZ();
        return new ChunkRegion(ox, oz, ox + active - 1, oz + active - 1);
    }

    /**
     * 主城<b>预留(最大)</b>整 chunk 范围（中心 (2*max+1) 个格全占，含路沟，连续）。
     * 这是按最大尺寸永久预留的中心区，成员地皮一律排在它之外；世界边界、出生点等都以它为基准。
     */
    public ChunkRegion mainCityRegion() {
        return cityRegion(config.mainCityHalfCellsMax());
    }

    /**
     * 给定公会等级下<b>当前实际</b>主城范围（从 initial 长到 max，⊆ {@link #mainCityRegion()}）。
     * 用于按等级铺城/整地/围墙；当前与最大之间那圈是"留给未来扩城"的预留空地。
     */
    public ChunkRegion currentCityRegion(int guildLevel, int maxGuildLevel) {
        return cityRegion(config.cityHalfAtLevel(guildLevel, maxGuildLevel));
    }

    private ChunkRegion cityRegion(int half) {
        int min = -half * pitch;
        int max = (half + 1) * pitch - 1;
        return new ChunkRegion(min, min, max, max);
    }

    /** 世界出生点所在方块（主城中心）。 */
    public int spawnBlockX() {
        return mainCityRegion().centerBlockX();
    }

    public int spawnBlockZ() {
        return mainCityRegion().centerBlockZ();
    }

    // ---- 世界边界（WorldBorder）派生 ----

    /**
     * 要把 {@code reservedSlots} 个成员地皮全部圈进去时，世界边界需覆盖到的外环（格）。
     * {@code reservedSlots} 由上层给出 = max(已分配, 当前公会等级的名额容量)，
     * 这样边界按"当前等级能容纳多少人"画出预留空地，公会升级放开更多名额时边界随之外扩。
     */
    public int borderRingCells(int reservedSlots) {
        return reservedSlots > 0
                ? SpiralIndex.ringOf(base + reservedSlots - 1)
                : config.mainCityHalfCellsMax(); // 无成员时也至少圈住预留(最大)主城
    }

    /** 世界边界中心方块 X（= 主城中心）。 */
    public int borderCenterBlockX() {
        return spawnBlockX();
    }

    public int borderCenterBlockZ() {
        return spawnBlockZ();
    }

    /** 世界边界全宽（方块），以中心向四周覆盖到外环地皮外沿 + margin（取偏宽的安全上界）。 */
    public double borderSizeBlocks(int reservedSlots) {
        int ring = borderRingCells(reservedSlots);
        int outerChunks = ring * pitch + plot + config.marginChunks();
        return (double) outerChunks * 2 * 16;
    }

    public int base() {
        return base;
    }

    public int pitchChunks() {
        return pitch;
    }
}
