package org.windy.guildshelter.adapter.bukkit;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.port.CityFlagStore;
import org.windy.guildshelter.domain.port.MembershipChangeListener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主城 flag 的内存缓存：{@code guild → (flagId → value)}。启动时从 {@link CityFlagStore} 全量加载，
 * set/unset/解散时同步内存 + 持久层。供 {@link ManorLookup#resolveFlag} 在<b>热路径</b>读取主城 chunk 的
 * flag——O(1) 哈希查找，零 DB 查询。
 *
 * <p>主城没有 {@link org.windy.guildshelter.domain.model.Manor}，其 flag 不入 manor.flags，单独存于此缓存。
 */
public final class CityFlagCache implements MembershipChangeListener {

    private final CityFlagStore store;
    private final Map<String, Map<String, String>> byGuild = new ConcurrentHashMap<>();

    public CityFlagCache(CityFlagStore store, List<GuildWorld> allGuilds) {
        this.store = store;
        for (GuildWorld gw : allGuilds) {
            byGuild.put(gw.guild().value(), new ConcurrentHashMap<>(store.flags(gw.guild())));
        }
    }

    /** 该公会主城的 flag map（flagId→value）；热路径用，返回内存视图（调用方只读）。 */
    public Map<String, String> flags(GuildId guild) {
        Map<String, String> m = byGuild.get(guild.value());
        return m == null ? Map.of() : m;
    }

    /** 设置主城 flag（内存 + 持久层）。 */
    public void put(GuildId guild, String flagId, String value) {
        byGuild.computeIfAbsent(guild.value(), k -> new ConcurrentHashMap<>()).put(flagId, value);
        store.put(guild, flagId, value);
    }

    /** 取消主城 flag（内存 + 持久层）。 */
    public void remove(GuildId guild, String flagId) {
        Map<String, String> m = byGuild.get(guild.value());
        if (m != null) {
            m.remove(flagId);
        }
        store.remove(guild, flagId);
    }

    /** 公会解散：清内存 + 持久层。 */
    public void removeGuild(GuildId guild) {
        byGuild.remove(guild.value());
        store.clear(guild);
    }

    // ===== MembershipChangeListener：成员变动不影响主城 flag，仅解散清空 =====

    @Override
    public void onMemberAssigned(GuildId guild, UUID playerId) {
        // no-op
    }

    @Override
    public void onMemberReleased(GuildId guild, UUID playerId) {
        // no-op
    }

    @Override
    public void onGuildDissolved(GuildId guild) {
        removeGuild(guild);
    }
}
