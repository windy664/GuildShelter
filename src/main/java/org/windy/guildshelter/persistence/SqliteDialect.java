package org.windy.guildshelter.persistence;

import java.util.List;

/** SQLite 方言：TEXT/INTEGER 列，{@code ON CONFLICT ... DO UPDATE} upsert，WAL。 */
public final class SqliteDialect implements SqlDialect {

    @Override
    public List<String> preSchemaStatements() {
        return List.of("PRAGMA journal_mode=WAL", "PRAGMA foreign_keys=ON");
    }

    @Override
    public List<String> schemaStatements() {
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS guild_world (
                    guild_id        TEXT PRIMARY KEY,
                    world_name      TEXT NOT NULL,
                    seed            INTEGER NOT NULL,
                    origin_x        INTEGER NOT NULL,
                    origin_z        INTEGER NOT NULL,
                    guild_level     INTEGER NOT NULL,
                    allocated_slots INTEGER NOT NULL,
                    layout_params   TEXT
                )""",
                // 老库迁移：列已存在会抛错，由 JdbcDatabase 吞掉。
                "ALTER TABLE guild_world ADD COLUMN layout_params TEXT",
                """
                CREATE TABLE IF NOT EXISTS manor (
                    guild_id   TEXT NOT NULL,
                    slot       INTEGER NOT NULL,
                    owner_uuid TEXT NOT NULL,
                    level      INTEGER NOT NULL,
                    flags      TEXT,
                    PRIMARY KEY (guild_id, slot)
                )""",
                "ALTER TABLE manor ADD COLUMN flags TEXT", // 迁移:列已存在会被吞掉
                "CREATE INDEX IF NOT EXISTS idx_manor_owner ON manor(guild_id, owner_uuid)",
                """
                CREATE TABLE IF NOT EXISTS manor_cobuilder (
                    guild_id    TEXT NOT NULL,
                    slot        INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    PRIMARY KEY (guild_id, slot, player_uuid)
                )""");
    }

    @Override
    public String upsertGuildWorld() {
        return """
                INSERT INTO guild_world(guild_id, world_name, seed, origin_x, origin_z, guild_level, allocated_slots, layout_params)
                VALUES(?,?,?,?,?,?,?,?)
                ON CONFLICT(guild_id) DO UPDATE SET
                    world_name=excluded.world_name,
                    seed=excluded.seed,
                    origin_x=excluded.origin_x,
                    origin_z=excluded.origin_z,
                    guild_level=excluded.guild_level,
                    allocated_slots=excluded.allocated_slots,
                    layout_params=excluded.layout_params""";
    }

    @Override
    public String upsertManor() {
        return """
                INSERT INTO manor(guild_id, slot, owner_uuid, level, flags) VALUES(?,?,?,?,?)
                ON CONFLICT(guild_id, slot) DO UPDATE SET
                    owner_uuid=excluded.owner_uuid,
                    level=excluded.level,
                    flags=excluded.flags""";
    }
}
