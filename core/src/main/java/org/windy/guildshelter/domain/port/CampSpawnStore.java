package org.windy.guildshelter.domain.port;

import org.windy.guildshelter.domain.model.CampSpawn;
import org.windy.guildshelter.domain.model.GuildId;

import java.util.Optional;

/**
 * 公会营地传送点的持久化：每会两类点（{@link Type#MEMBER} 成员点 / {@link Type#VISITOR} 访客点）。
 * 由会长/副会长用 {@code /gs setspawn} 设置；{@code /gs spawn} 取成员点、{@code /gs visit} 取访客点，
 * 未设置则回退主城安全出生点。三个存储后端（SQLite/MySQL/平铺文件）各自实现。
 */
public interface CampSpawnStore {

    /** 传送点类型。 */
    enum Type { MEMBER, VISITOR }

    /** 取某会某类型传送点；未设置返回 empty。 */
    Optional<CampSpawn> get(GuildId guild, Type type);

    /** 设置（覆盖）某会某类型传送点。 */
    void set(GuildId guild, Type type, CampSpawn spawn);

    /** 清除整个公会的传送点（解散时用）。 */
    void clear(GuildId guild);
}
