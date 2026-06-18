package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.flag.ManorEntityClass;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;

import java.util.Map;

/**
 * 地皮实体计数服务（平台中立判定，只依赖 Bukkit API，混合端也有）。<b>实时</b>扫描某地皮当前实占
 * chunk 范围内的实体并按 {@link ManorEntityClass} 归类——不持久化任何计数器。
 *
 * <p>既供实体上限 caps 做"再生成是否超限"的拦截，也作为<b>可复用 API</b> 供未来"家园卡/评分/
 * 谁的家园更值钱"等按实体数量判断的功能直接取数（{@link GuildShelterPlugin#entityCensus()}）。
 */
public final class ManorEntityCensus {

    private static final long CENSUS_TTL_MS = 3000; // 3 秒缓存

    private final GuildWorldRegistry registry;
    /** "guildId:slot" → (Census, timestamp) 缓存。 */
    private final java.util.Map<String, CensusEntry> censusCache = new java.util.concurrent.ConcurrentHashMap<>();

    private record CensusEntry(Census census, long timestamp) {}

    public ManorEntityCensus(GuildWorldRegistry registry) {
        this.registry = registry;
    }

    /** 某地皮当前各类实体计数。 */
    public record Census(int animals, int hostiles, int otherMobs, int vehicles) {
        public static final Census EMPTY = new Census(0, 0, 0, 0);

        /** 生物总数（动物+敌对+其它，不含载具）——mob-cap 的口径。 */
        public int livingTotal() {
            return animals + hostiles + otherMobs;
        }

        public int count(ManorEntityClass c) {
            return switch (c) {
                case ANIMAL -> animals;
                case HOSTILE -> hostiles;
                case OTHER_MOB -> otherMobs;
                case VEHICLE -> vehicles;
            };
        }
    }

    /** 带 3 秒 TTL 缓存的实体计数。高频刷怪时避免每次全量扫描。 */
    public Census countAtCached(World world, Manor manor) {
        String key = manor.guild().value() + ":" + manor.slot();
        long now = System.currentTimeMillis();
        CensusEntry entry = censusCache.get(key);
        if (entry != null && now - entry.timestamp < CENSUS_TTL_MS) {
            return entry.census;
        }
        Census c = countAt(world, manor);
        censusCache.put(key, new CensusEntry(c, now));
        return c;
    }

    /** 实时统计该地皮当前实占范围内、已加载 chunk 中的各类实体（无缓存）。 */
    public Census countAt(World world, Manor manor) {
        GuildWorld gw = registry.get(world.getName());
        if (gw == null) {
            return Census.EMPTY;
        }
        ChunkRegion region = new LayoutCalculator(gw.layout())
                .activeRegion(manor.slot(), manor.level())
                .shift(gw.originChunkX(), gw.originChunkZ());
        int animals = 0, hostiles = 0, other = 0, vehicles = 0;
        for (int cx = region.minChunkX(); cx <= region.maxChunkX(); cx++) {
            for (int cz = region.minChunkZ(); cz <= region.maxChunkZ(); cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue; // 未加载 = 无活动实体，跳过（也避免 getChunkAt 触发加载）
                }
                for (Entity e : world.getChunkAt(cx, cz).getEntities()) {
                    ManorEntityClass c = classify(e);
                    if (c == null) {
                        continue;
                    }
                    switch (c) {
                        case ANIMAL -> animals++;
                        case HOSTILE -> hostiles++;
                        case OTHER_MOB -> other++;
                        case VEHICLE -> vehicles++;
                    }
                }
            }
        }
        return new Census(animals, hostiles, other, vehicles);
    }

    /**
     * 在该地皮再生成/放置一个 {@code cls} 类实体是否会超出其相关 cap（当前数已达上限即超）。
     * 该类未设任何 cap（自身 cap 与 mob-cap 都为 -1）→ 不扫描直接返回 false（零开销，常态）。
     */
    public boolean exceedsCap(World world, Manor manor, ManorEntityClass cls) {
        Map<String, String> f = manor.flags();
        int own = cls.ownCap() == null ? -1 : cls.ownCap().resolveInt(f);
        int mob = cls.isLiving() ? Flag.MOB_CAP.resolveInt(f) : -1;
        if (own < 0 && mob < 0) {
            return false; // 没设相关 cap，免扫描
        }
        Census c = countAtCached(world, manor);
        if (own >= 0 && c.count(cls) >= own) {
            return true;
        }
        return mob >= 0 && c.livingTotal() >= mob;
    }

    /** 实体 → cap 分类；玩家/物品/弹射物/经验球等非生物非载具返回 null（不计）。 */
    public static ManorEntityClass classify(Entity e) {
        if (e instanceof Player) {
            return null;
        }
        if (e instanceof Vehicle) { // 船/矿车
            return ManorEntityClass.VEHICLE;
        }
        if (e instanceof Animals) { // 被动动物（含模组动物，通常实现 Animals）
            return ManorEntityClass.ANIMAL;
        }
        if (e instanceof Enemy) { // 敌对（Monster 及 Slime/Ghast/Phantom 等都实现 Enemy 标记）
            return ManorEntityClass.HOSTILE;
        }
        if (e instanceof LivingEntity) { // 其余非玩家生物（铁傀儡/雪傀儡/蝙蝠/水生等）
            return ManorEntityClass.OTHER_MOB;
        }
        return null;
    }
}
