package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.entity.Player;
import org.windy.guildshelter.domain.flag.InteractCategory;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;

import java.util.Optional;

/**
 * 访客交互的<b>平台中立</b>判定（Bukkit 与 NeoForge 两侧监听器共用）。在领地<b>硬保护</b>
 * （破坏/放置由 {@link ClaimGuard} 一律挡访客）之上，做一层<b>按类放宽</b>：
 * 成员/管理始终放行；访客则看其所在地皮是否用对应 flag（use/container/item-frame/vehicle-use）
 * 开放了这一类交互。
 *
 * <p>只依赖 Bukkit API（混合端也有）+ domain，不碰 NeoForge，故可被两侧引用。
 */
public final class InteractionPolicy {

    private final ClaimGuard guard;
    private final ManorLookup lookup;

    public InteractionPolicy(ClaimGuard guard, ManorLookup lookup) {
        this.guard = guard;
        this.lookup = lookup;
    }

    /**
     * 该玩家能否对其所在世界 (blockX,blockZ) 处做 {@code category} 类交互。
     * <ul>
     *   <li>成员/管理/非公会世界 → 走 {@link ClaimGuard#allowed} 直接放行；</li>
     *   <li>admin.interact.other/road → 细粒度 admin 放行；</li>
     *   <li>访客在某成员地皮内 → 看该地皮对应 flag（默认 false=拒绝）；</li>
     *   <li>公共区/道路/无地皮 → 保持拒绝（与硬保护一致）。</li>
     * </ul>
     */
    public boolean allowed(Player player, int blockX, int blockZ, InteractCategory category) {
        if (guard.allowed(player, blockX, blockZ)) {
            return true; // owner/trusted/member(在线)/管理/非公会世界
        }
        // 细粒度 admin 交互权限：按区域类型分流
        if (player.isOp()) {
            return true;
        }
        Optional<Manor> at = lookup.at(player.getWorld(), blockX, blockZ);
        if (at.isPresent()) {
            // 在某成员地皮内：admin.interact.other 放行（覆盖 denied，设计意图）
            if (Permissions.hasAdminPerm(player, Permissions.ADMIN_INTERACT_OTHER)) {
                return true;
            }
            Manor manor = at.get();
            // 黑名单：denied 玩家覆盖访客 flag，一律拒（持 admin.bypass.entry 的工作人员除外）。
            if (ManorRoles.isDenied(manor, PlayerRef.of(player.getUniqueId()))
                    && !Permissions.canBypassEntry(player)) {
                return false;
            }
            return category.flag().resolveBool(manor.flags());
        }
        // 无地皮 = 公共区/道路：admin.interact.road 放行
        return Permissions.hasAdminPerm(player, Permissions.ADMIN_INTERACT_ROAD);
    }

    /** 被拦截时复用 {@link ClaimGuard} 的限频提示。 */
    public void notifyDenied(Player player) {
        guard.notifyDenied(player);
    }
}
