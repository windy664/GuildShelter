package org.windy.guildshelter.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.windy.guildshelter.database.mysql.AreaInfo;
import org.windy.guildshelter.database.mysql.DatabaseManager;
import org.windy.guildshelter.events.GuildShelterEnterEvent;
import org.windy.guildshelter.events.GuildShelterLeaveEvent;
import java.util.HashSet;
import java.util.Set;

public class GuildMoveListener implements Listener {

    private DatabaseManager databaseManager;
    private Set<Player> playersInShelter = new HashSet<>();  // 用来存储已进入区域的玩家

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
        boolean isInPlotArea = plotInfo.isInArea();

        // 判断玩家是否离开区域
        if (playersInShelter.contains(player) && !isInPlotArea) {
            // 玩家离开 GuildShelter 区域，触发离开事件
            GuildShelterLeaveEvent leaveEvent = new GuildShelterLeaveEvent(player, plotInfo.getGuildname(), plotInfo.getMessage());
            org.bukkit.Bukkit.getServer().getPluginManager().callEvent(leaveEvent);
            playersInShelter.remove(player);  // 从已进入区域的玩家集合中移除
        }

        // 判断是否在 GuildShelter_plot 区域
        if (isInPlotArea) {
            if (!playersInShelter.contains(player)) {
                // 玩家进入 GuildShelter 区域，触发进入事件
                GuildShelterEnterEvent enterEvent = new GuildShelterEnterEvent(player, plotInfo.getGuildname(), plotInfo.getMessage());
                org.bukkit.Bukkit.getServer().getPluginManager().callEvent(enterEvent);
                playersInShelter.add(player);  // 记录玩家已进入区域
            }
        }

        // 判断是否在 GuildShelter_base 区域
        AreaInfo baseInfo = databaseManager.isPointInBaseAreaWithCache(x, z);
        boolean isInBaseArea = baseInfo.isInArea();

        if (isInBaseArea) {
            if (!playersInShelter.contains(player)) {
                // 玩家进入 GuildShelter_base 区域，触发进入事件
                GuildShelterEnterEvent enterEvent = new GuildShelterEnterEvent(player, baseInfo.getGuildname(), baseInfo.getMessage());
                org.bukkit.Bukkit.getServer().getPluginManager().callEvent(enterEvent);
                playersInShelter.add(player);  // 记录玩家已进入区域
            }
        }
    }
}
