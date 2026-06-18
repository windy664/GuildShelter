package org.windy.guildshelter.domain.port;

import org.windy.guildshelter.domain.model.GuildId;

import java.util.UUID;

/**
 * 公会成员变更回调（领域层定义，适配层实现）。
 * GuildService 在 assign/release/dissolve 时通知外部缓存同步更新。
 */
public interface MembershipChangeListener {
    void onMemberAssigned(GuildId guild, UUID playerId);
    void onMemberReleased(GuildId guild, UUID playerId);
    void onGuildDissolved(GuildId guild);
}
