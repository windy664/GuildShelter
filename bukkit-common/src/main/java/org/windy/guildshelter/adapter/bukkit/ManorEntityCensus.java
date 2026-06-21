package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.windy.guildshelter.domain.flag.ManorEntityClass;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.rule.OptimizationLimit;
import org.windy.guildshelter.domain.rule.quota.MachineKey;
import org.windy.guildshelter.domain.rule.quota.QuotaRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * 庄园实体计数服务（平台中立判定，只依赖 Bukkit API，混合端也有）。<b>实时</b>扫描某庄园当前实占
 * chunk 范围内的实体并按 {@link ManorEntityClass} 归类——不持久化任何计数器。
 *
 * <p>既供实体上限 caps 做"再生成是否超限"的拦截，也作为<b>可复用 API</b> 供未来"家园卡/评分/
 * 谁的家园更值钱"等按实体数量判断的功能直接取数（{@link GuildShelterPlugin#entityCensus()}）。
 */
public final class ManorEntityCensus {

    private static final long CENSUS_TTL_MS = 3000; // 3 秒缓存

    private final GuildWorldRegistry registry;
    private final QuotaRegistry quotas;
    private final CityLimits cityLimits;
    /** "guildId:slot" / "guildId:city" → (Census, timestamp) 缓存。 */
    private final java.util.Map<String, CensusEntry> censusCache = new java.util.concurrent.ConcurrentHashMap<>();

    private record CensusEntry(Census census, long timestamp) {}

    public ManorEntityCensus(GuildWorldRegistry registry, QuotaRegistry quotas, CityLimits cityLimits) {
        this.registry = registry;
        this.quotas = quotas;
        this.cityLimits = cityLimits == null ? CityLimits.disabled() : cityLimits;
    }

    /** 统一配额解析器（等级基础 + 管理员增量 + 玩家自调 cap）。 */
    public QuotaRegistry quotas() {
        return quotas;
    }

    /** 放置被某配额拦下的结果：消息键 + 格式参数（由调用方 Messages.get 解析）。 */
    public record Denial(String messageKey, Object[] args) {}

    /** 某庄园当前各类实体/方块实体计数。{@code machineCounts} 仅含被配额的机器 id（小写）→ 个数。 */
    public record Census(int animals, int hostiles, int otherMobs, int vehicles,
                         int tileEntities, int droppedItems, Map<String, Integer> machineCounts) {
        public static final Census EMPTY = new Census(0, 0, 0, 0, 0, 0, Map.of());

        /** 某机器 id 当前个数（未配额返回 0）。 */
        public int machineCount(String blockId) {
            return machineCounts.getOrDefault(blockId == null ? "" : blockId.toLowerCase(java.util.Locale.ROOT), 0);
        }

        /** 生物总数（动物+敌对+其它，不含载具）——mob-cap 的口径。 */
        public int livingTotal() {
            return animals + hostiles + otherMobs;
        }

        /** 所有实体总数（生物+载具+掉落物）。 */
        public int entityTotal() {
            return livingTotal() + vehicles + droppedItems;
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

    /** 实时统计该庄园当前实占范围内、已加载 chunk 中的各类实体/方块实体（无缓存）。 */
    public Census countAt(World world, Manor manor) {
        GuildWorld gw = registry.get(world.getName());
        if (gw == null) {
            return Census.EMPTY;
        }
        ChunkRegion region = new LayoutCalculator(gw.layout())
                .activeRegion(manor.slot(), manor.level())
                .shift(gw.originChunkX(), gw.originChunkZ());
        return countRegion(world, region, quotas == null ? java.util.Set.of() : quotas.machineIds());
    }

    /** 统计任意 chunk 范围内已加载 chunk 的各类实体/方块实体（庄园与主城共用）。{@code machineIds}=要按 id 计数的机器集合。 */
    private Census countRegion(World world, ChunkRegion region, java.util.Set<String> machineIds) {
        int animals = 0, hostiles = 0, other = 0, vehicles = 0, tileEntities = 0, droppedItems = 0;
        Map<String, Integer> machineCounts = machineIds.isEmpty() ? Map.of() : new HashMap<>();
        for (int cx = region.minChunkX(); cx <= region.maxChunkX(); cx++) {
            for (int cz = region.minChunkZ(); cz <= region.maxChunkZ(); cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                // 实体计数（生物+载具+掉落物）
                for (Entity e : world.getChunkAt(cx, cz).getEntities()) {
                    if (e instanceof Item) {
                        droppedItems++;
                        continue;
                    }
                    ManorEntityClass c = classify(e);
                    if (c == null) continue;
                    switch (c) {
                        case ANIMAL -> animals++;
                        case HOSTILE -> hostiles++;
                        case OTHER_MOB -> other++;
                        case VEHICLE -> vehicles++;
                    }
                }
                // 方块实体计数（箱子/熔炉/漏斗/告示牌等）+ 被配额机器按 id 计数
                for (BlockState state : world.getChunkAt(cx, cz).getTileEntities()) {
                    tileEntities++;
                    if (!machineIds.isEmpty()) {
                        String id = state.getType().getKey().toString(); // 命名空间 id（混合端含模组机器）
                        if (machineIds.contains(id)) {
                            machineCounts.merge(id, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        return new Census(animals, hostiles, other, vehicles, tileEntities, droppedItems, machineCounts);
    }

    /**
     * 在该庄园再生成/放置一个 {@code cls} 类实体是否会超出其<b>有效上限</b>（当前数已达上限即超）。
     * 有效上限经 {@link QuotaRegistry} 合成（等级基础 + 管理员增量，再与玩家自调 cap 取紧）。
     * 自身 cap 与 mob-cap 都不限（-1）→ 不扫描直接返回 false（零开销，常态）。
     */
    public boolean exceedsCap(World world, Manor manor, ManorEntityClass cls) {
        OptimizationLimit ownKind = kindOf(cls);
        int own = ownKind == null ? -1 : quotas.effectiveCap(manor, ownKind);
        int mob = cls.isLiving() ? quotas.effectiveCap(manor, OptimizationLimit.MOB) : -1;
        if (own < 0 && mob < 0) {
            return false; // 没设相关 cap，免扫描
        }
        Census c = countAtCached(world, manor);
        if (own >= 0 && c.count(cls) >= own) {
            return true;
        }
        return mob >= 0 && c.livingTotal() >= mob;
    }

    /** 该庄园掉落物有效上限（等级基础 + 管理员增量）；-1 = 不限。供 {@code ManorLimitTask} 用。 */
    public int dropCap(Manor manor) {
        return quotas.effectiveCap(manor, OptimizationLimit.DROPS);
    }

    /**
     * 放置一个带方块实体的方块时，按配额判断是否拦下；返回拦截结果（含提示消息）或 {@code null} 放行。
     * <b>统一的放置决策入口</b>：先查【具体机器配额】（更精细的提示），再查【方块实体总数】。
     * 两载体（Bukkit {@code ManorCapListener} / NeoForge {@code NeoForgeProtection}）共用，决策只此一处。
     */
    public Denial placementDenial(World world, Manor manor, String blockId) {
        // 机器配额
        if (blockId != null && !blockId.isEmpty() && quotas.hasMachine(blockId)) {
            int cap = quotas.effectiveCap(manor, new MachineKey(blockId));
            if (cap >= 0 && countAtCached(world, manor).machineCount(blockId) >= cap) {
                return new Denial("error.machine_cap_reached", new Object[]{quotas.display(blockId)});
            }
        }
        // 方块实体总数
        int tileCap = quotas.effectiveCap(manor, OptimizationLimit.TILES);
        if (tileCap >= 0 && countAtCached(world, manor).tileEntities() >= tileCap) {
            return new Denial("error.tile_cap_reached", new Object[0]);
        }
        return null;
    }

    // ===== 主城限额（固定 config 预算 {@link CityLimits}，范围 = mainCityRegion 整格）=====

    /** 主城限额是否启用。 */
    public boolean cityLimitsEnabled() {
        return cityLimits.enabled();
    }

    /** 主城掉落物上限；-1 = 不限 / 未启用。供 {@code ManorLimitTask} 清理用。 */
    public int cityDropCap() {
        return cityLimits.enabled() ? cityLimits.maxDrops() : -1;
    }

    /** 带 3 秒 TTL 缓存的主城实体计数（范围 = 整个主城 footprint）。 */
    public Census countCityCached(World world) {
        GuildWorld gw = registry.get(world.getName());
        if (gw == null) {
            return Census.EMPTY;
        }
        String key = gw.guild().value() + ":city";
        long now = System.currentTimeMillis();
        CensusEntry entry = censusCache.get(key);
        if (entry != null && now - entry.timestamp < CENSUS_TTL_MS) {
            return entry.census;
        }
        ChunkRegion region = new LayoutCalculator(gw.layout())
                .mainCityRegion().shift(gw.originChunkX(), gw.originChunkZ());
        Census c = countRegion(world, region, java.util.Set.of()); // 主城不按机器 id 计数（禁放名单已管机器）
        censusCache.put(key, new CensusEntry(c, now));
        return c;
    }

    /** 在主城再生成一个 {@code cls} 类实体是否超主城固定上限。未启用/无相关上限 → false（零开销）。 */
    public boolean cityExceedsCap(World world, ManorEntityClass cls) {
        if (!cityLimits.enabled()) {
            return false;
        }
        int own = cityLimits.capOf(cls);
        int mob = cls.isLiving() ? cityLimits.maxMobs() : -1;
        if (own < 0 && mob < 0) {
            return false;
        }
        Census c = countCityCached(world);
        if (own >= 0 && c.count(cls) >= own) {
            return true;
        }
        return mob >= 0 && c.livingTotal() >= mob;
    }

    /** 在主城放置带方块实体的方块是否超【主城方块实体总数】上限；超则返回拦截结果，否则 null。 */
    public Denial cityPlacementDenial(World world) {
        if (!cityLimits.enabled() || cityLimits.maxTiles() < 0) {
            return null;
        }
        if (countCityCached(world).tileEntities() >= cityLimits.maxTiles()) {
            return new Denial("error.city_tile_cap_reached", new Object[0]);
        }
        return null;
    }

    /** {@link ManorEntityClass} → 自身对应的 {@link OptimizationLimit}；OTHER_MOB 无自身限额返回 null。 */
    private static OptimizationLimit kindOf(ManorEntityClass cls) {
        return switch (cls) {
            case ANIMAL -> OptimizationLimit.ANIMAL;
            case HOSTILE -> OptimizationLimit.HOSTILE;
            case VEHICLE -> OptimizationLimit.VEHICLE;
            case OTHER_MOB -> null;
        };
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
