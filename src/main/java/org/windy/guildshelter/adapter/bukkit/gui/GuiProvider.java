package org.windy.guildshelter.adapter.bukkit.gui;

import org.bukkit.entity.Player;

/**
 * GUI 后端抽象：原版 Inventory 和外部 UI 模组共用此接口。
 * 运行时由 config 选择具体实现。
 */
public interface GuiProvider {

    /** 打开菜单。 */
    void openMenu(Player player, GuiSpec spec);

    /** 关闭菜单。 */
    void closeMenu(Player player);

    /** 处理点击。返回 true 表示已处理（应取消事件）。 */
    boolean handleClick(Player player, int slot, GuiSpec spec);
}
