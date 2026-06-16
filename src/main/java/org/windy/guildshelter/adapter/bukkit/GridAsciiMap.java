package org.windy.guildshelter.adapter.bukkit;

import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.layout.SpiralIndex;
import org.windy.guildshelter.domain.model.GuildWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 把公会世界的螺旋网格渲染成控制台 ASCII 图，便于直观看清主城 / 已占地皮 / 空闲名额 / 容量边界的分布。
 *
 * <p>一格 = 一块地皮（不是 chunk），用<b>定宽数字编号</b>标注每个成员 slot，方便对上"已分配地皮 #N"的日志。
 * 窗口为以主城为中心、半径 = 当前边界环的方形。纯 ASCII，避免 Windows 控制台码页乱码。
 */
public final class GridAsciiMap {

    private GridAsciiMap() {
    }

    /**
     * @param occupiedSlots    已被占用的成员 slot（来自实际庄园记录，含退会留下的空缺判断）
     * @param capacity         当前公会等级的名额容量
     * @param currentCityHalf  当前公会等级下主城的实际"半径"（格）：≤ 预留(最大)半径
     */
    public static List<String> render(LayoutCalculator layout, GuildWorld gw,
                                      Set<Integer> occupiedSlots, int capacity, int currentCityHalf) {
        int base = layout.base();
        int reserved = Math.max(gw.allocatedSlots(), capacity);
        int r = layout.borderRingCells(reserved);

        // 定宽：能放下最大编号(capacity-1) + 已占标记 '*'
        int maxSlot = Math.max(0, capacity - 1);
        int width = String.valueOf(maxSlot).length() + 1;

        List<String> lines = new ArrayList<>();
        lines.add("== 公会世界 " + gw.worldName() + " 网格分布 ==");
        lines.add("  等级 " + gw.guildLevel() + " | 名额容量 " + capacity
                + " | 已占 " + occupiedSlots.size() + " | 主城半径 " + currentCityHalf
                + " | 边界半径 " + r + " 格");
        lines.add("  图例: C=当前主城  c=预留扩城  *N=已占地皮#N  N=空闲名额#N  (空白)=容量外");
        for (int gz = -r; gz <= r; gz++) {
            StringBuilder sb = new StringBuilder("  ");
            for (int gx = -r; gx <= r; gx++) {
                int s = SpiralIndex.toIndex(gx, gz);
                String cell;
                if (s < base) { // 中心预留(最大)主城区：按当前半径区分已建/预留
                    int ring = Math.max(Math.abs(gx), Math.abs(gz));
                    cell = ring <= currentCityHalf ? "C" : "c";
                } else {
                    int slot = s - base;
                    if (occupiedSlots.contains(slot)) {
                        cell = "*" + slot;          // 已生成的地皮：*编号
                    } else if (slot < capacity) {
                        cell = String.valueOf(slot); // 空闲名额：编号
                    } else {
                        cell = "";                   // 容量外：空白
                    }
                }
                sb.append(String.format("%" + width + "s", cell)).append(' '); // 右对齐定宽
            }
            lines.add(sb.toString());
        }
        return lines;
    }
}
