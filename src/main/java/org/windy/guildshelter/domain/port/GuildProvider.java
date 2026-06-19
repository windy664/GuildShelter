package org.windy.guildshelter.domain.port;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.PlayerRef;

import java.util.Optional;

/**
 * 公会身份的外部来源（查询侧）。本插件是附属品——"玩家在不在某公会"完全委托给它。
 * 先由 {@code LegendaryGuildProvider} 实现，将来换公会插件只需再加一个实现。
 *
 * <p>建会/加入/退出/解散等生命周期由各 provider 适配器把外部插件事件翻译成对
 * {@code GuildService} 的调用，不在本接口内。
 */
public interface GuildProvider {

    /** 玩家当前所属公会（不在任何公会则空）。 */
    Optional<GuildId> guildOf(PlayerRef player);

    /** 玩家是否属于指定公会。 */
    boolean isMember(PlayerRef player, GuildId guild);

    /** 公会展示名。 */
    String displayName(GuildId guild);

    /**
     * 玩家是否是该公会的<b>会长/管理</b>（会长 + 副会长/官员）。供主城建造/信任授权等高权操作判定。
     * 宿主插件无角色概念时返回 false（退化为只有显式信任生效）。默认 false。
     */
    default boolean isGuildAdmin(PlayerRef player, GuildId guild) {
        return false;
    }

    /**
     * 宿主插件给出的公会<b>人数上限</b>（含额外名额）。GuildShelter 据此决定发多少庄园 slot；
     * 宿主无人数上限概念时返回 empty，调用方退回 GuildShelter 自己的等级容量。默认 empty。
     */
    default java.util.OptionalInt memberCap(GuildId guild) {
        return java.util.OptionalInt.empty();
    }

    /** 无宿主角色/容量信息的空实现（仅装了无此能力的公会插件、或未接公会插件时用）。 */
    GuildProvider NONE = new GuildProvider() {
        @Override public Optional<GuildId> guildOf(PlayerRef player) {
            return Optional.empty();
        }
        @Override public boolean isMember(PlayerRef player, GuildId guild) {
            return false;
        }
        @Override public String displayName(GuildId guild) {
            return guild.value();
        }
    };
}
