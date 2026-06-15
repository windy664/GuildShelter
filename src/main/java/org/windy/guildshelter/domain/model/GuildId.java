package org.windy.guildshelter.domain.model;

import java.util.Objects;

/**
 * 公会标识。公会身份来自外部 provider（如 PlayerGuild），这里只持有其稳定 key。
 */
public record GuildId(String value) {

    public GuildId {
        Objects.requireNonNull(value, "GuildId.value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("GuildId 不能为空");
        }
    }
}
