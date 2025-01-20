package org.windy.guildshelter.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.util.GenerateGuildBase;
import org.windy.guildshelter.util.GuildAreaInspection;

public class CreateCommand {

    private final JavaPlugin plugin;

    // 构造函数接收插件实
    public CreateCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    public boolean execute(CommandSender sender) {
        GenerateGuildBase generator = new GenerateGuildBase(plugin);
        if (sender instanceof Player player) {
            // 获取玩家的当前位置
            int centerX = player.getLocation().getBlockX();
            int centerZ = player.getLocation().getBlockZ();
            int centerY = player.getLocation().getBlockY();  // 如果需要的话可以获取 Y 轴坐标

            // 传入其他参数（例如：radius, Total_length, Total_width 等），这里假设你已经知道这些值
            int radius = 50;  // 示例值
            int totalLength = 100;  // 示例值
            int totalWidth = 100;   // 示例值
            int roadWidth = 10;     // 示例值
            int plotLength = 30;    // 示例值
            int plotWidth = 30;     // 示例值
            String world = player.getWorld().getName();  // 获取玩家所在的世界

            // 调用 checkAreaConflict 方法进行冲突检测
            boolean isConflict = GuildAreaInspection.checkAreaConflict(centerX, centerY, centerZ, radius, totalLength, totalWidth, roadWidth, plotLength, plotWidth, world);

            if (isConflict) {
                sender.sendMessage("The area conflicts with an existing guild shelter.");
                return false;  // 有冲突
            } else {
                sender.sendMessage("The area is free to use.");
                generator.createPlatform(0, 80, 0, 70,500,500,5,70,50);
                return true;  // 没有冲突
            }
        } else {
            sender.sendMessage("Only pla yes can execute this command.");
            return false;
        }
    }
}
