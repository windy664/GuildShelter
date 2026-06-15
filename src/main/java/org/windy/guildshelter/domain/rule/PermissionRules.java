package org.windy.guildshelter.domain.rule;

import org.windy.guildshelter.domain.layout.Classification;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;

import java.util.function.IntFunction;

/**
 * 领地权限规则（纯函数，可脱机测）。被 NeoForge / Bukkit 两侧保护监听器共用。
 *
 * <p>规则（v1）：
 * <ul>
 *   <li>非本公会成员：在该公会世界一律不可建造/破坏。</li>
 *   <li>主城：本公会成员可建造。</li>
 *   <li>路：任何人不可建造。</li>
 *   <li>地皮：必须是该 slot 庄园的庄主/共建人，且坐标落在当前<b>实占</b>范围内
 *       （已生成但未随等级解锁的预留外圈不可建）。未分配的 slot 一律不可建。</li>
 * </ul>
 */
public final class PermissionRules {

    private final LayoutCalculator layout;

    public PermissionRules(LayoutCalculator layout) {
        this.layout = layout;
    }

    /**
     * 玩家能否在该公会世界的 chunk (chunkX,chunkZ) 改动方块。
     *
     * @param player       行为玩家
     * @param playerInGuild 该玩家是否属于本世界对应的公会（由 GuildProvider 判定后传入）
     * @param manorBySlot  slot → 该 slot 的庄园（不存在返回 null）
     */
    public boolean canModify(PlayerRef player, boolean playerInGuild,
                             IntFunction<Manor> manorBySlot, int chunkX, int chunkZ) {
        if (!playerInGuild) {
            return false;
        }
        Classification c = layout.classify(chunkX, chunkZ);
        return switch (c.type()) {
            case MAIN_CITY -> true;
            case ROAD -> false;
            case PLOT -> {
                Manor m = manorBySlot.apply(c.slot());
                if (m == null || !m.hasBuildAccess(player)) {
                    yield false;
                }
                yield layout.activeRegion(c.slot(), m.level()).containsChunk(chunkX, chunkZ);
            }
        };
    }
}
