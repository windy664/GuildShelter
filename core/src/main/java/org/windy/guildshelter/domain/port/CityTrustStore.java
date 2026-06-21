package org.windy.guildshelter.domain.port;

import org.windy.guildshelter.domain.model.GuildId;

import java.util.Set;
import java.util.UUID;

/**
 * 主城信任名单的持久化：{@code guild → Set<UUID>}（整会一张表，被信任者可建造该公会主城）。
 *
 * <p>会长本身的主城权限来自 {@link GuildProvider#isGuildAdmin}，不入此表；此表只存会长<b>额外信任</b>的会内成员。
 * 三个存储后端（SQLite/MySQL/平铺文件）各自实现。
 */
public interface CityTrustStore {

    /** 某公会的主城信任成员集合（不存在返回空集）。 */
    Set<UUID> trusted(GuildId guild);

    /** 信任某玩家可建造主城。 */
    void add(GuildId guild, UUID player);

    /** 撤销某玩家的主城信任。 */
    void remove(GuildId guild, UUID player);

    /** 清除整个公会的主城信任（解散时用）。 */
    void clear(GuildId guild);
}
