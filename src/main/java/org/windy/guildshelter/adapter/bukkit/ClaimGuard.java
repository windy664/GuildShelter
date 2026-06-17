package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.entity.Player;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
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
 * <p>会员判定走自包含口径——"在本公会拥有地皮 = 成员"；OP 或持 {@code guildshelter.admin} 放行。
 */
public final class ClaimGuard {

    private static final String ADMIN_BYPASS = "guildshelter.admin";
    private static final long DENY_MSG_COOLDOWN_MS = 3000;

    private final GuildWorldRegistry registry;
    private final ManorRepository manors;
    private final PermissionRules rules;

    private final Map<UUID, Long> lastDenyMsg = new ConcurrentHashMap<>();

    public ClaimGuard(GuildWorldRegistry registry, ManorRepository manors, PermissionRules rules) {
        this.registry = registry;
        this.manors = manors;
        this.rules = rules;
    }

    /** 该玩家能否改动其所在世界 (blockX,blockZ) 处的方块。非公会世界一律放行。 */
    public boolean allowed(Player player, int blockX, int blockZ) {
        if (player.hasPermission(ADMIN_BYPASS) || player.isOp()) {
            return true;
        }
        GuildWorld gw = registry.get(player.getWorld().getName());
        if (gw == null) {
            return true; // 非公会世界，不干预
        }
        int lx = (blockX >> 4) - gw.originChunkX();
        int lz = (blockZ >> 4) - gw.originChunkZ();
        GuildId guild = gw.guild();
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        boolean inGuild = manors.findByOwner(guild, ref).isPresent();
        IntFunction<Manor> bySlot = slot -> manors.findBySlot(guild, slot).orElse(null);
        LayoutCalculator layout = new LayoutCalculator(gw.layout()); // 用该世界冻结的布局
        // 地皮内建造判定走身份分级 + member 在线门控（ManorRoles），非纯 hasBuildAccess。
        return rules.canModify(layout, ref, inGuild, bySlot, lx, lz, ManorRoles::effectiveBuild);
    }

    /** 被拦截时限频提示玩家（3 秒内不重复）。 */
    public void notifyDenied(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastDenyMsg.get(player.getUniqueId());
        if (last != null && now - last < DENY_MSG_COOLDOWN_MS) {
            return;
        }
        lastDenyMsg.put(player.getUniqueId(), now);
        player.sendMessage("§c这里不是你能改动的区域。");
    }
}
