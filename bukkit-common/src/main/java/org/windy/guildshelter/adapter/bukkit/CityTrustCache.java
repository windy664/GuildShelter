package org.windy.guildshelter.adapter.bukkit;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.port.CityTrustStore;
import org.windy.guildshelter.domain.port.MembershipChangeListener;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主城信任名单的内存缓存：{@code guild → Set<UUID>}（会长额外信任、可建主城的会内成员）。
 * 启动时从 {@link CityTrustStore} 全量加载，trust/untrust/解散时同步内存+持久层。
 * 供 {@link ClaimGuard} 热路径做主城建造判定——O(1) 哈希查找，零 DB 查询。
 *
 * <p>会长本身的主城权限来自 {@link org.windy.guildshelter.domain.port.GuildProvider#isGuildAdmin}，不在此缓存。
 */
public final class CityTrustCache implements MembershipChangeListener {

    private final CityTrustStore store;
    private final Map<String, Set<UUID>> trusted = new ConcurrentHashMap<>();

    public CityTrustCache(CityTrustStore store, List<GuildWorld> allGuilds) {
        this.store = store;
        for (GuildWorld gw : allGuilds) {
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            set.addAll(store.trusted(gw.guild()));
            trusted.put(gw.guild().value(), set);
        }
    }

    /** 该玩家是否被该公会主城信任。O(1)，热路径用。 */
    public boolean isTrusted(GuildId guild, UUID player) {
        Set<UUID> set = trusted.get(guild.value());
        return set != null && set.contains(player);
    }

    /** 信任某玩家可建主城（内存 + 持久层）。 */
    public void add(GuildId guild, UUID player) {
        trusted.computeIfAbsent(guild.value(), k -> ConcurrentHashMap.newKeySet()).add(player);
        store.add(guild, player);
    }

    /** 撤销主城信任（内存 + 持久层）。 */
    public void remove(GuildId guild, UUID player) {
        Set<UUID> set = trusted.get(guild.value());
        if (set != null) {
            set.remove(player);
        }
        store.remove(guild, player);
    }

    /** 公会解散：清内存 + 持久层。 */
    public void removeGuild(GuildId guild) {
        trusted.remove(guild.value());
        store.clear(guild);
    }

    /** 当前主城信任名单快照（命令展示用）。 */
    public Set<UUID> snapshot(GuildId guild) {
        Set<UUID> set = trusted.get(guild.value());
        return set == null ? Set.of() : Set.copyOf(set);
    }

    // ===== MembershipChangeListener：离会自动撤主城信任、解散清空 =====

    @Override
    public void onMemberAssigned(GuildId guild, UUID playerId) {
        // 入会不自动授予主城信任（须会长显式 /gs citytrust）。
    }

    @Override
    public void onMemberReleased(GuildId guild, UUID playerId) {
        remove(guild, playerId); // 离会/退会 → 撤其主城信任（内存 + 持久层）
    }

    @Override
    public void onGuildDissolved(GuildId guild) {
        removeGuild(guild);
    }
}
