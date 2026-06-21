package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.windy.guildshelter.adapter.bukkit.ManorEntityCensus;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.adapter.bukkit.Messages;
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

    /**
     * 方块实体上限：放置箱子/熔炉/漏斗/模组机器等带方块实体的方块时，达上限即拦下（block 模式，不删存量）。
     * 先查【具体机器配额】（更精细的提示），再查【方块实体总数】。
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!(block.getState() instanceof TileState)) {
            return; // 非方块实体，免查
        }
        if (block.getWorld() == null) return;
        var w = block.getWorld();
        var at = lookup.at(w, block.getX(), block.getZ());
        if (at.isPresent()) {
            String id = block.getType().getKey().toString();
            ManorEntityCensus.Denial denial = census.placementDenial(w, at.get(), id);
            if (denial != null) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Messages.get(denial.messageKey(), denial.args()));
            }
        } else if (lookup.isMainCityAt(w, block.getX(), block.getZ())) {
            // 主城方块实体总数固定上限（独立于庄园等级）
            ManorEntityCensus.Denial denial = census.cityPlacementDenial(w);
            if (denial != null) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Messages.get(denial.messageKey(), denial.args()));
            }
        }
    }

    private void apply(Entity entity, Location loc, Runnable cancel) {
        ManorEntityClass cls = ManorEntityCensus.classify(entity);
        if (cls == null || loc.getWorld() == null) {
            return;
        }
        var w = loc.getWorld();
        var at = lookup.at(w, loc.getBlockX(), loc.getBlockZ());
        if (at.isPresent()) {
            if (census.exceedsCap(w, at.get(), cls)) {
                cancel.run();
            }
        } else if (lookup.isMainCityAt(w, loc.getBlockX(), loc.getBlockZ())
                && census.cityExceedsCap(w, cls)) {
            cancel.run(); // 主城生物固定上限
        }
    }
}
