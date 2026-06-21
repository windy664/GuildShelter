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
import org.bukkit.inventory.InventoryHolder;
import org.windy.guildshelter.adapter.bukkit.ClaimGuard;
import org.windy.guildshelter.adapter.bukkit.InteractionPolicy;
import org.windy.guildshelter.adapter.bukkit.Messages;
import org.windy.guildshelter.adapter.bukkit.Permissions;
import org.windy.guildshelter.domain.flag.InteractCategory;

/**
 * 领地保护的 <b>Bukkit 后端</b>：把 {@link ClaimGuard} 接到 Bukkit 事件上，
 * 挡住非授权的破坏/放置/倒水（硬保护，访客一律不能改）。右键交互改走
 * {@link InteractionPolicy}——按类(use/container)对访客放宽。实体交互(展示框/载具)见
 * {@code ManorEntityListener}。<b>仅在纯 Bukkit 端注册</b>——混合端(NeoForge 在)改由
 * NeoForge EVENT_BUS 侧统一处理，避免双重拦截。
 */
public final class ManorProtectionListener implements Listener {

    private final ClaimGuard guard;
    private final InteractionPolicy policy;

    public ManorProtectionListener(ClaimGuard guard, InteractionPolicy policy) {
        this.guard = guard;
        this.policy = policy;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        guard(event.getPlayer(), event.getBlock(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        guard(player, block, () -> event.setCancelled(true));
        if (event.isCancelled()) {
            return; // 建造权限已拦
        }
        // 主城禁放名单：即便有建造权，名单方块在主城也挡(防主城改作生产用地)。OP/admin 放行布景。
        if (!player.isOp() && !Permissions.hasAdminPerm(player, Permissions.ADMIN)
                && guard.cityPlacementBlocked(block.getWorld().getName(), block.getX(), block.getZ(),
                        block.getType().getKey().toString())) {
            event.setCancelled(true);
            player.sendMessage(Messages.get("listener.city_block_blocked"));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null) {
            return; // 只管右键方块（开箱/门/拉杆/红石等）
        }
        // 带库存的方块归 container，其余可交互方块归 use；访客按庄园对应 flag 放宽。
        InteractCategory cat = block.getState() instanceof InventoryHolder
                ? InteractCategory.CONTAINER : InteractCategory.USE;
        if (!policy.allowed(event.getPlayer(), block.getX(), block.getZ(), cat)) {
            event.setCancelled(true);
            policy.notifyDenied(event.getPlayer());
        }
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
