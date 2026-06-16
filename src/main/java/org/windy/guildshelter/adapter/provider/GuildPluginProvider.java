package org.windy.guildshelter.adapter.provider;

import com.guild.GuildPlugin;
import com.guild.guild.Guild;
import com.guild.guild.GuildManager;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.GuildProvider;

import java.util.Optional;

/**
 * {@link GuildProvider} 的 Guild(com.guild，作者 ya_xzer21145)实现。
 * GuildId = 会名（{@code Guild.getName()}）——该插件以会名为主键（{@code getGuilds()} 按名索引），
 * 没有独立 UUID id，故与 PlayerGuild/LegendaryGuild 同款用会名作 key（改名会丢地皮，限制相同）。
 *
 * <p>查询走公开 API（{@code GuildPlugin.getInstance().getGuildManager()}），且按 UUID 解析，
 * 无需玩家在线。类名用 {@code GuildPlugin} 前缀以避开本插件端口 {@link GuildProvider} 的简单名冲突。
 */
public final class GuildPluginProvider implements GuildProvider {

    @Override
    public Optional<GuildId> guildOf(PlayerRef player) {
        GuildManager gm = manager();
        if (gm == null) {
            return Optional.empty();
        }
        Guild g = gm.getPlayerGuild(player.uuid());
        return (g == null || g.getName() == null || g.getName().isBlank())
                ? Optional.empty()
                : Optional.of(new GuildId(g.getName()));
    }

    @Override
    public boolean isMember(PlayerRef player, GuildId guild) {
        GuildManager gm = manager();
        if (gm == null) {
            return false;
        }
        Guild g = gm.getGuild(guild.value());
        return g != null && g.isMember(player.uuid());
    }

    @Override
    public String displayName(GuildId guild) {
        GuildManager gm = manager();
        if (gm == null) {
            return guild.value();
        }
        Guild g = gm.getGuild(guild.value());
        return g != null ? g.getName() : guild.value();
    }

    private static GuildManager manager() {
        GuildPlugin plugin = GuildPlugin.getInstance();
        return plugin == null ? null : plugin.getGuildManager();
    }
}
