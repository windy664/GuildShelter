package org.windy.guildshelter.domain.model;

import java.util.Objects;
import java.util.Set;

/**
 * 成员庄园：占公会世界里的一个螺旋 slot。物理范围（满级地皮 / 当前实占）由 LayoutCalculator
 * 从 slot + level 算出，这里只存归属与等级。
 *
 * @param slot       螺旋 slot 号（0 起）
 * @param guild      所属公会
 * @param owner      庄园主
 * @param level      庄园等级（决定实占大小，受公会等级封顶）
 * @param coBuilders 共建人（owner 之外可建造的成员）
 */
public record Manor(int slot, GuildId guild, PlayerRef owner, int level, Set<PlayerRef> coBuilders) {

    public Manor {
        if (slot < 0) {
            throw new IllegalArgumentException("slot 必须 ≥0");
        }
        Objects.requireNonNull(guild, "guild");
        Objects.requireNonNull(owner, "owner");
        if (level < 1) {
            throw new IllegalArgumentException("level 必须 ≥1");
        }
        coBuilders = Set.copyOf(coBuilders == null ? Set.of() : coBuilders);
    }

    public static Manor create(int slot, GuildId guild, PlayerRef owner) {
        return new Manor(slot, guild, owner, 1, Set.of());
    }

    /** 该玩家是否可在本庄园建造（庄主或共建人）。 */
    public boolean hasBuildAccess(PlayerRef player) {
        return owner.equals(player) || coBuilders.contains(player);
    }

    public Manor withLevel(int newLevel) {
        return new Manor(slot, guild, owner, newLevel, coBuilders);
    }
}
