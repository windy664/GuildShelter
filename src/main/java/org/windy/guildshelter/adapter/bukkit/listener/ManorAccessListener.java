package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.adapter.bukkit.ManorRoles;
import org.windy.guildshelter.adapter.bukkit.Permissions;
import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地皮 flag 的 <b>访问类</b> 执行（Bukkit 侧）：
 * <ul>
 *   <li>deny-entry / deny-exit：挡非成员进入 / 困住非成员不让离开；</li>
 *   <li>greeting / farewell：进出消息（titles flag 开则用屏幕标题显示，否则聊天框）；</li>
 *   <li>notify-enter / notify-leave：有人进出时私信在线成员（庄主/共建人）。</li>
 * </ul>
 * 仅在跨 chunk 时查库（性能），按"当前所在地皮"变化触发进出。OP / guildshelter.admin 不受进出限制。
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
        Manor prev = lastManor.get(id);
        PlayerRef ref = PlayerRef.of(id);
        boolean bypass = player.hasPermission(ADMIN_BYPASS) || player.isOp()
                || player.hasPermission(Permissions.BYPASS_DENY);

        // 黑名单：denied 玩家禁止进入（覆盖一切；owner 不会被判 denied），推回不更新 last*
        if (cur != null && !bypass && ManorRoles.isDenied(cur, ref)) {
            event.setTo(event.getFrom());
            player.sendMessage("§c你被列入这块地皮的黑名单，无法进入。");
            return;
        }
        // deny-entry：访客被谢客时推回（owner/trusted/member/管理可进），不更新 last*（下次再判）
        if (cur != null && !canEnter(player, cur) && Flag.DENY_ENTRY.resolveBool(cur.flags())) {
            event.setTo(event.getFrom());
            player.sendMessage("§c这块地皮谢绝访客进入。");
            return;
        }
        // deny-exit：非成员被困在 prev 内，离开时推回（成员/管理可自由出入）
        if (prev != null && !sameManor(prev, cur)
                && !canEnter(player, prev) && Flag.DENY_EXIT.resolveBool(prev.flags())) {
            event.setTo(event.getFrom());
            player.sendMessage("§c你被困在这块地皮里，无法离开。");
            return;
        }
        lastChunk.put(id, new long[]{cx, cz});

        if (sameManor(prev, cur)) {
            return; // 同一块地皮（地皮跨多 chunk）
        }
        // 离开 prev
        if (prev != null) {
            String bye = Flag.FAREWELL.resolveString(prev.flags());
            if (!bye.isBlank()) {
                showMessage(player, prev, bye);
            }
            if (Flag.NOTIFY_LEAVE.resolveBool(prev.flags())) {
                notifyMembers(prev, player, "§7离开了你的地皮 §f#" + prev.slot());
            }
        }
        // 进入 cur
        if (cur != null) {
            String hi = Flag.GREETING.resolveString(cur.flags());
            if (!hi.isBlank()) {
                showMessage(player, cur, hi);
            }
            if (Flag.NOTIFY_ENTER.resolveBool(cur.flags())) {
                notifyMembers(cur, player, "§7进入了你的地皮 §f#" + cur.slot());
            }
            lastManor.put(id, cur);
        } else {
            lastManor.remove(id);
        }
    }

    /** 进出消息：titles flag 开 → 屏幕标题；否则聊天框。统一翻译 & 颜色码。 */
    private void showMessage(Player player, Manor manor, String raw) {
        String msg = ChatColor.translateAlternateColorCodes('&', raw);
        if (Flag.TITLES.resolveBool(manor.flags())) {
            player.sendTitle(msg, "", 10, 40, 10);
        } else {
            player.sendMessage(msg);
        }
    }

    /** 私信该庄园在线成员（庄主+共建人），不通知触发者本人。 */
    private void notifyMembers(Manor manor, Player mover, String suffix) {
        String line = "§e" + mover.getName() + " " + suffix;
        notifyOne(manor.owner(), mover, line);
        for (PlayerRef ref : manor.coBuilders()) {
            notifyOne(ref, mover, line);
        }
    }

    private void notifyOne(PlayerRef ref, Player mover, String line) {
        if (ref.uuid().equals(mover.getUniqueId())) {
            return; // 别给触发者自己发
        }
        Player member = Bukkit.getPlayer(ref.uuid());
        if (member != null) {
            member.sendMessage(line);
        }
    }

    /** 可无视 deny-entry 进入者：管理 + owner/trusted/member（member 不论上级是否在线都可进入）。 */
    private boolean canEnter(Player p, Manor m) {
        return p.hasPermission(ADMIN_BYPASS) || p.isOp()
                || ManorRoles.isMemberOrAbove(m, PlayerRef.of(p.getUniqueId()));
    }

    private static boolean sameManor(Manor a, Manor b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.slot() == b.slot() && a.guild().equals(b.guild());
    }
}
