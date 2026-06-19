package org.windy.guildshelter.domain.rule;

import org.windy.guildshelter.domain.layout.Classification;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;

import java.util.function.BiPredicate;
import java.util.function.IntFunction;

/**
 * 领地权限规则（纯函数，可脱机测）。被 NeoForge / Bukkit 两侧保护监听器共用。
 *
 * <p>规则（v2）：
 * <ul>
 *   <li>非本公会成员：在该公会世界一律不可建造/破坏。</li>
 *   <li>主城：默认只有<b>会长 / 会长信任的会内成员</b>可建造（由 {@code canBuildCity} 注入判定）。</li>
 *   <li>路：任何人不可建造。</li>
 *   <li>地皮：必须是该 slot 庄园的庄主/共建人，且坐标落在当前<b>实占</b>范围内
 *       （已生成但未随等级解锁的预留外圈不可建）。未分配的 slot 一律不可建。</li>
 * </ul>
 */
public final class PermissionRules {

    /**
     * 玩家能否在该公会世界的 chunk (chunkX,chunkZ) 改动方块。
     *
     * @param layout       <b>该世界</b>的布局计算器（用世界自己冻结的参数，不能用全局）
     * @param player       行为玩家
     * @param playerInGuild 该玩家是否属于本世界对应的公会（由 GuildProvider 判定后传入）
     * @param manorBySlot  slot → 该 slot 的庄园（不存在返回 null）
     */
    public boolean canModify(LayoutCalculator layout, PlayerRef player, boolean playerInGuild,
                             IntFunction<Manor> manorBySlot, int chunkX, int chunkZ) {
        // 默认：地皮判定=庄主/共建人；主城判定=任意会员（旧行为，脱机测用）。
        return canModify(layout, player, playerInGuild, manorBySlot, chunkX, chunkZ,
                Manor::hasBuildAccess, p -> true);
    }

    /**
     * 同上，但地皮内的"可建造"判定由调用方注入（{@code canBuild}）。主城退化为任意会员可建。
     */
    public boolean canModify(LayoutCalculator layout, PlayerRef player, boolean playerInGuild,
                             IntFunction<Manor> manorBySlot, int chunkX, int chunkZ,
                             BiPredicate<Manor, PlayerRef> canBuild) {
        return canModify(layout, player, playerInGuild, manorBySlot, chunkX, chunkZ, canBuild, p -> true);
    }

    /**
     * 完整版：地皮可建判定 {@code canBuild} 与<b>主城可建判定</b> {@code canBuildCity} 都由调用方注入，
     * domain 仍保持纯。{@code canBuildCity} 接入"会长 / 会长信任的会内成员"（需运行期角色/信任信息）。
     */
    public boolean canModify(LayoutCalculator layout, PlayerRef player, boolean playerInGuild,
                             IntFunction<Manor> manorBySlot, int chunkX, int chunkZ,
                             BiPredicate<Manor, PlayerRef> canBuild,
                             java.util.function.Predicate<PlayerRef> canBuildCity) {
        if (!playerInGuild) {
            return false;
        }
        Classification c = layout.classify(chunkX, chunkZ);
        return switch (c.type()) {
            case MAIN_CITY -> canBuildCity.test(player);
            case ROAD -> false;
            case PLOT -> {
                Manor m = manorBySlot.apply(c.slot());
                if (m == null || !canBuild.test(m, player)) {
                    yield false;
                }
                yield layout.activeRegion(c.slot(), m.level()).containsChunk(chunkX, chunkZ);
            }
        };
    }
}
