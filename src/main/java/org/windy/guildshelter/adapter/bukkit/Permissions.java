package org.windy.guildshelter.adapter.bukkit;

/**
 * GuildShelter 的 Bukkit 权限节点常量（供 LuckPerms 等权限组插件对接）。
 * 命令级节点(guildshelter.command.*)随命令分权时在此扩充。
 */
public final class Permissions {

    private Permissions() {
    }

    /** 全权管理员：bypass 一切保护/flag/黑名单，可管理任意庄园。 */
    public static final String ADMIN = "guildshelter.admin";

    /** 无视黑名单与 deny-entry（可进入/交互被拉黑或谢客的地皮，但不等于可建造）。 */
    public static final String BYPASS_DENY = "guildshelter.bypass.deny";
}
