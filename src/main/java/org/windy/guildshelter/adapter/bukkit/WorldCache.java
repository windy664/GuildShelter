package org.windy.guildshelter.adapter.bukkit;

import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.layout.LayoutConfig;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.PlayerRef;

import java.util.Map;
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
