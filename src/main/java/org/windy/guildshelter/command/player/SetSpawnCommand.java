package org.windy.guildshelter.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSpawnCommand {

    // 处理 /gs setspawn 命令
    public boolean execute(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            player.getWorld().setSpawnLocation(player.getLocation());
            sender.sendMessage("Spawn point set to your current location.");
            return true;
        } else {
            sender.sendMessage("This command can only be executed by a player.");
            return false;
        }
    }
}