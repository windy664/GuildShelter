package org.windy.guildshelter.adapter.bukkit;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.Manor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存 supervisorOnline 结果（MEMBER 建造/交互门控）。
 * 每 5 秒过期一次，避免每次事件都遍历所有 trusted 玩家。
 */
public final class SupervisorCache {

    private static final long TTL_MS = 5000; // 5 秒

    /** "guildId:slot" → 缓存条目。 */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 检查某庄园是否有上级在线（带缓存）。
     * 每 TTL_MS 秒才重新扫描一次 trusted 玩家。
     */
    public boolean supervisorOnline(Manor manor) {
        String key = manor.guild().value() + ":" + manor.slot();
        CacheEntry entry = cache.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && now - entry.timestamp < TTL_MS) {
            return entry.result;
        }
        boolean result = ManorRoles.supervisorOnline(manor);
        cache.put(key, new CacheEntry(result, now));
        return result;
    }

    /** 清除指定庄园的缓存（玩家上下线时调用）。 */
    public void invalidate(GuildId guild, int slot) {
        cache.remove(guild.value() + ":" + slot);
    }

    /** 清除指定公会的所有缓存。 */
    public void invalidateGuild(GuildId guild) {
        String prefix = guild.value() + ":";
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** 清除所有缓存。 */
    public void clearAll() {
        cache.clear();
    }

    private record CacheEntry(boolean result, long timestamp) {}
}
