package org.windy.guildshelter.command.debug;

import com.handy.guild.api.PlayerGuildApi;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.api.ConfigAPI;
import org.windy.guildshelter.util.GenerateGuildBase;

public class GenPlatCommand {

    private final JavaPlugin plugin;

    // 构造函数接收插件实
    public GenPlatCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // 执行命令的方法
    public boolean execute(CommandSender sender) {
        // 确保命令由玩家执行
        if (sender instanceof Player player) {
            int centerX = player.getLocation().getBlockX();
            int centerZ = player.getLocation().getBlockZ();
            int centerY = player.getLocation().getBlockY();  // 如果需要的话可以获取 Y 轴坐标
            int plotLength = ConfigAPI.getPlotLength();
            int plotWidth = ConfigAPI.getPlotWidth();
            int totalLength = ConfigAPI.getTotalLength();
            int totalWidth = ConfigAPI.getTotalWidth();
            int roadWidth = ConfigAPI.getRoadWidth();
            int radius = ConfigAPI.getRadius();
            String world = player.getWorld().getName();  // 获取玩家所在的世界
            String guildName = PlayerGuildApi.getInstance().getPlayerGuildName(player);
            // 创建 GenerateGuildBase 实例并调用 createPlatform
            GenerateGuildBase generator = new GenerateGuildBase(plugin);
            generator.createPlatform(centerX, centerZ, centerY, radius,plotLength,plotWidth,totalLength,totalWidth,roadWidth,world,"公会测试");  // 在坐标 (0, 80, 0) 生成一个半径为 75 的平台
//    public void createPlatform(int centerX, int centerY, int centerZ, int radius,int Total_length,int Total_width,int Road_width,int Plot_length,int Plot_width) {
            player.sendMessage("平台已生成！");
            return true;
        } else {
            sender.sendMessage("此命令只能由玩家执行！");
            return false;
        }
    }
}
