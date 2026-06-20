package org.windy.guildshelter.adapter.bukkit;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.port.RoadPermitStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限时路权内存缓存：{@code guild → (playerUUID → 到期毫秒)}。启动全量加载，授予/撤销时同步内存+持久层。
 * 供 {@link ClaimGuard}/{@link InteractionPolicy} 热路径 O(1) 判定，<b>惰性过期</b>（查时比 now）。
 */
public final class RoadPermitCache {

    private final RoadPermitStore store;
    private final Map<String, Map<UUID, Long>> permits = new ConcurrentHashMap<>();

    public RoadPermitCache(RoadPermitStore store) {
        this.store = store;
        for (RoadPermitStore.Entry e : store.loadAll()) {
            permits.computeIfAbsent(e.guild().value(), k -> new ConcurrentHashMap<>())
                    .put(e.player(), e.expireAtMillis());
        }
    }

    /** 该玩家此刻在该营地是否持有<b>未过期</b>路权。O(1)，热路径。 */
    public boolean hasPermit(GuildId guild, UUID player) {
        Map<UUID, Long> m = permits.get(guild.value());
        if (m == null) {
            return false;
        }
        Long expire = m.get(player);
        return expire != null && expire > System.currentTimeMillis();
    }

    /** 该玩家在该营地路权的到期时间戳（毫秒）；无/已过期返回 0。 */
    public long expireAt(GuildId guild, UUID player) {
        Map<UUID, Long> m = permits.get(guild.value());
        Long e = m == null ? null : m.get(player);
        return (e != null && e > System.currentTimeMillis()) ? e : 0L;
    }

    /** 授予/续期（内存 + 持久层）。 */
    public void grant(GuildId guild, UUID player, long expireAtMillis) {
        permits.computeIfAbsent(guild.value(), k -> new ConcurrentHashMap<>()).put(player, expireAtMillis);
        store.grant(guild, player, expireAtMillis);
    }

    /** 撤销（内存 + 持久层）。 */
    public void revoke(GuildId guild, UUID player) {
        Map<UUID, Long> m = permits.get(guild.value());
        if (m != null) {
            m.remove(player);
        }
        store.revoke(guild, player);
    }
}
