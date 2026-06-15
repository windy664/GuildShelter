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
}
