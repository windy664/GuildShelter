package org.windy.guildshelter.adapter.provider;

import cn.handyplus.guild.api.PlayerGuildApi;
import cn.handyplus.guild.constants.GuildRoleEnum;
import cn.handyplus.guild.enter.GuildInfo;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.GuildProvider;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * {@link GuildProvider} 的 PlayerGuild 实现。GuildId = PlayerGuild 的公会名（其 API 按名查）。
 *
 * <p>新版 PlayerGuildApi 按 {@link java.util.UUID} 静态查询（在线/离线均可解析）。
 */
public final class PlayerGuildProvider implements GuildProvider {

    @Override
    public Optional<GuildId> guildOf(PlayerRef player) {
        String name = PlayerGuildApi.getPlayerGuildName(player.uuid());
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
        String name = PlayerGuildApi.getPlayerGuildName(player.uuid());
        if (name == null || !name.equals(guild.value())) {
            return false; // 不在该公会
        }
        GuildRoleEnum role = PlayerGuildApi.getPlayerGuildRoleEnum(player.uuid());
        return role != null && role.getRoleId() != null && role.getRoleId() <= 2;
    }

    /** PlayerGuild 有公会人数上限：从全部公会里按名匹配取 getMemberMaxCount。 */
    @Override
    public OptionalInt memberCap(GuildId guild) {
        for (GuildInfo g : PlayerGuildApi.getAllGuild()) {
            if (guild.value().equals(g.getGuildName())) {
                Integer cap = g.getMemberMaxCount();
                return cap != null ? OptionalInt.of(cap) : OptionalInt.empty();
            }
        }
        return OptionalInt.empty();
    }
}
