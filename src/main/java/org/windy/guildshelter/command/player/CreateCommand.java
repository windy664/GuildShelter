package org.windy.guildshelter.command.player;

import com.handy.guild.api.PlayerGuildApi;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.api.ConfigAPI;
import org.windy.guildshelter.database.PlotTable;
import org.windy.guildshelter.util.GenerateGuildBase;
import org.windy.guildshelter.util.GuildAreaInspection;

public class CreateCommand {

    private final JavaPlugin plugin;

    public CreateCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender) {
        PlotTable plotTable = new PlotTable();  // 创建 PlotTable 实例
        GenerateGuildBase generator = new GenerateGuildBase(plugin, plotTable);  // 将 PlotTable 传递给 GenerateGuildBase
        if (sender instanceof Player player) {
            // 获取玩家的当前位置
            int centerX = player.getLocation().getBlockX();
            int centerZ = player.getLocation().getBlockZ();
            int centerY = player.getLocation().getBlockY();  // 如果需要的话可以获取 Y 轴坐标

            // 获取配置参数
            int plotLength = ConfigAPI.getPlotLength();
            int plotWidth = ConfigAPI.getPlotWidth();
            int totalLength = ConfigAPI.getTotalLength();
            int totalWidth = ConfigAPI.getTotalWidth();
            int roadWidth = ConfigAPI.getRoadWidth();
            int radius = ConfigAPI.getRadius();
            String world = player.getWorld().getName();
            String guildName = PlayerGuildApi.getInstance().getPlayerGuildName(player);

            // 实例化 GuildAreaInspection
            GuildAreaInspection areaInspection = new GuildAreaInspection(plotTable);

            // 检查是否有冲突
            boolean isConflict = areaInspection.checkAreaConflict(centerX, centerY, centerZ, radius, totalLength, totalWidth, roadWidth, plotLength, plotWidth, world);

            if (isConflict) {
                sender.sendMessage("The area conflicts with an existing guild shelter.");
                return false;  // 有冲突
            } else {
                sender.sendMessage("The area is free to use.");

                // 插入区域数据到数据库
                plotTable.insertGuildShelterArea(centerX, centerZ, centerX + totalLength, centerZ + totalWidth, guildName, world);

                // 创建平台
                generator.createPlatform(centerX, centerY, centerZ, radius, totalLength, totalWidth, roadWidth, plotLength, plotWidth, world, guildName);
                return true;  // 没有冲突
            }
        } else {
            sender.sendMessage("Only players can execute this command.");
            return false;
        }
    }
}
