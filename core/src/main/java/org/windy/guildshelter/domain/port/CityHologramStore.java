package org.windy.guildshelter.domain.port;

import org.windy.guildshelter.domain.model.GuildId;

import java.util.List;

/**
 * 公会<b>主城悬浮字</b>的归属映射持久化：{@code guild → [ (name, label) ]}。
 *
 * <p>悬浮字本体由后端（DecentHolograms）自行持久化（重启自恢复）；本 store 只记"哪些悬浮字属于哪个公会"，
 * 用于<b>数量限额</b>、{@code /gs holo list} 展示、解散时清理。{@code name} 是后端全局唯一名，
 * {@code label} 是展示用文本（多行以 {@code |} 连接）。三个存储后端各自实现。
 */
public interface CityHologramStore {

    /** 一条主城悬浮字记录。 */
    record HoloRecord(String name, String label) {}

    /** 某公会主城的全部悬浮字（按创建顺序）；不存在返回空表。 */
    List<HoloRecord> list(GuildId guild);

    /** 新增一条（name 须全局唯一）。 */
    void add(GuildId guild, String name, String label);

    /** 更新某悬浮字的展示文本。 */
    void updateLabel(GuildId guild, String name, String label);

    /** 删除某悬浮字记录。 */
    void remove(GuildId guild, String name);

    /** 清除整个公会的主城悬浮字记录（解散时用）。 */
    void clear(GuildId guild);
}
