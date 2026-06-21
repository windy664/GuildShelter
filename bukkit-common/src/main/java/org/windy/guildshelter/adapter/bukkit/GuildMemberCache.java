package org.windy.guildshelter.adapter.bukkit;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.port.ManorRepository;
import org.windy.guildshelter.domain.port.MembershipChangeListener;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公会成员内存缓存：{@code guildId → Set<ownerUUID>}。
 * 启动时从 DB 全量加载，assign/release/dissolve 时同步更新。
 * 供 ClaimGuard 做"该玩家是否在本公会有庄园"判定——O(1) 哈希查找，零 DB 查询。
 */
public final class GuildMemberCache implements MembershipChangeListener {

    /** guildId → Set of owner UUIDs (ConcurrentHashMap.newKeySet for thread safety)。 */
    private final Map<String, Set<UUID>> members = new ConcurrentHashMap<>();

    public GuildMemberCache(ManorRepository manors, List<org.windy.guildshelter.domain.model.GuildWorld> allGuilds) {
        for (var gw : allGuilds) {
            loadGuild(gw.guild(), manors);
        }
    }

    /** 启动时加载某公会的所有庄园主到缓存。 */
    private void loadGuild(GuildId guild, ManorRepository manors) {
        Set<UUID> set = ConcurrentHashMap.newKeySet();
        for (Manor m : manors.findAll(guild)) {
            set.add(m.owner().uuid());
        }
        members.put(guild.value(), set);
    }

    /** 该玩家是否在指定公会拥有庄园。O(1)。 */
    public boolean isMember(GuildId guild, UUID playerId) {
        Set<UUID> set = members.get(guild.value());
        return set != null && set.contains(playerId);
    }

    /** 新分配庄园时调用。 */
    public void addMember(GuildId guild, UUID playerId) {
        members.computeIfAbsent(guild.value(), k -> ConcurrentHashMap.newKeySet()).add(playerId);
    }

    /** 释放庄园时调用。 */
    public void removeMember(GuildId guild, UUID playerId) {
        Set<UUID> set = members.get(guild.value());
        if (set != null) {
            set.remove(playerId);
        }
    }

    /** 公会解散时清除。 */
    public void removeGuild(GuildId guild) {
        members.remove(guild.value());
    }

    // ===== MembershipChangeListener 实现（GuildService 回调）=====

    @Override
    public void onMemberAssigned(GuildId guild, UUID playerId) {
        addMember(guild, playerId);
    }

    @Override
    public void onMemberReleased(GuildId guild, UUID playerId) {
        removeMember(guild, playerId);
    }

    @Override
    public void onGuildDissolved(GuildId guild) {
        removeGuild(guild);
    }
}
