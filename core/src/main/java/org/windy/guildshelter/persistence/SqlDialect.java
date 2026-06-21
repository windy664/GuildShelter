package org.windy.guildshelter.persistence;

import java.util.Collections;
import java.util.List;
import java.util.SortedMap;

/**
 * SQL 方言：SQLite 与 MySQL 的差异集中在这里——建表前置语句、基线 schema、版本化迁移、两张表的 upsert 语法。
 * 其余 SELECT/DELETE 都是可移植标准 SQL，由 {@link JdbcGuildRepository}/{@link JdbcManorRepository} 直接写。
 */
public interface SqlDialect {

    /** 建表前每次启动都跑的会话/库设置（如 SQLite 的 WAL/busy_timeout/foreign_keys）。幂等、可空。 */
    List<String> preSchemaStatements();

    /**
     * <b>基线 schema（= schema 版本 1）</b>：全量幂等建表 DDL，首次启动跑一次把任何库（全新/旧库）拉齐到 v1。
     * 含历史 {@code ALTER ADD COLUMN}（旧库加列），列已存在时抛错由 {@link JdbcDatabase} 吞掉——
     * <b>仅在基线那一次运行</b>，之后版本表记为 1 便不再重跑，故脆弱的"已存在"匹配不再每次启动暴露。
     */
    List<String> schemaStatements();

    /**
     * <b>版本 2 起的有序迁移</b>：版本号 → {@link Migration}（按 key 升序、每个版本<b>恰好执行一次</b>，每步一事务）。
     *
     * <p>以后改表<b>不要</b>再往 {@link #schemaStatements} 堆 ALTER，而是在此追加一条新编号迁移（v2, v3…）：
     * <pre>{@code
     * var m = new TreeMap<Integer, Migration>();
     * m.put(2, Migration.sql("ALTER TABLE manor ADD COLUMN foo TEXT DEFAULT ''")); // 纯改表
     * m.put(3, c -> {                                                              // 迭代老数据(Java)
     *     try (var sel = c.prepareStatement("SELECT guild_id, slot, old FROM manor");
     *          var rs = sel.executeQuery();
     *          var upd = c.prepareStatement("UPDATE manor SET foo=? WHERE guild_id=? AND slot=?")) {
     *         while (rs.next()) { upd.setString(1, transform(rs.getString("old")));
     *             upd.setString(2, rs.getString("guild_id")); upd.setInt(3, rs.getInt("slot")); upd.addBatch(); }
     *         upd.executeBatch();
     *     }
     * });
     * return m;
     * }</pre>
     *
     * <p>{@link JdbcDatabase} 据库里版本与本表最大 key 决定跑哪些；跨多版本老库会沿 v1→…→vN 逐级爬。默认空。
     * <p>注意：SQLite 的 DDL 可在事务内回滚；MySQL 的 DDL 隐式提交、不可回滚 → MySQL 侧迁移须写成幂等/分步。
     */
    default SortedMap<Integer, Migration> migrations() {
        return Collections.emptySortedMap();
    }

    /** guild_world 的 upsert（主键 guild_id），列顺序：
     *  guild_id, world_name, seed, origin_x, origin_z, guild_level, allocated_slots, layout_params。 */
    String upsertGuildWorld();

    /** manor 的 upsert（主键 guild_id+slot），列顺序：guild_id, slot, owner_uuid, level。 */
    String upsertManor();
}
