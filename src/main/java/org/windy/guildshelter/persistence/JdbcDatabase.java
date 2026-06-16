package org.windy.guildshelter.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * 通用 JDBC 数据源 + 建表（SQLite / MySQL 共用）。方言差异（建表类型、upsert、WAL）由 {@link SqlDialect} 给出。
 * 迁移类语句（如 ADD COLUMN）在"列已存在"时被吞掉，保证可重复初始化。
 */
public final class JdbcDatabase implements AutoCloseable {

    private final HikariDataSource dataSource;

    public JdbcDatabase(String jdbcUrl, String driverClassName, String user, String password,
                        String connectionInitSql, SqlDialect dialect) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        if (driverClassName != null && !driverClassName.isBlank()) {
            cfg.setDriverClassName(driverClassName);
        }
        if (user != null) {
            cfg.setUsername(user);
        }
        if (password != null) {
            cfg.setPassword(password);
        }
        if (connectionInitSql != null && !connectionInitSql.isBlank()) {
            cfg.setConnectionInitSql(connectionInitSql);
        }
        cfg.setMaximumPoolSize(8);
        cfg.setPoolName("GuildShelter-DB");
        this.dataSource = new HikariDataSource(cfg);
        init(dialect);
    }

    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new PersistenceException("获取数据库连接失败", e);
        }
    }

    private void init(SqlDialect dialect) {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            for (String s : dialect.preSchemaStatements()) {
                st.execute(s);
            }
            for (String s : dialect.schemaStatements()) {
                try {
                    st.executeUpdate(s);
                } catch (SQLException e) {
                    if (!isAlreadyExists(e)) {
                        throw e; // 只容忍"已存在"(迁移幂等)，其它建表错误照常抛出
                    }
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("初始化数据库表失败", e);
        }
    }

    /** 迁移语句在列/对象已存在时的常见报错（SQLite/MySQL 都含 "duplicate column"）。 */
    private static boolean isAlreadyExists(SQLException e) {
        String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return m.contains("duplicate column") || m.contains("already exists");
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
