package org.windy.guildshelter.adapter.bukkit;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.port.ManorRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 合并数据的<b>内存缓存</b>：启动时从 DB 全量加载，merge/unmerge 时同步更新。
 * 避免每次 ROAD chunk 事件都查库（高频路径）。
 *
 * <p>结构：{@code guild → (absorbedSlot → primarySlot)} + {@code guild → primarySlot → Set<absorbedSlot>}（反向索引）。
 * 线程安全：ConcurrentHashMap，读无锁，写原子。
 */
public final class MergeRegistry {

    /** absorbedSlot → primarySlot */
    private final Map<String, Map<Integer, Integer>> absorbedToPrimary = new ConcurrentHashMap<>();
    /** primarySlot → Set<absorbedSlot> */
    private final Map<String, Map<Integer, Set<Integer>>> primaryToAbsorbed = new ConcurrentHashMap<>();

    private final ManorRepository manors;

    public MergeRegistry(ManorRepository manors) {
        this.manors = manors;
    }

    /**
     * 启动时调用：从 DB 一次性全量加载某公会的 merge 数据到内存（单条 SQL，非 N+1）。
     */
    public void load(GuildId guild, List<Integer> allSlots) {
        Map<Integer, Integer> a2p = new ConcurrentHashMap<>();
        Map<Integer, Set<Integer>> p2a = new ConcurrentHashMap<>();
        // 单条 SQL 取全量合并记录，避免每个 slot 一次查询
        for (var entry : manors.getAllMerges(guild)) {
            a2p.put(entry.absorbedSlot(), entry.primarySlot());
            p2a.computeIfAbsent(entry.primarySlot(), k -> ConcurrentHashMap.newKeySet())
               .add(entry.absorbedSlot());
        }
        absorbedToPrimary.put(guild.value(), a2p);
        primaryToAbsorbed.put(guild.value(), p2a);
    }

    /** absorbedSlot 是否被合并到了某 primarySlot。O(1)。 */
    public int getMergedTarget(GuildId guild, int slot) {
        Map<Integer, Integer> a2p = absorbedToPrimary.get(guild.value());
        if (a2p == null) return slot;
        return a2p.getOrDefault(slot, slot);
    }

    /** primarySlot 吸收了哪些 slot。O(1)。 */
    public Set<Integer> getMergedSlots(GuildId guild, int primarySlot) {
        Map<Integer, Set<Integer>> p2a = primaryToAbsorbed.get(guild.value());
        if (p2a == null) return Set.of();
        return p2a.getOrDefault(primarySlot, Set.of());
    }

    /** 记录合并（DB + 缓存同步更新）。 */
    public void merge(GuildId guild, int primarySlot, int absorbedSlot) {
        manors.merge(primarySlot, absorbedSlot, guild);
        absorbedToPrimary.computeIfAbsent(guild.value(), k -> new ConcurrentHashMap<>())
                .put(absorbedSlot, primarySlot);
        primaryToAbsorbed.computeIfAbsent(guild.value(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(primarySlot, k -> ConcurrentHashMap.newKeySet())
                .add(absorbedSlot);
    }

    /** 取消合并（DB + 缓存同步更新）。 */
    public void unmerge(GuildId guild, int primarySlot) {
        manors.unmerge(guild, primarySlot);
        Map<Integer, Set<Integer>> p2a = primaryToAbsorbed.get(guild.value());
        if (p2a != null) {
            Set<Integer> absorbed = p2a.remove(primarySlot);
            if (absorbed != null) {
                Map<Integer, Integer> a2p = absorbedToPrimary.get(guild.value());
                if (a2p != null) {
                    absorbed.forEach(a2p::remove);
                }
            }
        }
    }

    /** 该公会是否有任何合并数据。 */
    public boolean hasMerges(GuildId guild) {
        Map<Integer, Integer> a2p = absorbedToPrimary.get(guild.value());
        return a2p != null && !a2p.isEmpty();
    }

    /** 从缓存中移除单条合并记录（比 reload 高效）。 */
    public void removeOne(GuildId guild, int primarySlot, int absorbedSlot) {
        Map<Integer, Integer> a2p = absorbedToPrimary.get(guild.value());
        if (a2p != null) {
            a2p.remove(absorbedSlot);
        }
        Map<Integer, Set<Integer>> p2a = primaryToAbsorbed.get(guild.value());
        if (p2a != null) {
            Set<Integer> set = p2a.get(primarySlot);
            if (set != null) {
                set.remove(absorbedSlot);
                if (set.isEmpty()) {
                    p2a.remove(primarySlot);
                }
            }
        }
    }

    /** 移除某公会的全部合并缓存（公会解散时调用）。 */
    public void removeGuild(GuildId guild) {
        absorbedToPrimary.remove(guild.value());
        primaryToAbsorbed.remove(guild.value());
    }

    /** 重新加载某公会的 merge 数据（全量重建，仅在批量操作后使用）。 */
    public void reload(GuildId guild, List<Integer> allSlots) {
        absorbedToPrimary.remove(guild.value());
        primaryToAbsorbed.remove(guild.value());
        load(guild, allSlots);
    }
}
