package org.windy.guildshelter.domain.port.ui;

import java.util.List;

/**
 * 平台中立的菜单项：图标 + 文本 + 点击动作。
 *
 * <p>不含任何 Bukkit 类型——{@code ItemStack} 的构造在具体后端完成。整个 record 可序列化，
 * 因此能原样通过"插件 ↔ 模组"通道发给模组侧渲染（见 {@code ModChannelUi}）。
 *
 * @param icon     图标。
 * @param name     显示名（支持 § 颜色码）。
 * @param lore     描述行。
 * @param actionId 点击动作 id（路由到 {@link UiActionRouter} 的处理器，如 {@code "flag.toggle.pvp"}）；
 *                 空串表示不可点击的纯展示项。
 */
public record UiItem(UiIcon icon, String name, List<String> lore, String actionId) {

    /** 无 lore 的简单项。 */
    public static UiItem of(UiIcon icon, String name, String actionId) {
        return new UiItem(icon, name, List.of(), actionId);
    }

    /** 完整项。 */
    public static UiItem of(UiIcon icon, String name, List<String> lore, String actionId) {
        return new UiItem(icon, name, lore, actionId);
    }

    /** 不可点击的分隔/填充项。 */
    public static UiItem separator(UiIcon icon) {
        return new UiItem(icon, " ", List.of(), "");
    }

    /** 是否可点击（有动作 id）。 */
    public boolean clickable() {
        return actionId != null && !actionId.isEmpty();
    }
}
