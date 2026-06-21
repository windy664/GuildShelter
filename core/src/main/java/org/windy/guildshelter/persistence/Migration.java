package org.windy.guildshelter.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 一条 schema/数据迁移步骤（由 {@link SqlDialect#migrations()} 按版本号给出，{@link JdbcDatabase} 按序执行）。
 *
 * <p>关键：迁移不只是改表结构，<b>也能迭代老数据</b>——{@link #apply(Connection)} 拿到的是迁移事务里的连接，
 * 可以 {@code SELECT} 旧数据、用 Java 逻辑转换、再 {@code UPDATE/INSERT} 回去（纯 SQL 表达不了的转换用这条路）。
 * 版本记录与事务边界由 {@link JdbcDatabase} 负责，迁移体只管"把库从上一版改成本版"。
 *
 * <p><b>纪律（生产红线）</b>：已发布的迁移<b>只增不改不删、永久保留</b>——这样无论用户从多老的版本上来，
 * 都能沿 v1→v2→…→vN 一级级爬上来。改表只准在末尾追加新编号，绝不回头编辑历史迁移。
 */
@FunctionalInterface
public interface Migration {

    /** 在迁移事务的连接上执行本次变更（DDL 和/或数据转换）。抛异常则上层回滚并中止启动。 */
    void apply(Connection c) throws SQLException;

    /** 便捷：把若干纯 SQL（DDL/DML）包成一条迁移——无需 Java 逻辑时用。 */
    static Migration sql(String... statements) {
        return c -> {
            try (Statement st = c.createStatement()) {
                for (String s : statements) {
                    st.execute(s);
                }
            }
        };
    }
}
