package org.windy.guildshelter.command.debug;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.windy.guildshelter.util.GeneraeInitialGuildBase;
import org.windy.guildshelter.database.mysql.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

public class GenPlatCommand {
    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;

    // 构造函数，接收 plugin 实例，并内部初始化 databaseManager
    public GenPlatCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = new DatabaseManager(plugin);  // 假设你在 DatabaseManager 中有一个构造函数接收 JavaPlugin
    }

    // 执行命令的方法
    public boolean execute(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            String playerName = player.getName();
            String guildName = "TestGuild";  // 假设你已经从某处获取了公会名

            // 获取玩家所在世界
            String worldName = player.getWorld().getName();

            // 假设基于某些参数，生成平台的大小和位置
            int startX = player.getLocation().getBlockX();
            int startZ = player.getLocation().getBlockZ();
            int totalLength = 600;  // 假设一个总长度
            int totalWidth = 300;   // 假设一个总宽度
            int roadWidth = 5;
            int plotLength = 100;
            int plotWidth = 90;

            // 创建 GeneraeInitialGuildBase 实例并生成数据
            GeneraeInitialGuildBase generator = new GeneraeInitialGuildBase(databaseManager);
            generator.create(startX, startZ, totalLength, totalWidth, roadWidth, plotLength, plotWidth, worldName, guildName);

            // 告知玩家
            player.sendMessage("Guild base generation initiated!");

            return true;
        } else {
            sender.sendMessage("This command can only be executed by a player.");
            return false;
        }
    }
}
