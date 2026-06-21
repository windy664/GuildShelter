package org.windy.guildshelter.domain.port.ui;

/**
 * 平台中立的图标标识。
 *
 * <p>故意<b>不</b>引用 {@code org.bukkit.Material}：易变的物品枚举/Inventory API 只在具体后端
 * （{@code BukkitInventoryUi}）里解析。模组侧后端可把同一个 {@link #key} 当成命名空间 id 渲染。
 *
 * @param key             图标标识。约定写物品名（不区分大小写），如 {@code "book"}、{@code "player_head"}；
 *                        允许带命名空间前缀 {@code "minecraft:diamond"}，由后端自行剥离/解析。
 * @param customModelData 自定义模型数据；{@code <= 0} 表示无。供资源包/模组 UI 选贴图用。
 */
public record UiIcon(String key, int customModelData) {

    public static UiIcon of(String key) {
        return new UiIcon(key, 0);
    }

    public static UiIcon of(String key, int customModelData) {
        return new UiIcon(key, customModelData);
    }
}
