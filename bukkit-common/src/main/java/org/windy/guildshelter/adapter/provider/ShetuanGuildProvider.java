package org.windy.guildshelter.adapter.provider;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.GuildProvider;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link GuildProvider} 的 Shetuan 实现。GuildId = Shetuan 社团的<b>稳定 UUID</b>（{@code Club.id()}），
 * 而非会名——社团可改名，用 UUID 作 key 改名也不丢庄园/世界。
 *
 * <p>Shetuan 的查询 API 全按 UUID 走，故无需玩家在线即可解析（优于 PlayerGuild / LegendaryGuild）。
 */
public final class ShetuanGuildProvider implements GuildProvider {

    private final ShetuanAccess access;

    public ShetuanGuildProvider(ShetuanAccess access) {
        this.access = access;
    }

    @Override
    public Optional<GuildId> guildOf(PlayerRef player) {
        return access.clubIdOf(player.uuid()).map(id -> new GuildId(id.toString()));
    }

    @Override
    public boolean isMember(PlayerRef player, GuildId guild) {
        return access.clubIdOf(player.uuid())
                .map(id -> id.toString().equals(guild.value()))
                .orElse(false);
    }

    @Override
    public String displayName(GuildId guild) {
        return parseUuid(guild.value())
                .flatMap(access::clubById)
                .map(access::displayName)
                .orElse(guild.value());
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
