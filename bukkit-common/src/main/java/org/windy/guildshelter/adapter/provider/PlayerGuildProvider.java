package org.windy.guildshelter.adapter.provider;

import cn.handyplus.guild.api.PlayerGuildApi;
import cn.handyplus.guild.constants.GuildRoleEnum;
import cn.handyplus.guild.enter.GuildInfo;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.GuildProvider;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.logging.Logger;

/**
 * {@link GuildProvider} 的 PlayerGuild 实现。GuildId = PlayerGuild 的公会名（其 API 按名查）。
 *
 * <p>新版 PlayerGuildApi 按 {@link java.util.UUID} 静态查询（在线/离线均可解析）。
 *
 * <p><b>对宿主健壮</b>：PlayerGuild 是附属宿主，其内部可能抛异常（如自身 SQL 拼接 bug
 * {@code near "nullidnull": syntax error}）。所有宿主 API 调用都包在 {@link #guard} 里降级，
 * 绝不让宿主插件的异常冒到我们的命令/建世界/边界逻辑里（曾因 memberCap 直抛把 /gs home 整条掀翻）。
 */
public final class PlayerGuildProvider implements GuildProvider {

    private static final Logger LOG = Logger.getLogger("GuildShelter");

    /** 调一次宿主 API，任何异常都吞掉并返回兜底值（节流告警，避免刷屏）。 */
    private static <T> T guard(String what, java.util.function.Supplier<T> call, T fallback) {
        try {
            return call.get();
        } catch (Throwable t) { // 宿主插件内部异常（SQL/反射/NPE 等）一律不外泄
            warnThrottled(what, t);
            return fallback;
        }
    }

    private static long lastWarn;
    private static void warnThrottled(String what, Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastWarn > 30_000L) { // 30s 一次足够定位，不淹没控制台
            lastWarn = now;
            LOG.warning("[GuildShelter] 宿主 PlayerGuild API 调用失败(" + what + ")，已降级: " + t);
        }
    }

    @Override
    public Optional<GuildId> guildOf(PlayerRef player) {
        String name = guard("guildOf", () -> PlayerGuildApi.getPlayerGuildName(player.uuid()), null);
        return (name == null || name.isBlank()) ? Optional.empty() : Optional.of(new GuildId(name));
    }

    @Override
    public boolean isMember(PlayerRef player, GuildId guild) {
        return guildOf(player).map(g -> g.equals(guild)).orElse(false);
    }

    @Override
    public String displayName(GuildId guild) {
        return guild.value();
    }

    /** 会长/副会长算管理：PlayerGuild 角色 roleId 越小权越高（ONE=会长, TWO=副会长），取 ≤2。 */
    @Override
    public boolean isGuildAdmin(PlayerRef player, GuildId guild) {
        String name = guard("isGuildAdmin.name", () -> PlayerGuildApi.getPlayerGuildName(player.uuid()), null);
        if (name == null || !name.equals(guild.value())) {
            return false; // 不在该公会
        }
        GuildRoleEnum role = guard("isGuildAdmin.role", () -> PlayerGuildApi.getPlayerGuildRoleEnum(player.uuid()), null);
        return role != null && role.getRoleId() != null && role.getRoleId() <= 2;
    }

    // —— memberCap 缓存 ——
    // PlayerGuild 的 getAllGuild() 是会刷异常的坏 API（内部 SQL 拼成 nullidnull）。容量极少变（仅宿主公会
    // 升级时变，且我们另有事件跟随），故缓存 name→cap 60s：把对宿主坏 API 的调用从"每次算容量"降到"最多 60s 一次"，
    // 既不丢容量功能，又把宿主自己打的异常日志刷屏压到几乎没有。宿主异常时保留旧缓存并推后重试。
    private static final long CAP_TTL_MS = 60_000L;
    private static volatile long capCacheAt;
    private static volatile java.util.Map<String, Integer> capCache = java.util.Collections.emptyMap();

    /**
     * PlayerGuild 公会人数上限：从缓存的 name→cap 取（缓存过期才打一次宿主 getAllGuild）。
     *
     * <p>宿主 SQL/查询异常时缓存不更新、返回空，调用方（{@code WorldManager.applyBorderTo} 等）
     * 即用我们自己的等级容量兜底，不至于因宿主故障建不了世界/传不了送。
     */
    @Override
    public OptionalInt memberCap(GuildId guild) {
        java.util.Map<String, Integer> caps = capCache;
        if (System.currentTimeMillis() - capCacheAt > CAP_TTL_MS) {
            caps = refreshCaps();
        }
        Integer cap = caps.get(guild.value());
        return cap != null ? OptionalInt.of(cap) : OptionalInt.empty();
    }

    /** 刷新容量缓存（最多每 CAP_TTL_MS 真打一次宿主 API）；宿主异常则保留旧缓存并推后重试。 */
    private static synchronized java.util.Map<String, Integer> refreshCaps() {
        if (System.currentTimeMillis() - capCacheAt <= CAP_TTL_MS) {
            return capCache; // 另一线程刚刷过
        }
        java.util.List<GuildInfo> all = guard("memberCap", PlayerGuildApi::getAllGuild, null);
        if (all == null) {
            capCacheAt = System.currentTimeMillis(); // 宿主异常：推后 TTL 再试，避免每次操作都戳坏 API
            return capCache;                          // 沿用旧缓存（可能为空 → 上层用等级容量兜底）
        }
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        for (GuildInfo g : all) {
            if (g.getGuildName() != null && g.getMemberMaxCount() != null) {
                map.put(g.getGuildName(), g.getMemberMaxCount());
            }
        }
        capCache = map;
        capCacheAt = System.currentTimeMillis();
        return map;
    }
}
