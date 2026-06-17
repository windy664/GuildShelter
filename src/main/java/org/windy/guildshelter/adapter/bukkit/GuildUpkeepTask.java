package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.port.GuildRepository;

import java.util.List;
import java.util.logging.Logger;

/**
 * 每日公会维护费：从公会资金中扣除，按公会等级计算。
 * 余额不足时只警告该公会世界里的玩家。
 */
public final class GuildUpkeepTask extends BukkitRunnable {

    private final GuildRepository guilds;
    private final GuildWorldRegistry registry;
    private final double baseCost;
    private final double perLevelCost;
    private final Logger logger;

    public GuildUpkeepTask(GuildRepository guilds, GuildWorldRegistry registry,
                           double baseCost, double perLevelCost, Logger logger) {
        this.guilds = guilds;
        this.registry = registry;
        this.baseCost = baseCost;
        this.perLevelCost = perLevelCost;
        this.logger = logger;
    }

    @Override
    public void run() {
        List<GuildWorld> all = guilds.findAll();
        int charged = 0;
        int insufficient = 0;
        for (GuildWorld gw : all) {
            double cost = baseCost + perLevelCost * gw.guildLevel();
            double newFunds = gw.funds() - cost;
            if (newFunds >= 0) {
                guilds.save(gw.withFunds(newFunds));
                charged++;
            } else {
                insufficient++;
                // 只广播给该公会世界里的玩家
                String msg = "§e[公会营地] §c" + gw.guild().value() + " 维护费不足！"
                        + " 需要 §e" + String.format("%.0f", cost) + "§c，余额 §e"
                        + String.format("%.0f", Math.max(0, gw.funds())) + "§c。请尽快充值。";
                String worldName = gw.worldName();
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.getWorld().getName().equals(worldName))
                        .forEach(p -> p.sendMessage(msg));
                logger.warning("[GuildShelter] " + gw.guild().value() + " 维护费不足(需"
                        + cost + "，余额" + gw.funds() + ")");
            }
        }
        if (charged > 0 || insufficient > 0) {
            logger.info("[GuildShelter] 每日维护费: " + charged + " 个公会已扣费, "
                    + insufficient + " 个余额不足");
        }
    }
}
