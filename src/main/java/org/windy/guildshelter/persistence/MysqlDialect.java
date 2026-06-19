package org.windy.guildshelter.persistence;

import java.util.List;

/** MySQL 方言：VARCHAR/BIGINT/INT 列，{@code ON DUPLICATE KEY UPDATE} upsert。 */
public final class MysqlDialect implements SqlDialect {

    @Override
    public List<String> preSchemaStatements() {
        return List.of();
    }

    @Override
    public List<String> schemaStatements() {
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS guild_world (
                    guild_id        VARCHAR(255) PRIMARY KEY,
                    world_name      VARCHAR(255) NOT NULL,
                    seed            BIGINT NOT NULL,
                    origin_x        INT NOT NULL,
                    origin_z        INT NOT NULL,
                    guild_level     INT NOT NULL,
                    allocated_slots INT NOT NULL,
                    layout_params   TEXT
                )""",
                // 老库迁移：列已存在会抛错，由 JdbcDatabase 吞掉。
                "ALTER TABLE guild_world ADD COLUMN layout_params TEXT",
                "ALTER TABLE guild_world ADD COLUMN funds DOUBLE DEFAULT 0",
                "ALTER TABLE guild_world ADD COLUMN bulletin TEXT DEFAULT ''",
                "ALTER TABLE guild_world ADD COLUMN terrain_mode VARCHAR(32) DEFAULT 'CLEAR_VEGETATION'",
                "ALTER TABLE guild_world ADD COLUMN server_name VARCHAR(255) DEFAULT ''",
                """
                CREATE TABLE IF NOT EXISTS manor (
                    guild_id   VARCHAR(255) NOT NULL,
                    slot       INT NOT NULL,
                    owner_uuid VARCHAR(36) NOT NULL,
                    level      INT NOT NULL,
                    flags      TEXT,
                    PRIMARY KEY (guild_id, slot),
                    INDEX idx_manor_owner (guild_id, owner_uuid)
                )""",
                "ALTER TABLE manor ADD COLUMN flags TEXT", // 迁移:列已存在会被吞掉
                "ALTER TABLE manor ADD COLUMN unlocked_chunks TEXT", // 已解锁 chunk 集合(packed int CSV)
                """
                CREATE TABLE IF NOT EXISTS manor_cobuilder (
                    guild_id    VARCHAR(255) NOT NULL,
                    slot        INT NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    PRIMARY KEY (guild_id, slot, player_uuid)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_member (
                    guild_id    VARCHAR(255) NOT NULL,
                    slot        INT NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    PRIMARY KEY (guild_id, slot, player_uuid)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_denied (
                    guild_id    VARCHAR(255) NOT NULL,
                    slot        INT NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    PRIMARY KEY (guild_id, slot, player_uuid)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_rating (
                    guild_id    VARCHAR(255) NOT NULL,
                    slot        INT NOT NULL,
                    rater_uuid  VARCHAR(36) NOT NULL,
                    score       INT NOT NULL,
                    PRIMARY KEY (guild_id, slot, rater_uuid)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_comment (
                    id          INT AUTO_INCREMENT PRIMARY KEY,
                    guild_id    VARCHAR(255) NOT NULL,
                    slot        INT NOT NULL,
                    author_uuid VARCHAR(36) NOT NULL,
                    message     TEXT NOT NULL,
                    created_at  BIGINT NOT NULL
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_merge (
                    guild_id      VARCHAR(255) NOT NULL,
                    primary_slot  INT NOT NULL,
                    absorbed_slot INT NOT NULL,
                    PRIMARY KEY (guild_id, primary_slot, absorbed_slot)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_template (
                    guild_id VARCHAR(255) NOT NULL,
                    name     VARCHAR(255) NOT NULL,
                    flags    TEXT NOT NULL,
                    PRIMARY KEY (guild_id, name)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_sub (
                    guild_id VARCHAR(255) NOT NULL,
                    slot     INT NOT NULL,
                    name     VARCHAR(255) NOT NULL,
                    min_x    INT NOT NULL,
                    min_z    INT NOT NULL,
                    max_x    INT NOT NULL,
                    max_z    INT NOT NULL,
                    flags    TEXT NOT NULL,
                    PRIMARY KEY (guild_id, slot, name)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_visit (
                    guild_id   VARCHAR(255) NOT NULL,
                    slot       INT NOT NULL,
                    visit_count INT NOT NULL DEFAULT 0,
                    PRIMARY KEY (guild_id, slot)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_flower (
                    guild_id    VARCHAR(255) NOT NULL,
                    slot        INT NOT NULL,
                    sender_uuid VARCHAR(36) NOT NULL,
                    sent_date   VARCHAR(10) NOT NULL,
                    PRIMARY KEY (guild_id, slot, sender_uuid, sent_date)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_move_record (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    last_move_at BIGINT NOT NULL
                )""",
                """
                CREATE TABLE IF NOT EXISTS guild_city_trust (
                    guild_id    VARCHAR(255) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    PRIMARY KEY (guild_id, player_uuid)
                )""");
    }

    @Override
    public String upsertGuildWorld() {
        return """
                INSERT INTO guild_world(guild_id, world_name, seed, origin_x, origin_z, guild_level, allocated_slots, layout_params, funds, bulletin, terrain_mode, server_name)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    world_name=VALUES(world_name),
                    seed=VALUES(seed),
                    origin_x=VALUES(origin_x),
                    origin_z=VALUES(origin_z),
                    guild_level=VALUES(guild_level),
                    allocated_slots=VALUES(allocated_slots),
                    layout_params=VALUES(layout_params),
                    funds=VALUES(funds),
                    bulletin=VALUES(bulletin),
                    terrain_mode=VALUES(terrain_mode),
                    server_name=VALUES(server_name)""";
    }

    @Override
    public String upsertManor() {
        return """
                INSERT INTO manor(guild_id, slot, owner_uuid, level, flags, unlocked_chunks) VALUES(?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    owner_uuid=VALUES(owner_uuid),
                    level=VALUES(level),
                    flags=VALUES(flags),
                    unlocked_chunks=VALUES(unlocked_chunks)""";
    }
}
