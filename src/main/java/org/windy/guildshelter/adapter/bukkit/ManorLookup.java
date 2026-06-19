package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.Location;
import org.bukkit.World;
import org.windy.guildshelter.domain.layout.Classification;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.layout.RegionType;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.port.ManorRepository;

import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.port.ManorRepository;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 坐标 → 该处所属成员庄园(若有)。复用世界注册表 + 该世界冻结布局做归类。
 * flag 执行监听器用它判断"这块方块/这个位置属于哪块庄园、其 flag 如何"。
 */
public final class ManorLookup {

    private final GuildWorldRegistry registry;
    private final ManorRepository manors;
    private final WorldCache cache;

    /** 子领地缓存 TTL（子领地变更频率低，长缓存无问题）。 */
    private static final long SUB_CACHE_TTL_MS = 10_000;
    private record SubCacheEntry(java.util.List<ManorRepository.SubEntry> subs, long ts) {}
    private final java.util.Map<String, SubCacheEntry> subCache = new java.util.concurrent.ConcurrentHashMap<>();

    public ManorLookup(GuildWorldRegistry registry, ManorRepository manors, WorldCache cache) {
        this.registry = registry;
        this.manors = manors;
        this.cache = cache;
    }

    public boolean isGuildWorld(World world) {
        return registry.isGuildWorld(world.getName());
    }

    /**
     * 返回 (blockX,blockZ) 处的成员庄园；不在公会营地/不在庄园/未分配则 empty。合并路 chunk 归主庄园。
     *
     * <p>性能：走 {@link WorldCache#manorAt} 的 2 秒 TTL 缓存，正常情况 0 次 DB 查询。
     * 所有监听器（Flag/Env/Entity/Protection/Access）共用此方法，确保热路径不查库。
     */
    public Optional<Manor> at(World world, int blockX, int blockZ) {
        GuildWorld gw = registry.get(world.getName());
        if (gw == null) {
            return Optional.empty();
        }
        LayoutCalculator layout = cache.layout(gw.layout()); // 缓存命中 O(1)
        int lx = (blockX >> 4) - gw.originChunkX();
        int lz = (blockZ >> 4) - gw.originChunkZ();
        OptionalInt slot = layout.slotAt(lx, lz);
        if (slot.isPresent()) {
            Manor m = cache.manorAt(gw, slot.getAsInt()); // 2 秒 TTL 缓存，非查库
            return Optional.ofNullable(m);
        }
        // 原始 classify 不是 PLOT → 检查是否为合并路 chunk
        if (cache.merges().hasMerges(gw.guild())) {
            Classification raw = layout.classify(lx, lz);
            if (raw.type() == RegionType.ROAD) {
                MergeAwareClassifier merger = cache.merger(layout, gw.guild()); // 缓存命中 O(1)
                Classification merged = merger.classify(lx, lz);
                if (merged.isPlot()) {
                    Manor m = cache.manorAt(gw, merged.slot()); // 2 秒 TTL 缓存
                    return Optional.ofNullable(m);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 查找 (blockX,blockZ) 处的子领地（如果有）。带 10 秒 TTL 缓存。
     * 子领地的 flag 会覆盖所在庄园的对应 flag。
     */
    public Optional<ManorRepository.SubEntry> subAt(GuildWorld gw, Manor manor, int blockX, int blockZ) {
        String cacheKey = gw.guild().value() + ":" + manor.slot();
        long now = System.currentTimeMillis();
        SubCacheEntry cached = subCache.get(cacheKey);
        java.util.List<ManorRepository.SubEntry> subs;
        if (cached != null && now - cached.ts < SUB_CACHE_TTL_MS) {
            subs = cached.subs();
        } else {
            subs = manors.getSubs(gw.guild(), manor.slot());
            subCache.put(cacheKey, new SubCacheEntry(subs, now));
        }
        for (ManorRepository.SubEntry sub : subs) {
            if (blockX >= sub.minX() && blockX <= sub.maxX() && blockZ >= sub.minZ() && blockZ <= sub.maxZ()) {
                return Optional.of(sub);
            }
        }
        return Optional.empty();
    }

    /**
     * 解析某位置的 flag 值：子领地 flag 优先 → 庄园 flag → flag 默认值。
     * 供保护监听器使用，实现子领地级别的细粒度控制。
     */
    public boolean resolveFlag(World world, int blockX, int blockZ, Flag flag) {
        Optional<Manor> manorOpt = at(world, blockX, blockZ);
        if (manorOpt.isEmpty()) return flag.resolveBool(Map.of()); // 无庄园 → 用默认值
        Manor manor = manorOpt.get();
        // 先查子领地
        GuildWorld gw = registry.get(world.getName());
        if (gw != null) {
            Optional<ManorRepository.SubEntry> subOpt = subAt(gw, manor, blockX, blockZ);
            if (subOpt.isPresent()) {
                String subVal = subOpt.get().flags().get(flag.id());
                if (subVal != null) return Boolean.parseBoolean(subVal);
            }
        }
        // 子领地没设 → 用庄园 flag
        return flag.resolveBool(manor.flags());
    }

    /** 清除子领地缓存（庄主修改子领地时调用）。 */
    public void invalidateSub(GuildId guild, int slot) {
        subCache.remove(guild.value() + ":" + slot);
    }

    /** 庄园实占范围中心的 Location（供 deny-exit 传送用）。 */
    public Location manorCenter(World world, Manor manor) {
        GuildWorld gw = registry.get(world.getName());
        if (gw == null) return null;
        ChunkRegion active = new LayoutCalculator(gw.layout())
                .activeRegion(manor.slot(), manor.level())
                .shift(gw.originChunkX(), gw.originChunkZ());
        int cx = (active.minBlockX() + active.maxBlockX()) / 2;
        int cz = (active.minBlockZ() + active.maxBlockZ()) / 2;
        world.loadChunk(cx >> 4, cz >> 4, true);
        int cy = world.getHighestBlockYAt(cx, cz) + 1;
        return new Location(world, cx + 0.5, cy, cz + 0.5);
    }
}
