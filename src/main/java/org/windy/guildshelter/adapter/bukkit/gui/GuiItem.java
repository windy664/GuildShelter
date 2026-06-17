package org.windy.guildshelter.adapter.bukkit.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 菜单项：图标 + 名称 + 描述 + 点击动作。
 */
public record GuiItem(
    /** 图标材质。 */
    Material icon,
    /** 显示名（支持 § 颜色码）。 */
    String name,
    /** 描述文本（lore）。 */
    List<String> lore,
    /** 点击动作 ID（路由到处理方法，如 "flag.toggle.pvp"）。 */
    String actionId
) {
    /** 转换为 Bukkit ItemStack（用于原版 GUI）。 */
    public ItemStack toItemStack() {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 简单构造：无 lore。 */
    public static GuiItem of(Material icon, String name, String actionId) {
        return new GuiItem(icon, name, List.of(), actionId);
    }

    /** 完整构造。 */
    public static GuiItem of(Material icon, String name, List<String> lore, String actionId) {
        return new GuiItem(icon, name, lore, actionId);
    }

    /** 分隔线（不可点击）。 */
    public static GuiItem separator(Material icon) {
        return new GuiItem(icon, " ", List.of(), "");
    }
}
