package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.windy.guildshelter.adapter.bukkit.ManorEntityCensus;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.domain.flag.ManorEntityClass;

/**
 * 实体数量上限 caps 的执行（Bukkit 侧）：生物生成 / 载具放置时，按其分类实时统计所在庄园实体数，
 * 达上限即拦下。<b>两载体都注册</b>——载具是玩家放置(Bukkit 全覆盖)，原版生物 Bukkit 也覆盖；
 * 混合端的<b>模组生物</b>另由 NeoForge 侧 {@code NeoForgeFlags} 的 FinalizeSpawn 补足
 * （对原版生物两边都查一次属幂等，且未设 cap 时零开销，无副作用）。
 */
public final class ManorCapListener implements Listener {

    private final ManorLookup lookup;
    private final ManorEntityCensus census;

    public ManorCapListener(ManorLookup lookup, ManorEntityCensus census) {
        this.lookup = lookup;
        this.census = census;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        apply(event.getEntity(), event.getLocation(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleCreate(VehicleCreateEvent event) {
        apply(event.getVehicle(), event.getVehicle().getLocation(), () -> event.setCancelled(true));
    }

    private void apply(Entity entity, Location loc, Runnable cancel) {
        ManorEntityClass cls = ManorEntityCensus.classify(entity);
        if (cls == null || loc.getWorld() == null) {
            return;
        }
        lookup.at(loc.getWorld(), loc.getBlockX(), loc.getBlockZ()).ifPresent(manor -> {
            if (census.exceedsCap(loc.getWorld(), manor, cls)) {
                cancel.run();
            }
        });
    }
}
