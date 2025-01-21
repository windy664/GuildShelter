package org.windy.guildshelter.listener;

import com.handy.guild.event.GuildCreateEvent;  // 引入你的 GuildCreateEvent
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.Bukkit;
import org.windy.guildshelter.database.CenterTable;
import org.windy.guildshelter.database.GuildRegionTable;
import org.windy.guildshelter.util.GenerateGuildBase;
import org.windy.guildshelter.database.PlotTable;  // 导入 PlotTable
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

        // 创建 PlotTable 实例
        PlotTable plotTable = new PlotTable();
        CenterTable centerTable = new CenterTable();
        GuildRegionTable guildRegionTable = new GuildRegionTable();

        // 创建 GenerateGuildBase 实例并传递 PlotTable
        GenerateGuildBase generator = new GenerateGuildBase(plugin, plotTable,centerTable, guildRegionTable);

        // 你可以在此处添加额外的逻辑，如让玩家选择自定义位置，或者随机生成位置等
        // 假设玩家选择随机生成位置，这里可以先处理位置的生成逻辑。

        // 示例：假设我们选择生成一个平台，位置和半径可以根据实际情况调整
        int centerX = 0;  // 可以根据实际逻辑获取玩家位置
        int centerZ = 0;
        int centerY = 80;  // 假设使用默认高度
        int radius = 75;
        int plotLength = 100;
        int plotWidth = 100;
        int totalLength = 120;
        int totalWidth = 120;
        int roadWidth = 5;
        String world = event.getPlayer().getWorld().getName();

        // 调用生成平台的方法
        generator.createPlatform(centerX, centerY, centerZ, radius, totalLength, totalWidth, roadWidth, plotLength, plotWidth, world, guildName);

        // 可以在此处进一步处理冲突检查等逻辑
    }
}
