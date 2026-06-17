package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.domain.flag.Flag;

/**
 * 伤害/实体组里<b>玩家行为</b>类 flag(始终 Bukkit,Youer 上全覆盖,无需 NeoForge):
 * keep-inventory / item-drop / instabreak / mob-place。
 * pve/invincible 属伤害判定,放在 {@link ManorFlagListener}(载体分流)。
 */
public final class ManorPlayerListener implements Listener {

    private final ManorLookup lookup;

    public ManorPlayerListener(ManorLookup lookup) {
        this.lookup = lookup;
    }

    private boolean flagOff(Location loc, Flag flag) {
        return lookup.at(loc.getWorld(), loc.getBlockX(), loc.getBlockZ())
                .map(m -> !flag.resolveBool(m.flags())).orElse(false);
    }

    private boolean flagOn(Location loc, Flag flag) {
        return lookup.at(loc.getWorld(), loc.getBlockX(), loc.getBlockZ())
                .map(m -> flag.resolveBool(m.flags())).orElse(false);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        if (flagOn(event.getEntity().getLocation(), Flag.KEEP_INVENTORY)) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (flagOff(event.getPlayer().getLocation(), Flag.ITEM_DROP)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (flagOn(event.getBlock().getLocation(), Flag.INSTABREAK)) {
            event.setInstaBreak(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEggSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason r = event.getSpawnReason();
        if ((r == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || r == CreatureSpawnEvent.SpawnReason.DISPENSE_EGG)
                && flagOff(event.getLocation(), Flag.MOB_PLACE)) {
            event.setCancelled(true);
        }
    }
}
