package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.windy.guildshelter.adapter.bukkit.InteractionPolicy;
import org.windy.guildshelter.domain.flag.InteractCategory;

import java.util.Optional;

/**
 * 访客对<b>实体</b>交互的访问控制（Bukkit 后端）：展示框/盔甲架(item-frame flag)、船/矿车(vehicle-use)。
 * 与 {@link ManorProtectionListener} 同思路，<b>仅在纯 Bukkit 端注册</b>——混合端由 NeoForge 侧
 * {@code NeoForgeProtection} 统一处理（覆盖模组实体）。决策共用 {@link InteractionPolicy}。
 *
 * <p>说明：展示框/盔甲架的旋转·取放走右键事件，破坏·取物走 {@link EntityDamageByEntityEvent}；
 * 船/矿车的乘坐走 {@link VehicleEnterEvent}、破坏走 {@link VehicleDamageEvent}。
 * 放置船/矿车属于"放方块"，由 {@link ManorProtectionListener} 的放置保护管，这里不重复。
 */
public final class ManorEntityListener implements Listener {

    private final InteractionPolicy policy;

    public ManorEntityListener(InteractionPolicy policy) {
        this.policy = policy;
    }

    /** 实体 → 交互类；非受管实体返回 empty（不干预）。 */
    private static Optional<InteractCategory> categoryOf(Entity entity) {
        if (entity instanceof ItemFrame || entity instanceof ArmorStand) {
            return Optional.of(InteractCategory.ITEM_FRAME);
        }
        if (entity instanceof Vehicle) { // Boat / Minecart 等
            return Optional.of(InteractCategory.VEHICLE);
        }
        return Optional.empty();
    }

    // ---- 右键交互：展示框旋转·放物、盔甲架穿脱、右键上船/矿车 ----
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        check(event.getPlayer(), event.getRightClicked(), () -> event.setCancelled(true));
    }

    // 盔甲架等带位置的交互走 At 变体（与上面各有独立 HandlerList，ignoreCancelled 避免双拦提示）。
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        check(event.getPlayer(), event.getRightClicked(), () -> event.setCancelled(true));
    }

    // ---- 破坏/取物：玩家打展示框(掉落或取出物品)、打盔甲架 ----
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        check(player, event.getEntity(), () -> event.setCancelled(true));
    }

    // ---- 乘坐载具 ----
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player)) {
            return; // 只管玩家上车（怪物/动物被推入不拦）
        }
        check(player, event.getVehicle(), () -> event.setCancelled(true));
    }

    // ---- 破坏载具 ----
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!(event.getAttacker() instanceof Player player)) {
            return;
        }
        check(player, event.getVehicle(), () -> event.setCancelled(true));
    }

    private void check(Player player, Entity target, Runnable cancel) {
        categoryOf(target).ifPresent(cat -> {
            Location loc = target.getLocation();
            if (!policy.allowed(player, loc.getBlockX(), loc.getBlockZ(), cat)) {
                cancel.run();
                policy.notifyDenied(player);
            }
        });
    }
}
