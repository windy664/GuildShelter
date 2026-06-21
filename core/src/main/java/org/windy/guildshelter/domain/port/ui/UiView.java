package org.windy.guildshelter.domain.port.ui;

import java.util.Map;

/**
 * 平台中立的菜单视图：一屏菜单的完整结构。由命令层（或 {@code Menus}）构造，交给 {@link UiBackend} 渲染。
 *
 * <p><b>序列化边界铁律：</b>{@link #items}/{@link #title}/{@link #id} 是纯数据，可过通道发给模组；
 * 而 {@link #context} 装的是服务端活对象（Manor/GuildWorld 等），<b>只在服务端进程内</b>给
 * {@link UiActionRouter} 的处理器用，<b>绝不跨通道</b>。模组侧回传点击时只带 {@code (menuId, actionId, 玩家)}，
 * 服务端按玩家当前位置重新解析 context。
 *
 * @param id      菜单 id（事件路由用，如 {@code "manor_info"}）。
 * @param title   标题（支持 § 颜色码）。
 * @param rows    行数（1-6，每行 9 格）。原版 Inventory 用；模组侧可忽略改用自由布局。
 * @param items   slot → 菜单项。
 * @param context 服务端上下文（不序列化，见类注释）。
 * @param page    当前页（多页菜单用，默认 0）。
 */
public record UiView(
        String id,
        String title,
        int rows,
        Map<Integer, UiItem> items,
        Map<String, Object> context,
        int page
) {
    public UiView(String id, String title, int rows, Map<Integer, UiItem> items, Map<String, Object> context) {
        this(id, title, rows, items, context, 0);
    }

    /** 总 slot 数（rows × 9）。 */
    public int totalSlots() {
        return rows * 9;
    }
}
