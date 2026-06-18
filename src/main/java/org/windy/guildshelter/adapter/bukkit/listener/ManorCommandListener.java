package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.adapter.bukkit.Messages;
import org.windy.guildshelter.adapter.bukkit.Permissions;
import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.model.Manor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 地皮 flag {@code blocked-cmds} 的 Bukkit 执行：当玩家站在设了此 flag 的地皮内时，
 * 禁止使用其中列出的命令（逗号分隔，不含 {@code /}）。
 *
 * <p>admin 持有者不受此限制。
 */
public final class ManorCommandListener implements Listener {

    private final ManorLookup lookup;

    public ManorCommandListener(ManorLookup lookup) {
        this.lookup = lookup;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!org.windy.guildshelter.adapter.bukkit.FakePlayerFilter.isRealPlayer(player)) return;
        if (player.isOp() || Permissions.hasAdminPerm(player, Permissions.ADMIN)) {
            return; // admin 不受限制
        }
        Optional<Manor> at = lookup.at(player.getWorld(),
                player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (at.isEmpty()) {
            return; // 不在任何地皮上
        }
        String blocked = Flag.BLOCKED_CMDS.resolveString(at.get().flags());
        if (blocked.isBlank()) {
            return;
        }
        // 解析命令名："/tp spawn" → "tp"
        String msg = event.getMessage();
        if (!msg.startsWith("/")) {
            return;
        }
        String cmd = msg.substring(1).split("\\s+")[0].toLowerCase();
        Set<String> blockedSet = new HashSet<>(Arrays.asList(blocked.toLowerCase().split(",")));
        if (blockedSet.contains(cmd)) {
            event.setCancelled(true);
            player.sendMessage(Messages.get("listener.cmd_blocked", cmd));
        }
    }
}
