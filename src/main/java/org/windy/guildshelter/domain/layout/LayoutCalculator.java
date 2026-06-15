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

    /** 成员 slot + 庄园等级 → 当前实占范围（在满级地皮内居中的子方形，⊆ plotRegion）。 */
    public ChunkRegion activeRegion(int slot, int manorLevel) {
        ChunkRegion full = plotRegion(slot);
        int active = config.plotChunksByLevel(manorLevel);
        int margin = (plot - active) / 2;
        int ox = full.minChunkX() + margin;
        int oz = full.minChunkZ() + margin;
        return new ChunkRegion(ox, oz, ox + active - 1, oz + active - 1);
    }

    /** 主城整 chunk 范围（中心 (2*half+1) 个格全占，含路沟，连续）。 */
    public ChunkRegion mainCityRegion() {
        int half = config.mainCityHalfCells();
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
     * 已分配 {@code allocatedSlots} 个成员地皮、公会等级 {@code guildLevel} 时，
     * 世界边界需覆盖到的外环（格）。取"成员分配所需"与"公会等级下限"的较大者。
     */
    public int borderRingCells(int allocatedSlots, int guildLevel) {
        int memberRing = allocatedSlots > 0
                ? SpiralIndex.ringOf(base + allocatedSlots - 1)
                : config.mainCityHalfCells();
        int levelRing = config.mainCityHalfCells() + Math.max(guildLevel - 1, 0);
        return Math.max(memberRing, levelRing);
    }

    /** 世界边界中心方块 X（= 主城中心）。 */
    public int borderCenterBlockX() {
        return spawnBlockX();
    }

    public int borderCenterBlockZ() {
        return spawnBlockZ();
    }

    /** 世界边界全宽（方块），以中心向四周覆盖到外环地皮外沿 + margin（取偏宽的安全上界）。 */
    public double borderSizeBlocks(int allocatedSlots, int guildLevel) {
        int ring = borderRingCells(allocatedSlots, guildLevel);
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
