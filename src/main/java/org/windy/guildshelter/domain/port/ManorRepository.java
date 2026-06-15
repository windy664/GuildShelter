package org.windy.guildshelter.domain.port;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;

import java.util.List;
import java.util.Optional;

/** 庄园归属/等级的持久化（Phase 2 由 SQLite 实现）。 */
public interface ManorRepository {

    Optional<Manor> findBySlot(GuildId guild, int slot);

    Optional<Manor> findByOwner(GuildId guild, PlayerRef owner);

    /** 跨公会按 owner 查庄园（退出/解散时用，不需要事先知道公会）。 */
    Optional<Manor> findByOwnerAnywhere(PlayerRef owner);

    List<Manor> findAll(GuildId guild);

    void save(Manor manor);

    void delete(GuildId guild, int slot);

    /** 该公会下一个空闲 slot（优先复用最小空缺，保持螺旋紧凑排满）。 */
    int nextFreeSlot(GuildId guild);
}
