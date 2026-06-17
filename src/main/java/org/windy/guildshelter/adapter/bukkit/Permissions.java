package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.entity.Player;

/**
 * GuildShelter 的 Bukkit 权限节点常量（供 LuckPerms 等权限组插件对接）。
 * 命令级节点(guildshelter.command.*)随命令分权时在此扩充。
 *
 * <p>细粒度 admin 节点仿 PlotSquared 拆分，可给协管细分权限。
 * 持有 {@link #ADMIN} 总节点 = 自动拥有所有子节点（向后兼容）。
 */
public final class Permissions {

    private Permissions() {
    }

    // ===== 总管 =====
    /** 全权管理员：自动拥有所有 admin 子节点（向后兼容）。 */
    public static final String ADMIN = "guildshelter.admin";

    // ===== admin 细粒度（仿 PlotSquared） =====
    /** 在别人地皮建造/破坏方块。 */
    public static final String ADMIN_BUILD_OTHER = "guildshelter.admin.build.other";
    /** 在道路建造/破坏方块。 */
    public static final String ADMIN_BUILD_ROAD = "guildshelter.admin.build.road";
    /** 在别人地皮破坏方块。 */
    public static final String ADMIN_DESTROY_OTHER = "guildshelter.admin.destroy.other";
    /** 在道路破坏方块。 */
    public static final String ADMIN_DESTROY_ROAD = "guildshelter.admin.destroy.road";
    /** 在别人地皮交互（右键容器/按钮等）。 */
    public static final String ADMIN_INTERACT_OTHER = "guildshelter.admin.interact.other";
    /** 在道路交互。 */
    public static final String ADMIN_INTERACT_ROAD = "guildshelter.admin.interact.road";
    /** 管理别人地皮的 flag。 */
    public static final String ADMIN_FLAG_OTHER = "guildshelter.admin.flag.other";
    /** 管理别人地皮的 trust/member/deny。 */
    public static final String ADMIN_TRUST_OTHER = "guildshelter.admin.trust.other";
    /** 无视世界边界。 */
    public static final String ADMIN_BYPASS_BORDER = "guildshelter.admin.bypass.border";
    /** 无视 deny-entry/deny flag（可进入被谢客的地皮）。 */
    public static final String ADMIN_BYPASS_ENTRY = "guildshelter.admin.bypass.entry";

    // ===== 兼容旧节点 =====
    /** @deprecated 用 {@link #ADMIN_BYPASS_ENTRY} 替代；保留兼容。 */
    @Deprecated
    public static final String BYPASS_DENY = "guildshelter.bypass.deny";

    // ===== per-flag 权限 =====
    /** flag 设置总开关（庄主天然有，此节点控制非庄主能否设 flag）。 */
    public static final String FLAG_SET = "guildshelter.flag.set";

    // ===== 批量操作 =====
    /** 允许用 * 批量 trust。 */
    public static final String TRUST_EVERYONE = "guildshelter.trust.everyone";
    /** 允许用 * 批量 deny。 */
    public static final String DENY_EVERYONE = "guildshelter.deny.everyone";

    // ===== 辅助方法 =====

    /**
     * 该玩家是否持有指定的细粒度 admin 权限（ADMIN 总节点 = 自动拥有全部子节点）。
     */
    public static boolean hasAdminPerm(Player player, String specific) {
        return player.hasPermission(ADMIN) || player.hasPermission(specific);
    }

    /**
     * 该玩家是否可无视 deny-entry/bypass.deny（兼容旧节点）。
     */
    public static boolean canBypassEntry(Player player) {
        return hasAdminPerm(player, ADMIN_BYPASS_ENTRY) || player.hasPermission(BYPASS_DENY);
    }

    /**
     * 生成 per-flag 权限节点字符串：{@code guildshelter.flag.set.<flagId>}。
     */
    public static String flagSet(String flagId) {
        return "guildshelter.flag.set." + flagId;
    }
}
