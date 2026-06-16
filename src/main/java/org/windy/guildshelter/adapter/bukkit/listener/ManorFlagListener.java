package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.domain.flag.Flag;

/**
 * 地皮 flag 的 <b>Bukkit 后端</b>（A 类氛围保护）：pvp / mob-spawn / explosion / fire-spread / mob-griefing。
 * 通过 {@link ManorLookup} 把事件落点解析到地皮再查该地皮 flag。混合端模组内容的完整覆盖由后续 NeoForge 侧补。
 */
public final class ManorFlagListener implements Listener {

    private final ManorLookup lookup;

    public ManorFlagListener(ManorLookup lookup) {
        this.lookup = lookup;
    }

    /** 某位置的地皮该 flag 是否为 false(即"禁止")。无地皮则不拦(false)。 */
    private boolean denied(World world, int x, int z, Flag flag) {
        return lookup.at(world, x, z).map(m -> !flag.resolveBool(m.flags())).orElse(false);
    }

    private boolean denied(Location loc, Flag flag) {
        return denied(loc.getWorld(), loc.getBlockX(), loc.getBlockZ(), flag);
    }

    // ---- pvp ----
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (denied(victim.getLocation(), Flag.PVP)) {
            event.setCancelled(true);
        }
    }

    private Player resolveAttacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) {
                return p;
            }
        }
        return null;
    }

    // ---- mob-spawn(挡环境刷怪,放行玩家召唤/刷怪笼蛋/繁殖)----
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        switch (event.getSpawnReason()) {
            case CUSTOM, SPAWNER_EGG, BREEDING, EGG, DISPENSE_EGG -> {
                return; // 玩家行为引起的，不拦
            }
            default -> { /* 继续判断 */ }
        }
        if (denied(event.getLocation(), Flag.MOB_SPAWN)) {
            event.setCancelled(true);
        }
    }

    // ---- explosion(按方块剔除受保护地皮内的方块)----
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> denied(b.getWorld(), b.getX(), b.getZ(), Flag.EXPLOSION));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> denied(b.getWorld(), b.getX(), b.getZ(), Flag.EXPLOSION));
    }

    // ---- fire-spread ----
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        BlockIgniteEvent.IgniteCause cause = event.getCause();
        if (cause == BlockIgniteEvent.IgniteCause.SPREAD || cause == BlockIgniteEvent.IgniteCause.LAVA) {
            if (denied(event.getBlock().getLocation(), Flag.FIRE_SPREAD)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (denied(event.getBlock().getLocation(), Flag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    // ---- mob-griefing(非玩家实体改方块：苦力怕坑/末影人搬块/僵尸破门等)----
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        if (denied(event.getBlock().getLocation(), Flag.MOB_GRIEFING)) {
            event.setCancelled(true);
        }
    }
}
