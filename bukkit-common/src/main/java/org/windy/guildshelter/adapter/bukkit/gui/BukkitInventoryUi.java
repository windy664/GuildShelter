package org.windy.guildshelter.adapter.bukkit.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.windy.guildshelter.domain.port.ui.UiBackend;
import org.windy.guildshelter.domain.port.ui.UiIcon;
import org.windy.guildshelter.domain.port.ui.UiItem;
import org.windy.guildshelter.domain.port.ui.UiActionRouter;
import org.windy.guildshelter.domain.port.ui.UiView;
import org.windy.guildshelter.domain.port.ui.UiViewer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 原版 Inventory UI 后端——<b>易变 Bukkit API 的唯一隔离区</b>。
 *
 * <p>本类是整个项目里唯一允许出现 {@code org.bukkit.inventory.*}/{@code Material}/{@code openInventory}
 * 的 UI 代码。{@link UiView}/{@link UiItem}/{@link UiIcon} 的平台中立模型在这里翻译成 Bukkit 胸 GUI。
 * MC/Paper 改 Inventory API（26.x 正在大改）时，改动半径止于此文件，命令层与模组后端零感知。
 *
 * <p>每个玩家同一时刻只持有一个打开的菜单。
 */
public final class BukkitInventoryUi implements UiBackend {

    /** 玩家 → 当前打开的视图。 */
    private final Map<UUID, UiView> openMenus = new HashMap<>();
    private final UiActionRouter router;

    public BukkitInventoryUi(UiActionRouter router) {
        this.router = router;
    }

    @Override
    public void open(UiViewer viewer, UiView view) {
        Player player = Bukkit.getPlayer(viewer.id());
        if (player == null) return;
        Inventory inv = Bukkit.createInventory(player, view.totalSlots(), view.title());
        for (Map.Entry<Integer, UiItem> entry : view.items().entrySet()) {
            int slot = entry.getKey();
            if (slot >= 0 && slot < view.totalSlots()) {
                inv.setItem(slot, toItemStack(entry.getValue()));
            }
        }
        player.openInventory(inv);
        openMenus.put(viewer.id(), view);
    }

    @Override
    public void close(UiViewer viewer) {
        Player player = Bukkit.getPlayer(viewer.id());
        if (player != null) player.closeInventory();
        openMenus.remove(viewer.id());
    }

    @Override
    public boolean available() {
        return true; // 原版后端永远可用，作为兜底。
    }

    // ── 供 VanillaGuiListener 回调 ───────────────────────────────────────────

    /** 玩家当前打开的视图（无则 null）。 */
    UiView openView(UUID playerId) {
        return openMenus.get(playerId);
    }

    /** 关闭时清理记录。 */
    void onClose(UUID playerId) {
        openMenus.remove(playerId);
    }

    /** 处理一次点击：解析 slot 上的动作并路由。返回是否已处理（应取消事件）。 */
    boolean handleClick(Player player, int slot, UiView view) {
        UiItem item = view.items().get(slot);
        if (item == null || !item.clickable()) return false;
        UiViewer viewer = new UiViewer(player.getUniqueId(), player.getName());
        return router.handle(viewer, item.actionId(), view);
    }

    // ── 中立模型 → Bukkit ────────────────────────────────────────────────────

    private ItemStack toItemStack(UiItem item) {
        Material mat = Material.matchMaterial(stripNamespace(item.icon().key()));
        if (mat == null) mat = Material.PAPER;
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(item.name());
            if (item.lore() != null && !item.lore().isEmpty()) {
                meta.setLore(item.lore());
            }
            int cmd = item.icon().customModelData();
            if (cmd > 0) {
                try {
                    meta.setCustomModelData(cmd);
                } catch (Throwable ignored) {
                    // 老/新版本签名差异，自定义模型非关键，忽略。
                }
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** {@code "minecraft:diamond"} → {@code "DIAMOND"}，无命名空间则原样。 */
    private static String stripNamespace(String key) {
        int idx = key.indexOf(':');
        return idx >= 0 ? key.substring(idx + 1) : key;
    }
}
