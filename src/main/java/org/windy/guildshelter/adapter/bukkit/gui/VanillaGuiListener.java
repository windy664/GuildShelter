package org.windy.guildshelter.adapter.bukkit.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * 原版 GUI 事件监听：拦截点击和关闭，路由到 GuiProvider。
 */
public final class VanillaGuiListener implements Listener {

    private final VanillaGuiProvider provider;

    public VanillaGuiListener(VanillaGuiProvider provider) {
        this.provider = provider;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GuiSpec spec = provider.getOpenMenu(player.getUniqueId());
        if (spec == null) return;
        event.setCancelled(true); // 防止拿走物品
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= spec.totalSlots()) return;
        provider.handleClick(player, slot, spec);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            provider.onClose(player.getUniqueId());
        }
    }
}
