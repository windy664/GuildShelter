package org.windy.guildshelter.domain.port;

import org.windy.guildshelter.domain.model.GuildId;

import java.util.List;
import java.util.UUID;

/**
 * <b>限时路权</b>的持久化：管理员授予某玩家在某公会营地<b>道路</b>上建造/交互的<b>限时</b>许可。
 * 记录到期时间戳（毫秒）；到期由判定侧<b>惰性比较</b>（{@code expireAt < now} 即失效），无需后台清理。
 *
 * <p>按 {@code (guild, player)} 唯一：同一玩家在同一营地再次授予即覆盖（续期）。
 */
public interface RoadPermitStore {

    /** 一条路权记录。 */
    record Entry(GuildId guild, UUID player, long expireAtMillis) {}

    /** 授予/续期：到期时间戳（毫秒）。 */
    void grant(GuildId guild, UUID player, long expireAtMillis);

    /** 撤销该玩家在该营地的路权。 */
    void revoke(GuildId guild, UUID player);

    /** 该玩家在该营地路权的到期时间戳（毫秒）；无记录返回 0。 */
    long expireAt(GuildId guild, UUID player);

    /** 全部路权记录（启动时载入缓存用）。 */
    List<Entry> loadAll();

    /** 清除所有已过期记录（数据卫生，可定时调用；判定本身惰性过期不依赖它）。 */
    void purgeExpired(long nowMillis);
}
