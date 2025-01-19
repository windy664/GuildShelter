package org.windy.guildshelter.command.player;


import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand {

    // 处理 /gs spawn 命令
    public boolean execute(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            player.teleport(player.getWorld().getSpawnLocation());
            sender.sendMessage("Teleported to spawn.");
            return true;
        } else {
            sender.sendMessage("This command can only be executed by a player.");
            return false;
        }
    }
}
