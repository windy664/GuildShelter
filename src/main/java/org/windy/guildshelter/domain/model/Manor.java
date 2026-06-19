package org.windy.guildshelter.domain.model;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 成员庄园：占公会营地里的一个螺旋 slot。物理范围由 LayoutCalculator 从 slot 算出满级范围；
 * 当前<b>可建造范围 = 已解锁的 chunk 集合</b>（玩家凭额度自由解锁，见 {@link #unlockedChunks}）。
 *
 * <p>身份分级（仿 PlotSquared，见权限系统计划）：owner &gt; trusted &gt; member &gt; denied &gt; 访客。
 * {@code coBuilders} 即 <b>trusted</b>（始终可建造的共建人，沿用旧字段名以兼容存储）；
 * {@code members} 为受限成员（仅 owner/trusted 在线时才有权，门控在适配层）；
 * {@code denied} 为黑名单（覆盖访客 flag，进入/交互一律拒，owner/admin 除外）。
 *
 * @param slot           螺旋 slot 号（0 起）
 * @param guild          所属公会
 * @param owner          庄园主
 * @param level          庄园等级（决定<b>额度上限</b>，不再直接决定范围）
 * @param coBuilders     trusted 共建人（owner 之外始终可建造）
 * @param members        受限成员（建造/交互权需上级在线，门控在适配层）
 * @param denied         黑名单玩家
 * @param flags          庄园 flag（flag id → 值字符串）；未含的 flag 用其默认值
 * @param unlockedChunks 已解锁的 chunk（相对满级庄园原点的偏移，编码见 {@link #packOffset}）。
 *                       玩家凭等级额度逐格解锁；权限按本集合判，与等级解耦。
 */
public record Manor(int slot, GuildId guild, PlayerRef owner, int level,
                    Set<PlayerRef> coBuilders, Set<PlayerRef> members, Set<PlayerRef> denied,
                    Map<String, String> flags, Set<Integer> unlockedChunks) {

    public Manor {
        if (slot < 0) {
            throw new IllegalArgumentException("slot 必须 ≥0");
        }
        Objects.requireNonNull(guild, "guild");
        Objects.requireNonNull(owner, "owner");
        if (level < 1) {
            throw new IllegalArgumentException("level 必须 ≥1");
        }
        coBuilders = Set.copyOf(coBuilders == null ? Set.of() : coBuilders);
        members = Set.copyOf(members == null ? Set.of() : members);
        denied = Set.copyOf(denied == null ? Set.of() : denied);
        flags = Map.copyOf(flags == null ? Map.of() : flags);
        unlockedChunks = Set.copyOf(unlockedChunks == null ? Set.of() : unlockedChunks);
    }

    /** 把庄园内部偏移 (dx,dz)（均 0..plotChunks-1，<1024）打包成单个 int：高位 dx，低位 dz。 */
    public static int packOffset(int dx, int dz) {
        return (dx << 10) | (dz & 0x3FF);
    }

    public static int unpackDx(int packed) {
        return packed >>> 10;
    }

    public static int unpackDz(int packed) {
        return packed & 0x3FF;
    }

    /** 该内部偏移的 chunk 是否已解锁（可建造）。 */
    public boolean isUnlocked(int dx, int dz) {
        return unlockedChunks.contains(packOffset(dx, dz));
    }

    /** 兼容构造：8 参（无 unlockedChunks，置空）。 */
    public Manor(int slot, GuildId guild, PlayerRef owner, int level,
                 Set<PlayerRef> coBuilders, Set<PlayerRef> members, Set<PlayerRef> denied,
                 Map<String, String> flags) {
        this(slot, guild, owner, level, coBuilders, members, denied, flags, Set.of());
    }

    /** 兼容构造：旧的 (…, coBuilders, flags) 签名。members/denied 置空——持久化尚未落这两列时的读取路径走这里。 */
    public Manor(int slot, GuildId guild, PlayerRef owner, int level,
                 Set<PlayerRef> coBuilders, Map<String, String> flags) {
        this(slot, guild, owner, level, coBuilders, Set.of(), Set.of(), flags, Set.of());
    }

    /** 兼容构造：不带 flag（空）。 */
    public Manor(int slot, GuildId guild, PlayerRef owner, int level, Set<PlayerRef> coBuilders) {
        this(slot, guild, owner, level, coBuilders, Map.of());
    }

    public static Manor create(int slot, GuildId guild, PlayerRef owner) {
        return new Manor(slot, guild, owner, 1, Set.of(), Set.of(), Set.of(), Map.of(), Set.of());
    }

    /** trusted 共建人集合（{@link #coBuilders()} 的语义别名）。 */
    public Set<PlayerRef> trusted() {
        return coBuilders;
    }

    /**
     * 该玩家在本庄园的<b>基础身份</b>（不含"成员在线门控"等运行期判断，那部分在适配层）。
     * 判定顺序：owner &gt; denied &gt; trusted &gt; member &gt; 访客（owner 永不被 denied 覆盖）。
     */
    public ManorRole baseRoleOf(PlayerRef player) {
        if (owner.equals(player)) {
            return ManorRole.OWNER;
        }
        if (denied.contains(player)) {
            return ManorRole.DENIED;
        }
        if (coBuilders.contains(player)) {
            return ManorRole.TRUSTED;
        }
        if (members.contains(player)) {
            return ManorRole.MEMBER;
        }
        return ManorRole.VISITOR;
    }

    /** 该玩家是否可在本庄园建造（庄主或 trusted 共建人；member 的在线门控在适配层另判）。 */
    public boolean hasBuildAccess(PlayerRef player) {
        return owner.equals(player) || coBuilders.contains(player);
    }

    public Manor withLevel(int newLevel) {
        return new Manor(slot, guild, owner, newLevel, coBuilders, members, denied, flags, unlockedChunks);
    }

    public Manor withCoBuilders(Set<PlayerRef> newCoBuilders) {
        return new Manor(slot, guild, owner, level, newCoBuilders, members, denied, flags, unlockedChunks);
    }

    public Manor withMembers(Set<PlayerRef> newMembers) {
        return new Manor(slot, guild, owner, level, coBuilders, newMembers, denied, flags, unlockedChunks);
    }

    public Manor withDenied(Set<PlayerRef> newDenied) {
        return new Manor(slot, guild, owner, level, coBuilders, members, newDenied, flags, unlockedChunks);
    }

    public Manor withFlags(Map<String, String> newFlags) {
        return new Manor(slot, guild, owner, level, coBuilders, members, denied, newFlags, unlockedChunks);
    }

    public Manor withUnlockedChunks(Set<Integer> newUnlocked) {
        return new Manor(slot, guild, owner, level, coBuilders, members, denied, flags, newUnlocked);
    }
}
