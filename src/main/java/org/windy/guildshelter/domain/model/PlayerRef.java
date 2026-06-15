package org.windy.guildshelter.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 玩家引用（仅 UUID）。domain 层不认识 Bukkit 的 Player，只用它来标识玩家。
 */
public record PlayerRef(UUID uuid) {

    public PlayerRef {
        Objects.requireNonNull(uuid, "PlayerRef.uuid");
    }

    public static PlayerRef of(UUID uuid) {
        return new PlayerRef(uuid);
    }
}
