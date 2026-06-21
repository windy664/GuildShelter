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

    /**
     * PlayerGuild 有公会人数上限：从全部公会里按名匹配取 getMemberMaxCount。
     *
     * <p>整个 getAllGuild() 包在 {@link #guard} 里：宿主 SQL/查询异常时返回空，调用方
     * （{@code WorldManager.applyBorderTo}）即用我们自己的等级容量兜底，不至于因宿主故障建不了世界/传不了送。
     */
    @Override
    public OptionalInt memberCap(GuildId guild) {
        java.util.List<GuildInfo> all = guard("memberCap", PlayerGuildApi::getAllGuild, java.util.Collections.<GuildInfo>emptyList());
        for (GuildInfo g : all) {
            if (guild.value().equals(g.getGuildName())) {
                Integer cap = g.getMemberMaxCount();
                return cap != null ? OptionalInt.of(cap) : OptionalInt.empty();
            }
        }
        return OptionalInt.empty();
    }
}
