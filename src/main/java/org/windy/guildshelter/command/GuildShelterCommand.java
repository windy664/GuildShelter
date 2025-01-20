package org.windy.guildshelter.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.windy.guildshelter.command.debug.GenPlatCommand;
import org.windy.guildshelter.command.player.CreateCommand;
import org.windy.guildshelter.command.player.SetSpawnCommand;
import org.windy.guildshelter.command.player.SpawnCommand;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

public class GuildShelterCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;

    // 构造函数，接收插件实
    public GuildShelterCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // 处理命令选择
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 如果没有参数，发送基本的使用提示
        if (args.length == 0) {
            sender.sendMessage("Usage: /gs <debug|spawn|setspawn>");
            return false;
        }

        // 如果 args[0] 是 debug
        if (args[0].equalsIgnoreCase("debug")) {
            // 检查第二个参数是否为 genplat
            if (args.length > 1 && args[1].equalsIgnoreCase("genplat")) {
                // 传入 plugin 实例
                GenPlatCommand genPlatCommand = new GenPlatCommand(plugin);
                return genPlatCommand.execute(sender);
            }
        }

        // 处理其他命令
        switch (args[0].toLowerCase()) {
            case "spawn":
                return new SpawnCommand().execute(sender);
            case "setspawn":
                return new SetSpawnCommand().execute(sender);
            case "create":
                CreateCommand createCommand = new CreateCommand(plugin);
                return createCommand.execute(sender);
            default:
                sender.sendMessage("Unknown command. Usage: /gs <debug|spawn|setspawn>");
                return false;
        }
    }

    // Tab 补全
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("debug");
            completions.add("spawn");
            completions.add("setspawn");
            completions.add("create");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            completions.add("genplat");
        }

        return completions;
    }
}
