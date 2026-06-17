package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.entity.Player;
import org.windy.guildshelter.domain.layout.Classification;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.layout.RegionType;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.ManorRepository;
import org.windy.guildshelter.domain.rule.PermissionRules;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;

/**
 * 平台中立的领地保护判定（Bukkit 与 NeoForge 两侧监听器共用），把 {@link PermissionRules} 接到
 * 实际世界/玩家/坐标上。只依赖 Bukkit API（混合端也有），不碰 NeoForge，故可被两侧引用。
 *
 * <p>会员判定走自包含口径——"在本公会拥有地皮 = 成员"；admin 细粒度权限按区域类型分流
 * （{@code admin.build.other} 在别人地皮、{@code admin.build.road} 在道路），
 * 持有 {@code guildshelter.admin} 总节点 = 全通（向后兼容）。
 */
public final class ClaimGuard {

    private static final long DENY_MSG_COOLDOWN_MS = 3000;

    private final GuildWorldRegistry registry;
    private final ManorRepository manors;
    private final PermissionRules rules;
    private final WorldCache cache;
    private final SupervisorCache supervisorCache;

    private final Map<UUID, Long> lastDenyMsg = new ConcurrentHashMap<>();

    public ClaimGuard(GuildWorldRegistry registry, ManorRepository manors,
                      PermissionRules rules, WorldCache cache, SupervisorCache supervisorCache) {
        this.registry = registry;
        this.manors = manors;
        this.rules = rules;
        this.cache = cache;
        this.supervisorCache = supervisorCache;
    }

    /** 该玩家能否改动其所在世界 (blockX,blockZ) 处的方块。非公会世界一律放行。 */
    public boolean allowed(Player player, int blockX, int blockZ) {
        GuildWorld gw = registry.get(player.getWorld().getName());
        if (gw == null) {
            return true; // 非公会世界，不干预
        }
        int lx = (blockX >> 4) - gw.originChunkX();
        int lz = (blockZ >> 4) - gw.originChunkZ();
        GuildId guild = gw.guild();
        PlayerRef ref = cache.playerRef(player.getUniqueId());
        boolean inGuild = manors.findByOwner(guild, ref).isPresent();
        IntFunction<Manor> bySlot = slot -> manors.findBySlot(guild, slot).orElse(null);
        LayoutCalculator layout = cache.layout(gw.layout()); // 缓存命中 O(1)

        // 合并感知：先用原始 classify，如果是 ROAD 且有合并数据再查缓存
        Classification raw = layout.classify(lx, lz);
        Classification effective = raw;
        if (raw.type() == RegionType.ROAD && cache.merges().hasMerges(guild)) {
            effective = cache.merger(layout, guild).classify(lx, lz); // 缓存命中 O(1)
        }

        if (effective.type() == RegionType.PLOT) {
            // 合并后的路 或 原始地皮：按地皮权限判定（用缓存的 supervisorOnline）
            Manor m = bySlot.apply(effective.slot());
            if (m != null && ManorRoles.effectiveBuildCached(m, ref, supervisorCache)) {
                // 检查是否在实占范围内（合并后的路 chunk 视为在范围内）
                if (raw.type() == RegionType.ROAD || layout.activeRegion(effective.slot(), m.level()).containsChunk(lx, lz)) {
                    return true;
                }
            }
        } else {
            // 非合并的原始判定
            if (rules.canModify(layout, ref, inGuild, bySlot, lx, lz,
                    (m, p) -> ManorRoles.effectiveBuildCached(m, p, supervisorCache))) {
                return true;
            }
        }

        // 规则拒绝 → 检查细粒度 admin 权限（按区域类型分流）
        if (player.isOp()) {
            return true;
        }
        return switch (raw.type()) {
            case ROAD -> Permissions.hasAdminPerm(player, Permissions.ADMIN_BUILD_ROAD);
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
