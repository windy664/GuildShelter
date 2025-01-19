package org.windy.guildshelter.command.debug;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
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
        if (sender instanceof Player) {
            Player player = (Player) sender;

            // 创建 GenerateGuildBase 实例并调用 createPlatform
            GenerateGuildBase generator = new GenerateGuildBase(plugin);
            generator.createPlatform(0, 80, 0, 70,500,500,5,70,50);  // 在坐标 (0, 80, 0) 生成一个半径为 75 的平台
//    public void createPlatform(int centerX, int centerY, int centerZ, int radius,int Total_length,int Total_width,int Road_width,int Plot_length,int Plot_width) {
            player.sendMessage("平台已生成！");
            return true;
        } else {
            sender.sendMessage("此命令只能由玩家执行！");
            return false;
        }
    }
}
