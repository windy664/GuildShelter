package org.windy.guildshelter.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.windy.guildshelter.database.mysql.AreaInfo;
import org.windy.guildshelter.database.mysql.DatabaseManager;
import org.windy.guildshelter.events.GuildShelterEnterEvent;

public class GuildMoveListener implements Listener {

    private final DatabaseManager databaseManager;

    public GuildMoveListener(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @EventHandler
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();
        double x = player.getLocation().getX();
        double z = player.getLocation().getZ();
        String world = player.getWorld().getName();

        // 判断是否在 GuildShelter_plot 区域
        AreaInfo plotInfo = databaseManager.isPointInPlotAreaWithCache(x, z);
        if (plotInfo.isInArea()) {
            // 触发 GuildShelterEnterEvent 事件
            GuildShelterEnterEvent guildShelterEvent = new GuildShelterEnterEvent(player, plotInfo.getGuildname(), plotInfo.getMessage());
            org.bukkit.Bukkit.getServer().getPluginManager().callEvent(guildShelterEvent);
            return;  // 如果已经在 GuildShelter_plot 区域，就不再检查 GuildShelter_base
        }

        // 判断是否在 GuildShelter_base 区域
        AreaInfo baseInfo = databaseManager.isPointInBaseAreaWithCache(x, z);
        if (baseInfo.isInArea()) {
            // 触发 GuildShelterEnterEvent 事件
            GuildShelterEnterEvent guildShelterEvent = new GuildShelterEnterEvent(player, baseInfo.getGuildname(), baseInfo.getMessage());
            org.bukkit.Bukkit.getServer().getPluginManager().callEvent(guildShelterEvent);
        }
    }
}
