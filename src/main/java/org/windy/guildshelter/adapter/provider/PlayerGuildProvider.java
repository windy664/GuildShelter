package org.windy.guildshelter.adapter.provider;

import com.handy.guild.api.PlayerGuildApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.GuildProvider;

import java.util.Optional;

/**
 * {@link GuildProvider} 的 PlayerGuild 实现。GuildId = PlayerGuild 的公会名（其 API 按名查）。
 *
 * <p>注意：PlayerGuildApi 按 {@link Player} 查询，故只能解析<b>在线</b>玩家的公会；
 * 保护/权限判断时玩家正在交互（在线），够用。
 */
public final class PlayerGuildProvider implements GuildProvider {

    @Override
    public Optional<GuildId> guildOf(PlayerRef player) {
        Player p = Bukkit.getPlayer(player.uuid());
        if (p == null) {
            return Optional.empty();
        }
        String name = PlayerGuildApi.getInstance().getPlayerGuildName(p);
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
}
