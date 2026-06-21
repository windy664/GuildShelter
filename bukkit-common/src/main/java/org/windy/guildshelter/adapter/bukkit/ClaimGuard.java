package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.entity.Player;
import org.windy.guildshelter.domain.layout.Classification;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.layout.RegionType;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.rule.PermissionRules;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台中立的领地保护判定（Bukkit 与 NeoForge 两侧监听器共用）。
 *
 * <p>性能优化：所有 DB 查询已下沉到 {@link ManorLookup}（它内部用 {@link ManorRepository#findBySlot}
 * 查一次，结果由调用方缓存）。本类不再独立查库——{@link #allowed} 收到的 {@link Manor} 就是
 * ManorLookup.at() 的结果，直接复用，零额外 SQL。
 *
 * <p>会员判定走内存缓存 {@link GuildMemberCache}（启动时加载，入会/退会同步更新），
 * 避免每次事件都 SELECT。
 */
public final class ClaimGuard {

    private static final long DENY_MSG_COOLDOWN_MS = 3000;

    private final GuildWorldRegistry registry;
    private final PermissionRules rules;
    private final WorldCache cache;
    private final SupervisorCache supervisorCache;
    private final GuildMemberCache memberCache;
    private final CityTrustCache cityTrustCache;
    private final org.windy.guildshelter.domain.port.GuildProvider guildProvider;
    private final RoadPermitCache roadPermitCache;
    /** config road-allow-fake-players：道路是否放行模组假玩家(默认 false=禁,防自动拆路)。 */
    private final boolean roadAllowsFakePlayers;
    /** config main-city-blocked-blocks：主城禁放方块命名空间 id(小写)；空=不限。 */
    private final Set<String> cityBlockedBlocks;

    private final Map<UUID, Long> lastDenyMsg = new ConcurrentHashMap<>();

    public ClaimGuard(GuildWorldRegistry registry, PermissionRules rules,
                      WorldCache cache, SupervisorCache supervisorCache, GuildMemberCache memberCache,
                      CityTrustCache cityTrustCache,
                      org.windy.guildshelter.domain.port.GuildProvider guildProvider,
                      RoadPermitCache roadPermitCache, boolean roadAllowsFakePlayers,
                      Set<String> cityBlockedBlocks) {
        this.registry = registry;
        this.rules = rules;
        this.cache = cache;
        this.supervisorCache = supervisorCache;
        this.memberCache = memberCache;
        this.cityTrustCache = cityTrustCache;
        this.guildProvider = guildProvider != null
                ? guildProvider : org.windy.guildshelter.domain.port.GuildProvider.NONE;
        this.roadPermitCache = roadPermitCache;
        this.roadAllowsFakePlayers = roadAllowsFakePlayers;
        Set<String> bb = new java.util.HashSet<>();
        if (cityBlockedBlocks != null) {
            for (String s : cityBlockedBlocks) {
                if (s != null && !s.isBlank()) {
                    bb.add(s.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        this.cityBlockedBlocks = Set.copyOf(bb);
    }

    /**
     * 主城<b>禁放名单</b>判定：在<b>主城</b> chunk 放置名单内方块 → true(应拦)。用于防止把公共主城改成生产用地。
     * 与建造权限<b>正交</b>——即便有建造权，名单方块照样挡；放行 OP/admin 由调用方(监听器)负责。
     *
     * @param worldName 世界名(=registry key，混合端=维度 path guild_xxx)
     * @param blockId   放置方块命名空间 id(如 {@code minecraft:hopper})，大小写不敏感
     * @return true=该方块在主城禁放；false=不在主城/不在名单/未配置
     */
    public boolean cityPlacementBlocked(String worldName, int blockX, int blockZ, String blockId) {
        if (cityBlockedBlocks.isEmpty() || blockId == null
                || !cityBlockedBlocks.contains(blockId.toLowerCase(java.util.Locale.ROOT))) {
            return false;
        }
        GuildWorld gw = registry.get(worldName);
        if (gw == null) {
            return false;
        }
        int lx = (blockX >> 4) - gw.originChunkX();
        int lz = (blockZ >> 4) - gw.originChunkZ();
        return cache.layout(gw.layout()).classify(lx, lz).isMainCity();
    }

    /**
     * 模组【假玩家】(部署器/机械臂/采石场假人等，非真实在线玩家)能否在 (blockX,blockZ) 破坏/放置。
     * <p>仅约束<b>道路</b>：默认禁(防止玩家用自动化设备拆/占公共道路)；{@code road-allow-fake-players=true} 放开。
     * 非道路(庄园/主城/非营地)不由本规则管——玩家自家自动化照常。合并并入庄园的路视为庄园 → 放行。
     *
     * @param worldName 世界名(混合端 = 维度 path，约定 {@code guild_xxx}，与 registry key 一致)
     * @return true=放行；false=拦截
     */
    public boolean fakePlayerAllowed(String worldName, int blockX, int blockZ) {
        if (roadAllowsFakePlayers) {
            return true;
        }
        GuildWorld gw = registry.get(worldName);
        if (gw == null) {
            return true; // 非公会营地，不干预
        }
        int lx = (blockX >> 4) - gw.originChunkX();
        int lz = (blockZ >> 4) - gw.originChunkZ();
        LayoutCalculator layout = cache.layout(gw.layout());
        Classification c = layout.classify(lx, lz);
        if (c.type() == RegionType.ROAD && cache.merges().hasMerges(gw.guild())) {
            c = cache.merger(layout, gw.guild()).classify(lx, lz); // 合并后的路归庄园
        }
        return c.type() != RegionType.ROAD; // 仅(未合并的)路禁假玩家
    }

    /** 该玩家此刻在该营地是否持有未过期的限时路权（可在路上建造/交互）。 */
    public boolean hasRoadPermit(GuildId guild, UUID player) {
        return roadPermitCache != null && roadPermitCache.hasPermit(guild, player);
    }

    /**
     * 主城可建判定：身份是【会长(含副会长) 或 会长信任的会内成员】<b>且</b>该 chunk 已被主城解锁。
     * 主城解锁集合在 {@link GuildWorld} 上（gw 来自 registry，本就是热路径缓存，无需额外查询）。
     */
    private boolean canBuildCity(GuildWorld gw, PlayerRef ref, int lx, int lz) {
        boolean identity = cityTrustCache.isTrusted(gw.guild(), ref.uuid())
                || guildProvider.isGuildAdmin(ref, gw.guild());
        return identity && gw.isCityUnlocked(lx, lz); // 主城锚在 cell0 原点，内部偏移即 lx/lz
    }

    /**
     * 该玩家能否改动其所在世界 (blockX,blockZ) 处的方块。非公会营地一律放行。
     *
     * <p>性能：manorBySlot 走 WorldCache.manorAt（2 秒 TTL），memberCache 走内存 O(1)。
     * 正常情况下 0 次 DB 查询（缓存命中时）。
     */
    public boolean allowed(Player player, int blockX, int blockZ) {
        GuildWorld gw = registry.get(player.getWorld().getName());
        if (gw == null) {
            return true; // 非公会营地，不干预
        }
        int lx = (blockX >> 4) - gw.originChunkX();
        int lz = (blockZ >> 4) - gw.originChunkZ();
        GuildId guild = gw.guild();
        PlayerRef ref = cache.playerRef(player.getUniqueId());
        boolean inGuild = memberCache.isMember(guild, player.getUniqueId());
        // manorBySlot：走 WorldCache 短 TTL 缓存，避免每次事件查库
        java.util.function.IntFunction<Manor> manorBySlot = slot -> cache.manorAt(gw, slot);
        LayoutCalculator layout = cache.layout(gw.layout()); // 缓存命中 O(1)

        // 合并感知：先用原始 classify，如果是 ROAD 且有合并数据再查缓存
        Classification raw = layout.classify(lx, lz);
        Classification effective = raw;
        if (raw.type() == RegionType.ROAD && cache.merges().hasMerges(guild)) {
            effective = cache.merger(layout, guild).classify(lx, lz); // 缓存命中 O(1)
        }

        if (effective.type() == RegionType.PLOT) {
            // 合并后的路 或 原始庄园：按庄园权限判定（用缓存的 supervisorOnline）
            Manor m = manorBySlot.apply(effective.slot());
            if (m != null && ManorRoles.effectiveBuildCached(m, ref, supervisorCache)) {
                // 检查是否在已解锁范围内（合并后的路 chunk 视为在范围内）
                var plot = layout.plotRegion(effective.slot());
                if (raw.type() == RegionType.ROAD || m.isUnlocked(lx - plot.minChunkX(), lz - plot.minChunkZ())) {
                    return true;
                }
            }
        } else {
            // 非合并的原始判定：主城(会长/会长信任的会内人可建) / 道路(不可建)
            if (rules.canModify(layout, ref, inGuild, manorBySlot, lx, lz,
                    (m, p) -> ManorRoles.effectiveBuildCached(m, p, supervisorCache),
                    p -> canBuildCity(gw, p, lx, lz))) {
                return true;
            }
        }

        // 规则拒绝 → 检查细粒度 admin 权限（按区域类型分流）
        if (player.isOp()) {
            return true;
        }
        return switch (raw.type()) {
            // 路：admin 节点 或 持有未过期【限时路权】者可建。
            case ROAD -> Permissions.hasAdminPerm(player, Permissions.ADMIN_BUILD_ROAD)
                    || hasRoadPermit(guild, player.getUniqueId());
            case PLOT -> Permissions.hasAdminPerm(player, Permissions.ADMIN_BUILD_OTHER);
            case MAIN_CITY -> false;
        };
    }

    /** 清理玩家退出时的缓存。 */
    public void onPlayerQuit(UUID playerId) {
        lastDenyMsg.remove(playerId);
    }

    /** 被拦截时限频提示玩家（3 秒内不重复）。 */
    public void notifyDenied(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastDenyMsg.get(player.getUniqueId());
        if (last != null && now - last < DENY_MSG_COOLDOWN_MS) {
            return;
        }
        lastDenyMsg.put(player.getUniqueId(), now);
        player.sendMessage(Messages.get("listener.build_denied"));
    }
}
