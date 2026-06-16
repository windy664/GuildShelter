package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地皮 flag 的 <b>访问类</b> 执行（Bukkit 侧）：deny-entry(挡非成员进入) / greeting / farewell(进出消息)。
 * 仅在跨 chunk 时查库（性能），按"当前所在地皮"变化触发进出。OP / guildshelter.admin 不受 deny-entry 限制。
 */
public final class ManorAccessListener implements Listener {

    private static final String ADMIN_BYPASS = "guildshelter.admin";

    private final ManorLookup lookup;
    private final Map<UUID, long[]> lastChunk = new ConcurrentHashMap<>();
    private final Map<UUID, Manor> lastManor = new ConcurrentHashMap<>();

    public ManorAccessListener(ManorLookup lookup) {
        this.lookup = lookup;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        int cx = event.getTo().getBlockX() >> 4;
        int cz = event.getTo().getBlockZ() >> 4;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        long[] prevC = lastChunk.get(id);
        if (prevC != null && prevC[0] == cx && prevC[1] == cz) {
            return; // 同 chunk，不查库
        }

        Manor cur = lookup.at(event.getTo().getWorld(),
                event.getTo().getBlockX(), event.getTo().getBlockZ()).orElse(null);

        // deny-entry：未授权者推回，不更新 last*（下次再判）
        if (cur != null && Flag.DENY_ENTRY.resolveBool(cur.flags()) && !canEnter(player, cur)) {
            event.setTo(event.getFrom());
            player.sendMessage("§c这块地皮谢绝访客进入。");
            return;
        }
        lastChunk.put(id, new long[]{cx, cz});

        Manor prev = lastManor.get(id);
        if (sameManor(prev, cur)) {
            return; // 同一块地皮（地皮跨多 chunk）
        }
        if (prev != null) {
            String bye = Flag.FAREWELL.resolveString(prev.flags());
            if (!bye.isBlank()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', bye));
            }
        }
        if (cur != null) {
            String hi = Flag.GREETING.resolveString(cur.flags());
            if (!hi.isBlank()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', hi));
            }
            lastManor.put(id, cur);
        } else {
            lastManor.remove(id);
        }
    }

    private boolean canEnter(Player p, Manor m) {
        return p.hasPermission(ADMIN_BYPASS) || p.isOp()
                || m.hasBuildAccess(PlayerRef.of(p.getUniqueId()));
    }

    private static boolean sameManor(Manor a, Manor b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.slot() == b.slot() && a.guild().equals(b.guild());
    }
}
