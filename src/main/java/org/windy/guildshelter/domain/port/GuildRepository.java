package org.windy.guildshelter.domain.port;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;

import java.util.List;
import java.util.Optional;

/** 公会世界状态的持久化（Phase 2 由 SQLite 实现）。 */
public interface GuildRepository {

    Optional<GuildWorld> find(GuildId guild);

    boolean exists(GuildId guild);

    void save(GuildWorld world);

    void delete(GuildId guild);

    /** 所有公会世界（启动时载入内存注册表用）。 */
    List<GuildWorld> findAll();
}
