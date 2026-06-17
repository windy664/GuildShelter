package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.domain.flag.Flag;

/**
 * 地皮 flag 的 <b>Bukkit 后端</b>（A 氛围类，除 fire-spread）：pvp / mob-spawn / explosion / mob-griefing。
 * 仅在<b>纯 Bukkit 端</b>注册(NeoForge 在则由 NeoForgeFlags 处理这几个,免双重)。
 * fire-spread 因 NeoForge 26 无对应事件,拆到 {@link ManorFireListener}(两载体都注册)。
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

    // ---- pvp / pve / invincible（统一在一个 EntityDamageEvent 里判，避免父/子事件 HandlerList 共享坑）----
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        boolean victimIsPlayer = victim instanceof Player;

        // invincible：玩家在地皮内免一切伤害
        if (victimIsPlayer && flagOn(victim.getLocation(), Flag.INVINCIBLE)) {
            event.setCancelled(true);
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent ev)) {
            return;
        }
        Player attacker = resolveAttacker(ev.getDamager());
        if (victimIsPlayer) {
            if (attacker != null && !attacker.equals(victim)) {
                if (denied(victim.getLocation(), Flag.PVP)) { // 玩家打玩家
                    event.setCancelled(true);
                }
            } else if (isMob(ev.getDamager())) {
                if (denied(victim.getLocation(), Flag.PVE)) { // 怪打玩家
                    event.setCancelled(true);
                }
            }
        } else if (attacker != null) {
            if (denied(victim.getLocation(), Flag.PVE)) { // 玩家打怪
                event.setCancelled(true);
            }
        }
    }

    private Player resolveAttacker(Entity damager) {
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

    private static boolean isMob(Entity damager) {
        if (damager instanceof Player) {
            return false;
        }
        if (damager instanceof LivingEntity) {
            return true;
        }
        if (damager instanceof Projectile proj) {
            return proj.getShooter() instanceof LivingEntity && !(proj.getShooter() instanceof Player);
        }
        return false;
    }

    /** 该位置地皮该 flag 是否为 true(开)。用于 invincible 这种"开=拦"的反向语义。 */
    private boolean flagOn(Location loc, Flag flag) {
        return lookup.at(loc.getWorld(), loc.getBlockX(), loc.getBlockZ())
                .map(m -> flag.resolveBool(m.flags())).orElse(false);
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

    // 注：fire-spread 拆到 ManorFireListener(始终 Bukkit，因 NeoForge 26 无对应事件)。

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
