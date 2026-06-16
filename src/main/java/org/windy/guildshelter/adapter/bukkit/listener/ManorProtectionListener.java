package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.windy.guildshelter.adapter.bukkit.ClaimGuard;

/**
 * 领地保护的 <b>Bukkit 后端</b>：把 {@link ClaimGuard} 接到 Bukkit 事件上，
 * 挡住非授权的破坏/放置/交互/倒水。<b>仅在纯 Bukkit 端注册</b>——混合端(NeoForge 在)
 * 改由 NeoForge EVENT_BUS 侧统一处理，避免双重拦截。
 */
public final class ManorProtectionListener implements Listener {

    private final ClaimGuard guard;

    public ManorProtectionListener(ClaimGuard guard) {
        this.guard = guard;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        guard(event.getPlayer(), event.getBlock(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        guard(event.getPlayer(), event.getBlock(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return; // 只管右键方块（开箱/门/拉杆/红石等）
        }
        guard(event.getPlayer(), event.getClickedBlock(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        guard(event.getPlayer(), event.getBlockClicked().getRelative(event.getBlockFace()),
                () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        guard(event.getPlayer(), event.getBlockClicked(), () -> event.setCancelled(true));
    }

    private void guard(Player player, Block block, Runnable cancel) {
        if (guard.allowed(player, block.getX(), block.getZ())) {
            return;
        }
        cancel.run();
        guard.notifyDenied(player);
    }
}
