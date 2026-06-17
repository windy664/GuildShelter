package org.windy.guildshelter.adapter.bukkit;

import org.windy.guildshelter.domain.layout.Classification;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.port.ManorRepository;

import java.util.List;

/**
 * 合并感知的 chunk 归类器：包装 {@link LayoutCalculator}，当 classify 返回 ROAD 时
 * 检查该 chunk 是否位于两块已合并地皮之间的路带上——如果是，返回主地皮的 PLOT 归属。
 *
 * <p>合并方向判定：两个相邻 slot 的网格格，若 gx 相同则为上下合并（中间横路），
 * 若 gz 相同则为左右合并（中间竖路）。路带宽 = pitch - plot 个 chunk。
 *
 * <p>用法：替代直接调 {@code layout.classify()}，保护监听器/命令等上层统一走本类。
 */
public final class MergeAwareClassifier {

    private final LayoutCalculator layout;
    private final ManorRepository manors;
    private final GuildId guild;

    public MergeAwareClassifier(LayoutCalculator layout, ManorRepository manors, GuildId guild) {
        this.layout = layout;
        this.manors = manors;
        this.guild = guild;
    }

    /** 原始 classify（不含 merge 感知）。 */
    public Classification classifyRaw(int chunkX, int chunkZ) {
        return layout.classify(chunkX, chunkZ);
    }

    /** 合并感知的 classify：ROAD chunk 若在合并路带上，返回主地皮的 PLOT。 */
    public Classification classify(int chunkX, int chunkZ) {
        Classification raw = layout.classify(chunkX, chunkZ);
        if (raw.type() != org.windy.guildshelter.domain.layout.RegionType.ROAD) {
            return raw; // 主城/地皮直接返回
        }
        // 检查该 ROAD chunk 是否在某对合并的路带上
        int pitch = layout.pitchChunks();
        int gx = Math.floorDiv(chunkX, pitch);
        int gz = Math.floorDiv(chunkZ, pitch);
        int lx = Math.floorMod(chunkX, pitch);
        int lz = Math.floorMod(chunkZ, pitch);
        int plot = layout.config().plotChunks();

        // 检查四个可能的相邻格对：左-右、上-下
        // 左-右合并：当前格是右侧路带（lx >= plot），检查左邻格与当前格是否合并
        if (lx >= plot && lz < plot) {
            int leftSlot = slotOf(gx - 1, gz);
            int rightSlot = slotOf(gx, gz);
            if (leftSlot >= 0 && rightSlot >= 0 && isMerged(leftSlot, rightSlot)) {
                return Classification.plot(leftSlot, false); // 路归左边主地皮
            }
            if (leftSlot >= 0 && rightSlot >= 0 && isMerged(rightSlot, leftSlot)) {
                return Classification.plot(rightSlot, false); // 路归右边主地皮
            }
        }
        // 上-下合并：当前格是底部路带（lz >= plot），检查上邻格与当前格是否合并
        if (lz >= plot && lx < plot) {
            int topSlot = slotOf(gx, gz - 1);
            int bottomSlot = slotOf(gx, gz);
            if (topSlot >= 0 && bottomSlot >= 0 && isMerged(topSlot, bottomSlot)) {
                return Classification.plot(topSlot, false);
            }
            if (topSlot >= 0 && bottomSlot >= 0 && isMerged(bottomSlot, topSlot)) {
                return Classification.plot(bottomSlot, false);
            }
        }
        // 角落（右下角）：可能被左-右或上-下合并覆盖，上面已处理过，这里不需要额外逻辑
        return raw;
    }

    /** 网格格 → slot（负值 = 主城或无效）。 */
    private int slotOf(int gx, int gz) {
        int s = org.windy.guildshelter.domain.layout.SpiralIndex.toIndex(gx, gz);
        int base = layout.base();
        return s >= base ? s - base : -1;
    }

    /** primarySlot 是否吸收了 absorbedSlot。 */
    private boolean isMerged(int primarySlot, int absorbedSlot) {
        List<Integer> merged = manors.getMergedSlots(guild, primarySlot);
        return merged.contains(absorbedSlot);
    }

    /** 底层 LayoutCalculator（需要原始计算时用）。 */
    public LayoutCalculator raw() {
        return layout;
    }
}
