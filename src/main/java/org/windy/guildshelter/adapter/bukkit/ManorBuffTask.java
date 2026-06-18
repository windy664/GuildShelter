package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.model.Manor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 地皮 flag 的 <b>个人增益</b> 执行（Bukkit 侧）：fly / feed / heal。每秒扫一遍在线玩家，
 * 按其所在地皮 flag 持续施加；离开地皮自动撤销（fly 只撤我们授予的，不动创造/外部飞行权限）。
 */
public final class ManorBuffTask extends BukkitRunnable {

    private static final double HEAL_PER_TICK = 2.0;

    private final ManorLookup lookup;
    private final Set<UUID> grantedFly = new HashSet<>();

    public ManorBuffTask(ManorLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public void run() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!FakePlayerFilter.isRealPlayer(p)) continue; // 跳过模组假人
            Manor m = lookup.at(p.getWorld(),
                    p.getLocation().getBlockX(), p.getLocation().getBlockZ()).orElse(null);

            applyFly(p, m != null && Flag.FLY.resolveBool(m.flags()));

            if (m != null && Flag.FEED.resolveBool(m.flags())) {
                if (p.getFoodLevel() < 20) {
                    p.setFoodLevel(20);
                }
                if (p.getSaturation() < 10f) {
                    p.setSaturation(10f);
                }
            }

            if (m != null && Flag.HEAL.resolveBool(m.flags())) {
                double max = p.getMaxHealth();
                double hp = p.getHealth();
                if (hp > 0 && hp < max) {
                    p.setHealth(Math.min(max, hp + HEAL_PER_TICK));
                }
            }
        }
    }

    /** 只撤销本任务授予的飞行；创造/旁观或外部飞行权限保持不动。 */
    private void applyFly(Player p, boolean shouldFly) {
        UUID id = p.getUniqueId();
        if (shouldFly) {
            if (!p.getAllowFlight()) {
                p.setAllowFlight(true);
                grantedFly.add(id);
            }
        } else if (grantedFly.remove(id)) {
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                p.setFlying(false);
                p.setAllowFlight(false);
            }
        }
    }
}
