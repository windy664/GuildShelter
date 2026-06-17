package org.windy.guildshelter.adapter.bukkit.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 原版 Inventory GUI 实现：用 Bukkit 胸 GUI 渲染菜单。
 * 每个玩家同时只能打开一个菜单。
 */
public final class VanillaGuiProvider implements GuiProvider {

    /** 玩家 → 当前打开的菜单规格。 */
    private final Map<UUID, GuiSpec> openMenus = new HashMap<>();

    @Override
    public void openMenu(Player player, GuiSpec spec) {
        Inventory inv = Bukkit.createInventory(player, spec.totalSlots(), spec.title());
        for (Map.Entry<Integer, GuiItem> entry : spec.items().entrySet()) {
            int slot = entry.getKey();
            if (slot >= 0 && slot < spec.totalSlots()) {
                ItemStack item = entry.getValue().toItemStack();
                inv.setItem(slot, item);
            }
        }
        player.openInventory(inv);
        openMenus.put(player.getUniqueId(), spec);
    }

    @Override
    public void closeMenu(Player player) {
        player.closeInventory();
        openMenus.remove(player.getUniqueId());
    }

    @Override
    public boolean handleClick(Player player, int slot, GuiSpec spec) {
        GuiItem item = spec.items().get(slot);
        if (item == null || item.actionId().isEmpty()) {
            return false;
        }
        // 路由到 GuiRegistry 处理
        return GuiRegistry.getInstance().handleAction(player, item.actionId(), spec);
    }

    /** 获取玩家当前打开的菜单（无则 null）。 */
    public GuiSpec getOpenMenu(UUID playerId) {
        return openMenus.get(playerId);
    }

    /** 关闭时清理。 */
    public void onClose(UUID playerId) {
        openMenus.remove(playerId);
    }
}
