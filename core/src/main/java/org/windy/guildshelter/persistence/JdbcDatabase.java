package org.windy.guildshelter.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;

/**
 * 通用 JDBC 数据源 + <b>版本化 schema 迁移</b>（SQLite / MySQL 共用）。方言差异（类型、upsert、迁移）由
 * {@link SqlDialect} 给出。生产安全：用 {@code gs_schema_version} 表记录库版本，启动时比对代码目标版本，
 * 只把缺的迁移按序各跑一次（每步一事务），<b>不再每次启动重跑全部 DDL 靠报错字符串吞</b>。
 *
 * <p>流程：preSchema(每次跑) → 确保版本表 → 读库版本 V → V&lt;1 跑基线(幂等全量 schema)记 V=1 →
 * 按序跑 V&lt;ver≤target 的迁移。V&gt;target（疑似插件被降级）则拒绝改表、告警。
 */
public final class JdbcDatabase implements AutoCloseable {

    /** 基线 = 当前全量 schema 记为版本 1；之后的改动走 {@link SqlDialect#migrations()} 编号 2,3…。 */
    private static final int BASELINE_VERSION = 1;
    /** 迁移历史/审计表：每应用一个版本(含基线)追加一行(version, applied_at)；当前版本 = max(version)。 */
    private static final String HISTORY_TABLE = "gs_schema_history";

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
        // SQLite 是单写者数据库，连接多反而增加锁竞争；MySQL 可以多连接。
        boolean isSqlite = jdbcUrl != null && jdbcUrl.startsWith("jdbc:sqlite:");
        cfg.setMaximumPoolSize(isSqlite ? 2 : 8);
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
        try (Connection c = dataSource.getConnection()) {
            // 1) 会话/库设置：每次启动都跑（PRAGMA 等幂等，与版本无关）。
            try (Statement st = c.createStatement()) {
                for (String s : dialect.preSchemaStatements()) {
                    st.execute(s);
                }
            }
            // 2) 历史/审计表 + 读当前库版本(= 已应用的最高版本)。
            ensureHistoryTable(c);
            int current = currentVersion(c);

            // 3) 基线(v1)：全新库 / 上线前的旧库(无历史→version 0)首次跑一次幂等全量 schema 拉齐，留一条历史。
            if (current < BASELINE_VERSION) {
                runBaselineSchema(c, dialect);
                recordVersion(c, BASELINE_VERSION);
                current = BASELINE_VERSION;
            }

            // 4) v2+ 有序迁移：只跑库版本之后、≤target 的，每条一事务，跑完追加历史。
            //    跨多版本老库会沿 current+1, current+2, … 逐级爬(每条都保留在代码里，故任意老版都能升上来)。
            SortedMap<Integer, Migration> migrations = dialect.migrations();
            int target = migrations.isEmpty() ? BASELINE_VERSION
                    : Math.max(BASELINE_VERSION, migrations.lastKey());
            if (current > target) {
                // 库比插件期望新（多半是插件被降级）：绝不乱改表结构，告警后照常用现有结构启动。
                System.err.println("[GuildShelter] 数据库 schema 版本(" + current + ") 高于本插件期望("
                        + target + ")，疑似插件被降级；跳过迁移，不修改表结构。");
                return;
            }
            for (Map.Entry<Integer, Migration> e : migrations.entrySet()) {
                int ver = e.getKey();
                if (ver <= current) {
                    continue; // 已应用过
                }
                applyMigration(c, ver, e.getValue());
                current = ver;
            }
        } catch (SQLException e) {
            throw new PersistenceException("初始化/迁移数据库失败", e);
        }
    }

    /** 确保迁移历史表存在（两方言通用 DDL）。 */
    private static void ensureHistoryTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + HISTORY_TABLE
                    + " (version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL)");
        }
    }

    /** 当前 schema 版本 = 历史里最高版本；无历史（全新/上线前旧库）→ 0。 */
    private static int currentVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT MAX(version) FROM " + HISTORY_TABLE)) {
            return rs.next() ? rs.getInt(1) : 0; // MAX 空集返回 NULL → getInt=0
        }
    }

    /** 追加一条迁移历史（version 为程序内整数常量，无注入风险；applied_at=epoch 毫秒）。 */
    private static void recordVersion(Connection c, int v) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO " + HISTORY_TABLE + "(version, applied_at) VALUES("
                    + v + ", " + System.currentTimeMillis() + ")");
        }
    }

    /**
     * 基线全量 schema：含历史 ADD COLUMN，列已存在时吞掉（仅此一次基线运行幂等，之后不再重跑）。
     * 这是把脆弱的"已存在"匹配收缩到只在<b>基线那一次</b>暴露的关键。
     */
    private static void runBaselineSchema(Connection c, SqlDialect dialect) throws SQLException {
        try (Statement st = c.createStatement()) {
            for (String s : dialect.schemaStatements()) {
                try {
                    st.executeUpdate(s);
                } catch (SQLException e) {
                    if (!isAlreadyExists(e)) {
                        throw e; // 只容忍"已存在"，其它建表错误照常抛出
                    }
                }
            }
        }
    }

    /** 应用一条编号迁移：迁移体(DDL/数据转换) + 追加历史，同一事务（SQLite 可回滚；MySQL 的 DDL 隐式提交，回滚仅及 DML）。 */
    private static void applyMigration(Connection c, int version, Migration migration) throws SQLException {
        boolean prevAuto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            migration.apply(c); // 可含 SELECT 旧数据→Java 转换→UPDATE 回写（迭代老数据）
            try (Statement st = c.createStatement()) {
                st.executeUpdate("INSERT INTO " + HISTORY_TABLE + "(version, applied_at) VALUES("
                        + version + ", " + System.currentTimeMillis() + ")");
            }
            c.commit();
        } catch (SQLException e) {
            try {
                c.rollback();
            } catch (SQLException ignore) {
                // 回滚本身失败不掩盖原始异常
            }
            throw new PersistenceException("数据库迁移到版本 " + version
                    + " 失败，已回滚（注意 MySQL 的 DDL 不可回滚，需人工核对该表结构）", e);
        } finally {
            c.setAutoCommit(prevAuto);
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
