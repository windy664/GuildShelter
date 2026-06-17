package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.ManorRole;
import org.windy.guildshelter.domain.model.PlayerRef;

/**
 * 把 {@link Manor#baseRoleOf 基础身份} 落到运行期的<b>有效权限</b>——唯一需要 Bukkit 在线信息的地方
 * （domain 不掺）就在这里：MEMBER 的"上级在线才生效"门控。Bukkit/NeoForge 两侧的保护与交互判定共用本类。
 */
public final class ManorRoles {

    private ManorRoles() {
    }

    /** 有效建造/交互权：OWNER/TRUSTED 恒真；MEMBER 仅当上级(owner 或某 trusted)在线；DENIED/VISITOR 假。 */
    public static boolean effectiveBuild(Manor manor, PlayerRef player) {
        return switch (manor.baseRoleOf(player)) {
            case OWNER, TRUSTED -> true;
            case MEMBER -> supervisorOnline(manor);
            case DENIED, VISITOR -> false;
        };
    }

    /** 带缓存的版本：MEMBER 门控走 SupervisorCache（每 5 秒刷新一次）。 */
    public static boolean effectiveBuildCached(Manor manor, PlayerRef player, SupervisorCache cache) {
        return switch (manor.baseRoleOf(player)) {
            case OWNER, TRUSTED -> true;
            case MEMBER -> cache.supervisorOnline(manor);
            case DENIED, VISITOR -> false;
        };
    }

    /** owner 或任一 trusted 在线——MEMBER 的在线门控依据。 */
    public static boolean supervisorOnline(Manor manor) {
        if (isOnline(manor.owner())) {
            return true;
        }
        for (PlayerRef t : manor.trusted()) {
            if (isOnline(t)) {
                return true;
            }
        }
        return false;
    }

    /** 该玩家是否被本庄园拉黑（owner 永不被判为 denied）。 */
    public static boolean isDenied(Manor manor, PlayerRef player) {
        return manor.baseRoleOf(player) == ManorRole.DENIED;
    }

    /** owner/trusted/member（不论在线）——可无视 deny-entry 自由进入。 */
    public static boolean isMemberOrAbove(Manor manor, PlayerRef player) {
        return switch (manor.baseRoleOf(player)) {
            case OWNER, TRUSTED, MEMBER -> true;
            case DENIED, VISITOR -> false;
        };
    }

    private static boolean isOnline(PlayerRef ref) {
        Player p = Bukkit.getPlayer(ref.uuid());
        return p != null && p.isOnline();
    }
}
