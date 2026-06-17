package org.windy.guildshelter.domain.port;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;

import java.util.List;
import java.util.Optional;

/** 庄园归属/等级的持久化（Phase 2 由 SQLite 实现）。 */
public interface ManorRepository {

    Optional<Manor> findBySlot(GuildId guild, int slot);

    Optional<Manor> findByOwner(GuildId guild, PlayerRef owner);

    /** 跨公会按 owner 查庄园（退出/解散时用，不需要事先知道公会）。 */
    Optional<Manor> findByOwnerAnywhere(PlayerRef owner);

    List<Manor> findAll(GuildId guild);

    void save(Manor manor);

    void delete(GuildId guild, int slot);

    /** 该公会下一个空闲 slot（优先复用最小空缺，保持螺旋紧凑排满）。 */
    int nextFreeSlot(GuildId guild);

    // ===== 评分系统 =====

    /** 给地皮打分（1-10，同一玩家重复评分会覆盖）。 */
    void rate(GuildId guild, int slot, PlayerRef rater, int score);

    /** 获取某玩家对某地皮的评分（未评过返回 0）。 */
    int getRating(GuildId guild, int slot, PlayerRef rater);

    /** 获取某地皮的平均评分（无评分返回 0）。 */
    double getAverageRating(GuildId guild, int slot);

    /** 获取该公会评分最高的地皮（返回 slot 列表，按平均分降序）。 */
    List<Integer> getTopRatedSlots(GuildId guild, int limit);

    /** 该地皮收到的评分数。 */
    int getRatingCount(GuildId guild, int slot);

    // ===== 留言系统 =====

    /** 给地皮留言。 */
    void addComment(GuildId guild, int slot, PlayerRef author, String message);

    /** 获取某地皮的留言列表（按时间正序）。 */
    List<CommentEntry> getComments(GuildId guild, int slot, int limit);

    /** 获取某玩家在某公会收到的所有未读留言（跨地皮）。 */
    List<CommentEntry> getInbox(PlayerRef owner, int limit);

    /** 留言记录。 */
    record CommentEntry(GuildId guild, int slot, PlayerRef author, String message, long timestamp) {}

    // ===== 合并系统 =====

    /** 记录两块地皮已合并（primarySlot 吸收 absorbedSlot，absorbedSlot 的路 chunk 归 primary）。 */
    void merge(int primarySlot, int absorbedSlot, GuildId guild);

    /** 获取某 slot 被合并到的主 slot（未合并返回自身）。 */
    int getMergedTarget(GuildId guild, int slot);

    /** 获取某主 slot 吸收的所有 slot 列表（不含自身）。 */
    List<Integer> getMergedSlots(GuildId guild, int primarySlot);

    /** 取消合并。 */
    void unmerge(GuildId guild, int primarySlot);
}
