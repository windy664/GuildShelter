package org.windy.guildshelter.adapter.provider;

import com.gyzer.Data.Guild.Guild;
import com.gyzer.Data.Player.User;
import com.gyzer.LegendaryGuild;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.GuildProvider;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * {@link GuildProvider} 的 LegendaryGuild 实现。GuildId = LegendaryGuild 的公会名（其数据以公会名为主键）。
 *
 * <p>注意：LegendaryGuild 以<b>玩家名</b>而非 UUID 管理成员，本类把 UUID 反解析成在线玩家名再查；
 * 故只能解析<b>在线</b>玩家的公会。保护/权限判断时玩家正在交互（在线），够用。
 */
public final class LegendaryGuildProvider implements GuildProvider {

    @Override
    public Optional<GuildId> guildOf(PlayerRef player) {
        Player p = Bukkit.getPlayer(player.uuid());
        if (p == null) {
            return Optional.empty();
        }
        User user = LegendaryGuild.getLegendaryGuild().getUserManager().getUser(p.getName());
        if (user == null || !user.hasGuild()) {
            return Optional.empty();
        }
        String name = user.getGuild();
        return (name == null || name.isBlank()) ? Optional.empty() : Optional.of(new GuildId(name));
    }

    @Override
    public boolean isMember(PlayerRef player, GuildId guild) {
        Player p = Bukkit.getPlayer(player.uuid());
        if (p == null) {
            return false;
        }
        Guild g = LegendaryGuild.getLegendaryGuild().getGuildsManager().getGuild(guild.value());
        return g != null && g.getMembers().contains(p.getName());
    }

    @Override
    public String displayName(GuildId guild) {
        Guild g = LegendaryGuild.getLegendaryGuild().getGuildsManager().getGuild(guild.value());
        return g != null ? g.getDisplay() : guild.value();
    }

    /** LegendaryGuild 只暴露会长(owner，以玩家名记)；无官员概念 → 仅会长算管理。需玩家在线拿名字。 */
    @Override
    public boolean isGuildAdmin(PlayerRef player, GuildId guild) {
        Player p = Bukkit.getPlayer(player.uuid());
        if (p == null) {
            return false;
        }
        Guild g = LegendaryGuild.getLegendaryGuild().getGuildsManager().getGuild(guild.value());
        return g != null && g.getOwner() != null && g.getOwner().equalsIgnoreCase(p.getName());
    }

    /** LegendaryGuild 公会人数上限 = 基础上限 + 额外名额。 */
    @Override
    public OptionalInt memberCap(GuildId guild) {
        Guild g = LegendaryGuild.getLegendaryGuild().getGuildsManager().getGuild(guild.value());
        return g != null ? OptionalInt.of(g.getMaxMembers() + g.getExtra_members()) : OptionalInt.empty();
    }
}
