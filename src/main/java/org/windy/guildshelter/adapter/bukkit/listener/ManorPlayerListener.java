package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.domain.flag.Flag;

/**
 * 伤害/实体组里<b>玩家行为</b>类 flag(始终 Bukkit,Youer 上全覆盖,无需 NeoForge):
 * keep-inventory / item-drop / instabreak / mob-place / 重生在家园(HOME_X/Y/Z)。
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

    /**
     * 重生在家园：庄主设了 HOME_X/Y/Z flag 后，死亡重生在地皮传送点。
     * 未设（全为 0）则不干预，走原版重生逻辑。
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onRespawn(PlayerRespawnEvent event) {
        // 用死亡位置查地皮（重生时 getLocation 可能已变，但 getRespawnLocation 可能还没设）
        // 优先用玩家的 bed spawn 位置判断是否在公会世界
        org.bukkit.World world = event.getPlayer().getWorld();
        // 如果有自定义重生点（床），不覆盖
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }
        Location deathLoc = event.getPlayer().getLocation();
        lookup.at(deathLoc.getWorld(), deathLoc.getBlockX(), deathLoc.getBlockZ()).ifPresent(manor -> {
            int hx = Flag.HOME_X.resolveInt(manor.flags());
            int hy = Flag.HOME_Y.resolveInt(manor.flags());
            int hz = Flag.HOME_Z.resolveInt(manor.flags());
            if (hx == 0 && hy == 0 && hz == 0) {
                return; // 未设传送点，不干预
            }
            Location home = new Location(deathLoc.getWorld(), hx, hy, hz);
            // 安全检查：目标不能是岩浆/虚空
            org.bukkit.block.Block feet = home.getBlock();
            org.bukkit.block.Block below = feet.getRelative(0, -1, 0);
            if (feet.getType().isAir() && below.getType().isAir()) {
                return; // 虚空，不安全
            }
            if (feet.isLiquid() || below.isLiquid()) {
                return; // 岩浆/水，不安全
            }
            event.setRespawnLocation(home);
        });
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
