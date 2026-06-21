package org.windy.guildshelter.persistence;

import java.util.List;

/** SQLite 方言：TEXT/INTEGER 列，{@code ON CONFLICT ... DO UPDATE} upsert，WAL。 */
public final class SqliteDialect implements SqlDialect {

    @Override
    public List<String> preSchemaStatements() {
        // WAL 下 synchronous=NORMAL 是安全的(崩溃最多丢最后一个未 checkpoint 的事务，不损坏库)，
        // 比默认 FULL 少一次 fsync，写入明显更快；busy_timeout 让短暂写锁竞争自动重试而非立刻抛错。
        return List.of(
                "PRAGMA journal_mode=WAL",
                "PRAGMA synchronous=NORMAL",
                "PRAGMA busy_timeout=5000",
                "PRAGMA foreign_keys=ON");
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
                "ALTER TABLE guild_world ADD COLUMN funds REAL DEFAULT 0",
                "ALTER TABLE guild_world ADD COLUMN bulletin TEXT DEFAULT ''",
                "ALTER TABLE guild_world ADD COLUMN terrain_mode TEXT DEFAULT 'CLEAR_VEGETATION'",
                "ALTER TABLE guild_world ADD COLUMN server_name TEXT DEFAULT ''",
                "ALTER TABLE guild_world ADD COLUMN city_unlocked TEXT", // 主城已解锁 chunk 集合(packed int CSV)
                "ALTER TABLE guild_world ADD COLUMN city_quota INTEGER DEFAULT -1", // 主城额度覆盖(-1=按等级)
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
                "ALTER TABLE manor ADD COLUMN unlocked_chunks TEXT", // 已解锁 chunk 集合(packed int CSV)
                "CREATE INDEX IF NOT EXISTS idx_manor_owner ON manor(guild_id, owner_uuid)",
                """
                CREATE TABLE IF NOT EXISTS manor_cobuilder (
                    guild_id    TEXT NOT NULL,
                    slot        INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    PRIMARY KEY (guild_id, slot, player_uuid)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_member (
                    guild_id    TEXT NOT NULL,
                    slot        INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    PRIMARY KEY (guild_id, slot, player_uuid)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_denied (
                    guild_id    TEXT NOT NULL,
                    slot        INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    PRIMARY KEY (guild_id, slot, player_uuid)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_rating (
                    guild_id    TEXT NOT NULL,
                    slot        INTEGER NOT NULL,
                    rater_uuid  TEXT NOT NULL,
                    score       INTEGER NOT NULL,
                    PRIMARY KEY (guild_id, slot, rater_uuid)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_comment (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    guild_id    TEXT NOT NULL,
                    slot        INTEGER NOT NULL,
                    author_uuid TEXT NOT NULL,
                    message     TEXT NOT NULL,
                    created_at  INTEGER NOT NULL
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_merge (
                    guild_id      TEXT NOT NULL,
                    primary_slot  INTEGER NOT NULL,
                    absorbed_slot INTEGER NOT NULL,
                    PRIMARY KEY (guild_id, primary_slot, absorbed_slot)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_template (
                    guild_id TEXT NOT NULL,
                    name     TEXT NOT NULL,
                    flags    TEXT NOT NULL,
                    PRIMARY KEY (guild_id, name)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_sub (
                    guild_id TEXT NOT NULL,
                    slot     INTEGER NOT NULL,
                    name     TEXT NOT NULL,
                    min_x    INTEGER NOT NULL,
                    min_z    INTEGER NOT NULL,
                    max_x    INTEGER NOT NULL,
                    max_z    INTEGER NOT NULL,
                    flags    TEXT NOT NULL,
                    PRIMARY KEY (guild_id, slot, name)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_visit (
                    guild_id   TEXT NOT NULL,
                    slot       INTEGER NOT NULL,
                    visit_count INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (guild_id, slot)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_flower (
                    guild_id    TEXT NOT NULL,
                    slot        INTEGER NOT NULL,
                    sender_uuid TEXT NOT NULL,
                    sent_date   TEXT NOT NULL,
                    PRIMARY KEY (guild_id, slot, sender_uuid, sent_date)
                )""",
                """
                CREATE TABLE IF NOT EXISTS manor_move_record (
                    player_uuid TEXT PRIMARY KEY,
                    last_move_at INTEGER NOT NULL
                )""",
                """
                CREATE TABLE IF NOT EXISTS guild_city_trust (
                    guild_id    TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    PRIMARY KEY (guild_id, player_uuid)
                )""",
                """
                CREATE TABLE IF NOT EXISTS road_permit (
                    guild_id    TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    expire_at   INTEGER NOT NULL,
                    PRIMARY KEY (guild_id, player_uuid)
                )""",
                """
                CREATE TABLE IF NOT EXISTS guild_spawn (
                    guild_id   TEXT NOT NULL,
                    spawn_type TEXT NOT NULL,
                    x          REAL NOT NULL,
                    y          REAL NOT NULL,
                    z          REAL NOT NULL,
                    yaw        REAL NOT NULL,
                    pitch      REAL NOT NULL,
                    PRIMARY KEY (guild_id, spawn_type)
                )""",
                """
                CREATE TABLE IF NOT EXISTS guild_city_flag (
                    guild_id TEXT NOT NULL,
                    flag_id  TEXT NOT NULL,
                    value    TEXT NOT NULL,
                    PRIMARY KEY (guild_id, flag_id)
                )""",
                """
                CREATE TABLE IF NOT EXISTS guild_hologram (
                    id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    guild_id TEXT NOT NULL,
                    name     TEXT NOT NULL UNIQUE,
                    label    TEXT NOT NULL
                )""",
                "CREATE INDEX IF NOT EXISTS idx_hologram_guild ON guild_hologram(guild_id)");
    }

    @Override
    public String upsertGuildWorld() {
        return """
                INSERT INTO guild_world(guild_id, world_name, seed, origin_x, origin_z, guild_level, allocated_slots, layout_params, funds, bulletin, terrain_mode, server_name, city_unlocked, city_quota)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(guild_id) DO UPDATE SET
                    world_name=excluded.world_name,
                    seed=excluded.seed,
                    origin_x=excluded.origin_x,
                    origin_z=excluded.origin_z,
                    guild_level=excluded.guild_level,
                    allocated_slots=excluded.allocated_slots,
                    layout_params=excluded.layout_params,
                    funds=excluded.funds,
                    bulletin=excluded.bulletin,
                    terrain_mode=excluded.terrain_mode,
                    server_name=excluded.server_name,
                    city_unlocked=excluded.city_unlocked,
                    city_quota=excluded.city_quota""";
    }

    @Override
    public String upsertManor() {
        return """
                INSERT INTO manor(guild_id, slot, owner_uuid, level, flags, unlocked_chunks) VALUES(?,?,?,?,?,?)
                ON CONFLICT(guild_id, slot) DO UPDATE SET
                    owner_uuid=excluded.owner_uuid,
                    level=excluded.level,
                    flags=excluded.flags,
                    unlocked_chunks=excluded.unlocked_chunks""";
    }
}
