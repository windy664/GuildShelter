package org.windy.guildshelter.listener;

import com.handy.guild.event.GuildCreateEvent;  // 引入你的 GuildCreateEvent
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.Bukkit;
import org.windy.guildshelter.util.GenerateGuildBase;
import org.bukkit.plugin.java.JavaPlugin;

public class GuildCreateListener implements Listener {

    private final JavaPlugin plugin;

    // 构造器，接收 plugin 实例
    public GuildCreateListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // 监听 GuildCreateEvent 事件
    @EventHandler
    public void onGuildCreate(GuildCreateEvent event) {
        // 获取玩家和公会名称
        String playerName = event.getPlayer().getName();
        String guildName = event.getGuildName();

        Bukkit.getLogger().info("Player " + playerName + " has created a guild named " + guildName);

        // 创建 GenerateGuildBase 实例并调用 createPlatform
        GenerateGuildBase generator = new GenerateGuildBase(plugin);

        // 问玩家自选 or 随机，若自选，则获取玩家坐标，排查创建的是否...
        // 如果是随机，则随机坐标，排查是否有其他公会重叠
    }
}
