package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.adapter.bukkit.ManorRoles;
import org.windy.guildshelter.adapter.bukkit.Messages;
import org.windy.guildshelter.adapter.bukkit.Permissions;
import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.EconomyPort;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地皮 flag 的 <b>访问类</b> 执行（Bukkit 侧）：
 * <ul>
 *   <li>deny-entry / deny-exit：挡非成员进入 / 困住非成员不让离开；</li>
 *   <li>greeting / farewell：进出消息（titles flag 开则用屏幕标题显示，否则聊天框）；</li>
 *   <li>notify-enter / notify-leave：有人进出时私信在线成员（庄主/共建人）；</li>
 *   <li>price：访客进入收费地皮时自动扣费（需 Vault；成员/管理免付）。</li>
 * </ul>
 * 仅在跨 chunk 时查库（性能），按"当前所在地皮"变化触发进出。OP / guildshelter.admin 不受进出限制。
 */
public final class ManorAccessListener implements Listener {

    private final ManorLookup lookup;
    private final EconomyPort economy; // null = 无 Vault，price flag 不生效
    private final Map<UUID, long[]> lastChunk = new ConcurrentHashMap<>();
    private final Map<UUID, Manor> lastManor = new ConcurrentHashMap<>();
    /** 已付过入场费的玩家 → 已付费的地皮 slot+guild 组合（离开地皮时清除，下次再进要再付）。 */
    private final Map<UUID, String> paidManors = new ConcurrentHashMap<>();
    /** 个人开关：关闭 titles 的玩家集合。 */
    private final Set<UUID> titlesDisabled = ConcurrentHashMap.newKeySet();

    public ManorAccessListener(ManorLookup lookup, EconomyPort economy) {
        this.lookup = lookup;
        this.economy = economy;
    }

    /** 切换玩家的 titles 个人开关。返回 true=现在开启。 */
    public boolean toggleTitles(UUID playerId) {
        if (titlesDisabled.remove(playerId)) {
            return true; // 刚开启
        } else {
            titlesDisabled.add(playerId);
            return false; // 刚关闭
        }
    }

    /** 玩家是否关闭了 titles。 */
    public boolean isTitlesDisabled(UUID playerId) {
        return titlesDisabled.contains(playerId);
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
        boolean bypass = Permissions.canBypassEntry(player) || player.isOp();

        // 黑名单：denied 玩家禁止进入（覆盖一切；owner 不会被判 denied），推回不更新 last*
        if (cur != null && !bypass && ManorRoles.isDenied(cur, ref)) {
            event.setTo(event.getFrom());
            player.sendMessage(Messages.get("listener.blacklisted"));
            return;
        }
        // deny-entry：访客被谢客时推回（owner/trusted/member/管理可进），不更新 last*（下次再判）
        if (cur != null && !canEnter(player, cur) && Flag.DENY_ENTRY.resolveBool(cur.flags())) {
            event.setTo(event.getFrom());
            player.sendMessage(Messages.get("listener.deny_entry"));
            return;
        }
        // deny-exit：非成员被困在 prev 内，离开时传送回地皮中心（成员/管理可自由出入）
        if (prev != null && !sameManor(prev, cur)
                && !canEnter(player, prev) && Flag.DENY_EXIT.resolveBool(prev.flags())) {
            teleportToManorCenter(player, prev);
            player.sendMessage(Messages.get("listener.deny_exit"));
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
            paidManors.remove(id); // 离开地皮后下次再进要重新付费
        }
        // 进入 cur
        if (cur != null) {
            // 入场费：成员/管理免付；访客每次进入收费地皮都要付
            if (!chargeEntry(player, cur, ref)) {
                // 余额不足 → 推回
                event.setTo(event.getFrom());
                return;
            }
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

    /** 进出消息：titles flag 开 → 屏幕标题（尊重个人开关）；否则聊天框。统一翻译 & 颜色码。 */
    private void showMessage(Player player, Manor manor, String raw) {
        String msg = ChatColor.translateAlternateColorCodes('&', raw);
        if (Flag.TITLES.resolveBool(manor.flags()) && !titlesDisabled.contains(player.getUniqueId())) {
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
        return Permissions.canBypassEntry(p) || p.isOp()
                || ManorRoles.isMemberOrAbove(m, PlayerRef.of(p.getUniqueId()));
    }

    /**
     * 入场费扣费。返回 true=允许进入（已付/免费/成员/管理），false=余额不足被拒。
     * 同一地皮同一玩家只收一次（离开后重新进入再收）。
     */
    private boolean chargeEntry(Player player, Manor manor, PlayerRef ref) {
        double price = Flag.PRICE.resolveDouble(manor.flags());
        if (price <= 0 || economy == null) {
            return true; // 免费或无经济插件
        }
        // 成员/管理免付
        if (ManorRoles.isMemberOrAbove(manor, ref) || Permissions.canBypassEntry(player) || player.isOp()) {
            return true;
        }
        String manorKey = manor.guild().value() + ":" + manor.slot();
        String paidKey = paidManors.get(player.getUniqueId());
        if (manorKey.equals(paidKey)) {
            return true; // 已付过
        }
        if (!economy.has(ref, price)) {
            player.sendMessage(Messages.get("listener.price_no_money", economy.format(price)));
            return false;
        }
        economy.withdraw(ref, price);
        paidManors.put(player.getUniqueId(), manorKey);
        player.sendMessage(Messages.get("listener.price_charged", economy.format(price), manor.slot()));
        return true;
    }

    /** 传送到地皮实占范围中心（deny-exit 用）。 */
    private void teleportToManorCenter(Player player, Manor manor) {
        org.bukkit.Location center = lookup.manorCenter(player.getWorld(), manor);
        if (center != null) {
            player.teleport(center);
        } else {
            player.teleport(player.getWorld().getSpawnLocation());
        }
    }

    private static boolean sameManor(Manor a, Manor b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.slot() == b.slot() && a.guild().equals(b.guild());
    }
}
