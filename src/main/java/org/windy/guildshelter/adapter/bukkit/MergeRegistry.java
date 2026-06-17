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
     * 启动时调用：从 DB 全量加载某公会的 merge 数据到内存。
     * 对每个已知 primary slot 读取其 absorbed 列表。
     */
    public void load(GuildId guild, List<Integer> allSlots) {
        Map<Integer, Integer> a2p = new ConcurrentHashMap<>();
        Map<Integer, Set<Integer>> p2a = new ConcurrentHashMap<>();
        for (int slot : allSlots) {
            List<Integer> absorbed = manors.getMergedSlots(guild, slot);
            if (!absorbed.isEmpty()) {
                p2a.put(slot, ConcurrentHashMap.newKeySet());
                for (int a : absorbed) {
                    a2p.put(a, slot);
                    p2a.get(slot).add(a);
                }
            }
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

    /** 重新加载某公会的 merge 数据（用于单条删除后刷新缓存）。 */
    public void reload(GuildId guild, List<Integer> allSlots) {
        absorbedToPrimary.remove(guild.value());
        primaryToAbsorbed.remove(guild.value());
        load(guild, allSlots);
    }
}
