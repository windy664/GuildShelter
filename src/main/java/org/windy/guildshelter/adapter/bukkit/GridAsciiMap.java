package org.windy.guildshelter.adapter.bukkit;

import org.windy.guildshelter.domain.layout.Classification;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.GuildWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 把公会世界渲染成控制台 <b>chunk 级</b> ASCII 图：1 字符 = 1 chunk（区域过大时按比例降采样），
 * 直接用 {@link LayoutCalculator#classify} 上色，直观看清主城 / 路 / 各成员地皮的 chunk 分布。
 *
 * <p>坐标系为<b>布局坐标</b>（(0,0)=主城锚定角，与 origin 无关），故只依赖 classify + slot 占用，纯 ASCII。
 */
public final class GridAsciiMap {

    /** 单行最多字符数：超出则按 step 降采样，避免刷屏。 */
    private static final int MAX_CHARS = 64;

    private GridAsciiMap() {
    }

    /**
     * @param occupiedSlots     已被占用的成员 slot（来自实际庄园记录）
     * @param capacity          当前公会等级的名额容量
     * @param currentCityChunks 当前公会等级下主城的实占边长（chunk）：仅表头显示
     */
    public static List<String> render(LayoutCalculator layout, GuildWorld gw,
                                      Set<Integer> occupiedSlots, int capacity, int currentCityChunks) {
        int pitch = layout.pitchChunks();
        int reserved = Math.max(gw.allocatedSlots(), capacity);
        int r = layout.borderRingCells(reserved);          // 边界半径（格）
        // 窗口(chunk)：覆盖 [-r..r] 格 → chunk [-r*pitch .. (r+1)*pitch-1]
        int minC = -r * pitch;
        int maxC = (r + 1) * pitch - 1;
        int span = maxC - minC + 1;
        int step = Math.max(1, (span + MAX_CHARS - 1) / MAX_CHARS); // 1 字符代表 step×step chunk

        List<String> lines = new ArrayList<>();
        lines.add("== 公会世界 " + gw.worldName() + " 区块图(1字符=" + (step == 1 ? "1" : step + "x" + step) + " chunk) ==");
        lines.add("  等级 " + gw.guildLevel() + " | 名额容量 " + capacity
                + " | 已占 " + occupiedSlots.size() + " | 主城 " + currentCityChunks + " chunk"
                + " | 边界半径 " + r + " 格");
        lines.add("  图例: C=主城  #=已占地皮  +=空闲名额  .=路  (空白)=未开发/容量外");
        for (int cz = minC; cz <= maxC; cz += step) {
            StringBuilder sb = new StringBuilder("  ");
            for (int cx = minC; cx <= maxC; cx += step) {
                sb.append(symbol(layout, cx, cz, occupiedSlots, capacity));
            }
            lines.add(sb.toString());
        }
        return lines;
    }

    /** 单个 chunk(布局坐标) → 符号。 */
    private static char symbol(LayoutCalculator layout, int cx, int cz, Set<Integer> occ, int capacity) {
        Classification c = layout.classify(cx, cz);
        if (c.isMainCity()) {
            return 'C';
        }
        if (c.isRoad()) {
            return '.';
        }
        if (c.isPlot()) {
            int slot = c.slot();
            if (occ.contains(slot)) {
                return '#';     // 已占地皮
            }
            return slot < capacity ? '+' : ' '; // 空闲名额 / 容量外
        }
        return ' ';
    }
}
