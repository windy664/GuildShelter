package org.windy.guildshelter.adapter.bukkit;

import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.layout.LayoutConfig;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.ManorRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 性能缓存：避免热路径上重复创建对象。
 *
 * <ul>
 *   <li>LayoutCalculator — 不可变，按 LayoutConfig 实例缓存（同一世界永远相同）</li>
 *   <li>MergeAwareClassifier — 按 guild 缓存，内部引用 MergeRegistry（已是内存缓存）</li>
 *   <li>PlayerRef — 不可变 record，按 UUID 缓存</li>
 * </ul>
 */
public final class WorldCache {

    /** LayoutConfig.hashCode() → LayoutCalculator（LayoutConfig 是 record，hashCode 稳定）。 */
    private final Map<Integer, LayoutCalculator> layoutCache = new ConcurrentHashMap<>();
    /** GuildId → MergeAwareClassifier。 */
    private final Map<String, MergeAwareClassifier> mergerCache = new ConcurrentHashMap<>();
    /** UUID → PlayerRef（不可变 record，缓存无副作用）。 */
    private final Map<UUID, PlayerRef> playerRefCache = new ConcurrentHashMap<>();

    /** Manor 短 TTL 缓存："guildId:slot" → (Manor, timestamp)。避免每次事件查库。 */
    private static final long MANOR_CACHE_TTL_MS = 2000; // 2 秒
    private record ManorEntry(Manor manor, long timestamp) {}
    private final Map<String, ManorEntry> manorCache = new ConcurrentHashMap<>();
    private volatile ManorRepository manorRepo; // 延迟注入

    private final MergeRegistry merges;

    public WorldCache(MergeRegistry merges) {
        this.merges = merges;
    }

    /** 获取或创建 LayoutCalculator（O(1) 哈希查找，无 DB 查询）。 */
    public LayoutCalculator layout(LayoutConfig config) {
        return layoutCache.computeIfAbsent(config.hashCode(), k -> new LayoutCalculator(config));
    }

    /** 获取或创建 MergeAwareClassifier（O(1) 哈希查找）。 */
    public MergeAwareClassifier merger(LayoutCalculator layout, GuildId guild) {
        return mergerCache.computeIfAbsent(guild.value(), k -> new MergeAwareClassifier(layout, merges, guild));
    }

    /** 底层 MergeRegistry 引用（供 ClaimGuard 快速检查 hasMerges）。 */
    public MergeRegistry merges() {
        return merges;
    }

    /** 获取或创建 PlayerRef（O(1) 哈希查找，无对象分配）。 */
    public PlayerRef playerRef(UUID uuid) {
        return playerRefCache.computeIfAbsent(uuid, PlayerRef::of);
    }

    /** 移除玩家的 PlayerRef 缓存（退出时调用，防内存泄漏）。 */
    public void removePlayerRef(UUID uuid) {
        playerRefCache.remove(uuid);
    }

    /** 延迟注入 ManorRepository（避免构造函数循环依赖）。 */
    public void setManorRepository(ManorRepository repo) {
        this.manorRepo = repo;
    }

    /**
     * 带短 TTL 缓存的 Manor 查找。ClaimGuard 等热路径用此方法避免每次事件查库。
     * TTL 2 秒——flag/权限变更最多 2 秒生效，可接受。
     */
    public Manor manorAt(GuildWorld gw, int slot) {
        if (manorRepo == null) return null;
        String key = gw.guild().value() + ":" + slot;
        long now = System.currentTimeMillis();
        ManorEntry entry = manorCache.get(key);
        if (entry != null && now - entry.timestamp < MANOR_CACHE_TTL_MS) {
            return entry.manor;
        }
        Manor m = manorRepo.findBySlot(gw.guild(), slot).orElse(null);
        manorCache.put(key, new ManorEntry(m, now));
        return m;
    }

    /** 使指定庄园的缓存失效（flag/成员变更时调用）。 */
    public void invalidateManor(GuildId guild, int slot) {
        manorCache.remove(guild.value() + ":" + slot);
    }

    /** 清除指定公会的合并缓存（公会解散或合并变更时调用）。 */
    public void invalidateMerge(GuildId guild) {
        mergerCache.remove(guild.value());
    }

    /** 清除所有缓存（重载配置时调用）。 */
    public void clearAll() {
        layoutCache.clear();
        mergerCache.clear();
        playerRefCache.clear();
    }
}
