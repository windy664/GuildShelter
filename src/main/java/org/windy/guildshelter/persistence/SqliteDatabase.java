package org.windy.guildshelter.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 数据源 + 建表。单文件嵌入式库：小连接池 + WAL + busy_timeout，避免写锁竞争。
 * 区域全用整数列存储，无 GIS（取代旧代码那套坏掉的 MySQL ST_GeomFromText）。
 */
public final class SqliteDatabase implements AutoCloseable {

    private final HikariDataSource dataSource;

    public SqliteDatabase(String jdbcUrl) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setMaximumPoolSize(4);
        cfg.setPoolName("GuildShelter-SQLite");
        cfg.setConnectionInitSql("PRAGMA busy_timeout=5000");
        this.dataSource = new HikariDataSource(cfg);
        init();
    }

    /** 指向数据文件夹下的 database.db。 */
    public static SqliteDatabase forFile(Path dbFile) {
        return new SqliteDatabase("jdbc:sqlite:" + dbFile.toString().replace('\\', '/'));
    }

    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new PersistenceException("获取数据库连接失败", e);
        }
    }

    private void init() {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA foreign_keys=ON");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS guild_world (
                        guild_id        TEXT PRIMARY KEY,
                        world_name      TEXT NOT NULL,
                        seed            INTEGER NOT NULL,
                        origin_x        INTEGER NOT NULL,
                        origin_z        INTEGER NOT NULL,
                        guild_level     INTEGER NOT NULL,
                        allocated_slots INTEGER NOT NULL
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS manor (
                        guild_id   TEXT NOT NULL,
                        slot       INTEGER NOT NULL,
                        owner_uuid TEXT NOT NULL,
                        level      INTEGER NOT NULL,
                        PRIMARY KEY (guild_id, slot)
                    )""");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_manor_owner ON manor(guild_id, owner_uuid)");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS manor_cobuilder (
                        guild_id    TEXT NOT NULL,
                        slot        INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL,
                        PRIMARY KEY (guild_id, slot, player_uuid)
                    )""");
        } catch (SQLException e) {
            throw new PersistenceException("初始化数据库表失败", e);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
