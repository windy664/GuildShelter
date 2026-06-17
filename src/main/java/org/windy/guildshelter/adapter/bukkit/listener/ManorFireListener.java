package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.domain.flag.Flag;

/**
 * fire-spread flag 的执行。<b>两种载体都注册</b>——NeoForge 26 没有火相关事件，只能靠 Bukkit，
 * 因此即便在混合端也由它处理(不会与 NeoForge 侧重复，因为 NeoForge 侧根本不碰火)。
 */
public final class ManorFireListener implements Listener {

    private final ManorLookup lookup;

    public ManorFireListener(ManorLookup lookup) {
        this.lookup = lookup;
    }

    private boolean denied(Location loc) {
        return lookup.at(loc.getWorld(), loc.getBlockX(), loc.getBlockZ())
                .map(m -> !Flag.FIRE_SPREAD.resolveBool(m.flags())).orElse(false);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        BlockIgniteEvent.IgniteCause cause = event.getCause();
        if ((cause == BlockIgniteEvent.IgniteCause.SPREAD || cause == BlockIgniteEvent.IgniteCause.LAVA)
                && denied(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (denied(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
}
