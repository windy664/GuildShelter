package org.windy.guildshelter.adapter.bukkit.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.windy.guildshelter.domain.port.ui.UiView;

/**
 * 原版 Inventory 事件监听：拦截点击/关闭，路由到 {@link BukkitInventoryUi}。
 * 仅在 UI 后端为原版 Inventory 时有意义；模组后端的点击走通道回传，不经此监听器。
 */
public final class VanillaGuiListener implements Listener {

    private final BukkitInventoryUi backend;

    public VanillaGuiListener(BukkitInventoryUi backend) {
        this.backend = backend;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UiView view = backend.openView(player.getUniqueId());
        if (view == null) return;
        event.setCancelled(true); // 防止拿走物品
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= view.totalSlots()) return;
        backend.handleClick(player, slot, view);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            backend.onClose(player.getUniqueId());
        }
    }
}
