package org.windy.guildshelter.adapter.bukkit.gui;

import java.util.List;
import java.util.Map;

/**
 * 菜单规格：定义菜单的结构、内容和上下文。
 * 由命令层构造，传给 GuiProvider 渲染。
 */
public record GuiSpec(
    /** 菜单 ID（用于事件路由，如 "manor_info"、"flag_editor"）。 */
    String id,
    /** 标题（支持 § 颜色码）。 */
    String title,
    /** 行数（1-6，每行 9 格）。 */
    int rows,
    /** slot → 菜单项映射。 */
    Map<Integer, GuiItem> items,
    /** 上下文数据（如 manor、guild 等，供点击处理用）。 */
    Map<String, Object> context,
    /** 当前页码（多页菜单用，默认 0）。 */
    int page
) {
    public GuiSpec(String id, String title, int rows, Map<Integer, GuiItem> items, Map<String, Object> context) {
        this(id, title, rows, items, context, 0);
    }

    /** 总 slot 数。 */
    public int totalSlots() {
        return rows * 9;
    }
}
