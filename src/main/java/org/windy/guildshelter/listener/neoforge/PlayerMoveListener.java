package org.windy.guildshelter.listener.neoforge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.windy.guildshelter.database.mysql.AreaInfo;
import org.windy.guildshelter.database.mysql.DatabaseManager;
import org.windy.guildshelter.events.GuildShelterEnterEvent;
import org.windy.guildshelter.events.GuildShelterLeaveEvent;

import java.util.HashSet;
import java.util.Set;

public class PlayerMoveListener {

    private DatabaseManager databaseManager;
    private Set<String> playersInShelter = new HashSet<>();  // 用来存储已进入区域的玩家

    public PlayerMoveListener(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @SubscribeEvent
    public void onPlayerMove(MovementInputUpdateEvent event) {
        // 获取玩家名和位置
        String playerName = event.getEntity().getName().getString();
        double x = event.getEntity().getX();
        double z = event.getEntity().getZ();

        // 获取 Bukkit 玩家对象
        Player player = Bukkit.getServer().getPlayer(playerName);
        if (player == null) {
            return;  // 如果玩家不在线，直接返回
        }

        // 判断是否在 GuildShelter_plot 区域
        AreaInfo plotInfo = databaseManager.isPointInPlotAreaWithCache(x, z);
        boolean isInPlotArea = plotInfo.isInArea();

        // 判断玩家是否离开 GuildShelter 区域
        if (playersInShelter.contains(playerName) && !isInPlotArea) {
            // 玩家离开 GuildShelter 区域，触发离开事件
            GuildShelterLeaveEvent leaveEvent = new GuildShelterLeaveEvent(player, plotInfo.getGuildname(), plotInfo.getMessage());
            Bukkit.getServer().getPluginManager().callEvent(leaveEvent);
            playersInShelter.remove(playerName);  // 从已进入区域的玩家集合中移除
        }

        // 判断是否在 GuildShelter_plot 区域
        if (isInPlotArea) {
            if (!playersInShelter.contains(playerName)) {
                // 玩家进入 GuildShelter 区域，触发进入事件
                GuildShelterEnterEvent enterEvent = new GuildShelterEnterEvent(player, plotInfo.getGuildname(), plotInfo.getMessage());
                Bukkit.getServer().getPluginManager().callEvent(enterEvent);
                playersInShelter.add(playerName);  // 记录玩家已进入区域
            }
        }

        // 判断是否在 GuildShelter_base 区域
        AreaInfo baseInfo = databaseManager.isPointInBaseAreaWithCache(x, z);
        boolean isInBaseArea = baseInfo.isInArea();

        if (isInBaseArea) {
            if (!playersInShelter.contains(playerName)) {
                // 玩家进入 GuildShelter_base 区域，触发进入事件
                GuildShelterEnterEvent enterEvent = new GuildShelterEnterEvent(player, baseInfo.getGuildname(), baseInfo.getMessage());
                Bukkit.getServer().getPluginManager().callEvent(enterEvent);
                playersInShelter.add(playerName);  // 记录玩家已进入区域
            }
        }
    }
}
