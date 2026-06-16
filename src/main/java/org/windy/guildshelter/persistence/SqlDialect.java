package org.windy.guildshelter.persistence;

import java.util.List;

/**
 * SQL 方言：SQLite 与 MySQL 的差异集中在这里——建表前置语句、建表 DDL、两张表的 upsert 语法。
 * 其余 SELECT/DELETE 都是可移植标准 SQL，由 {@link JdbcGuildRepository}/{@link JdbcManorRepository} 直接写。
 */
public interface SqlDialect {

    /** 建表前要跑的语句（如 SQLite 的 WAL/foreign_keys）。可空。 */
    List<String> preSchemaStatements();

    /** 建表/迁移 DDL（按顺序执行；迁移类语句可能在列已存在时抛错，调用方应吞掉）。 */
    List<String> schemaStatements();

    /** guild_world 的 upsert（主键 guild_id），列顺序：
     *  guild_id, world_name, seed, origin_x, origin_z, guild_level, allocated_slots, layout_params。 */
    String upsertGuildWorld();

    /** manor 的 upsert（主键 guild_id+slot），列顺序：guild_id, slot, owner_uuid, level。 */
    String upsertManor();
}
