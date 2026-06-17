package org.windy.guildshelter.domain.model;

/**
 * 玩家相对某个庄园的身份级别（仿 PlotSquared 的成员分级）。由 {@link Manor#baseRoleOf} 给出
 * <b>基础</b>身份；其中 {@link #MEMBER} 的实际建造/交互权还要看"上级是否在线"，那道门控在适配层
 * （需要 Bukkit 在线信息，domain 不掺）。
 */
public enum ManorRole {

    /** 庄主：全权，含管理 trust/flag/删除。 */
    OWNER,
    /** 共建人：始终可建造/交互（owner 离线也行），但不能管理。 */
    TRUSTED,
    /** 受限成员：仅当 owner 或某 trusted 在线时才有建造/交互权。 */
    MEMBER,
    /** 黑名单：进入/交互一律拒，覆盖访客 flag（owner/admin 除外）。 */
    DENIED,
    /** 其余访客：权限由地皮访客 flag（use/container/...）决定。 */
    VISITOR;

    /** 是否无条件可建造/交互（owner 与 trusted）。member 需另判在线门控。 */
    public boolean canBuildUnconditionally() {
        return this == OWNER || this == TRUSTED;
    }

    /** 是否可管理本庄园（trust 名单 / flag / 删除）——仅 owner（admin 节点在适配层另放行）。 */
    public boolean canManage() {
        return this == OWNER;
    }
}
