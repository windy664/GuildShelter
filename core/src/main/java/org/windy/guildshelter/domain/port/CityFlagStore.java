package org.windy.guildshelter.domain.port;

import org.windy.guildshelter.domain.model.GuildId;

import java.util.Map;

/**
 * 公会<b>主城</b> flag 的持久化：{@code guild → (flagId → value)}（整会一组，作用于主城领地）。
 *
 * <p>主城不是 {@link org.windy.guildshelter.domain.model.Manor}，没有 per-slot flag；其 flag 单独存于此。
 * 由会长/副会长用 {@code /gs flag set} 在主城内设置；保护/环境监听器经 {@code ManorLookup.resolveFlag}
 * 在主城 chunk 上读取（未设则用 flag 默认值）。三个存储后端（SQLite/MySQL/平铺文件）各自实现。
 */
public interface CityFlagStore {

    /** 某会主城的全部 flag（flagId→value）；不存在返回空 map。 */
    Map<String, String> flags(GuildId guild);

    /** 设置（覆盖）某会主城的一个 flag。 */
    void put(GuildId guild, String flagId, String value);

    /** 取消某会主城的一个 flag（回退默认值）。 */
    void remove(GuildId guild, String flagId);

    /** 清除整个公会的主城 flag（解散时用）。 */
    void clear(GuildId guild);
}
