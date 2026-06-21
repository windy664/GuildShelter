package org.windy.guildshelter;

import org.bukkit.ChatColor;

/**
 * 启动横幅文本（ASCII logo + 信息汇总框），带颜色（控制台 § 渲染为 ANSI）。
 * 载体配色：增强版金橙、普通版青绿——一眼区分服务端装的是哪个 jar。
 */
public final class Texts {

    private Texts() {}

    /** GUILDSHELTER 方块艺术字（box-drawing）。 */
    private static final String[] LOGO = {
        " ██████╗ ██╗   ██╗██╗██╗     ██████╗ ███████╗██╗  ██╗███████╗██╗     ████████╗███████╗██████╗ ",
        "██╔════╝ ██║   ██║██║██║     ██╔══██╗██╔════╝██║  ██║██╔════╝██║     ╚══██╔══╝██╔════╝██╔══██╗",
        "██║  ███╗██║   ██║██║██║     ██║  ██║███████╗███████║█████╗  ██║        ██║   █████╗  ██████╔╝",
        "██║   ██║██║   ██║██║██║     ██║  ██║╚════██║██╔══██║██╔══╝  ██║        ██║   ██╔══╝  ██╔══██╗",
        "╚██████╔╝╚██████╔╝██║███████╗██████╔╝███████║██║  ██║███████╗███████╗   ██║   ███████╗██║  ██║",
        " ╚═════╝  ╚═════╝ ╚═╝╚══════╝╚═════╝ ╚══════╝╚═╝  ╚═╝╚══════╝╚══════╝   ╚═╝   ╚══════╝╚═╝  ╚═╝",
    };

    /** 旧字段保留兼容（无色 logo）；新代码用 {@link #startupBanner}。 */
    public static final String logo = "\n" + String.join("\n", LOGO) + "\n";

    /**
     * 完整启动横幅：上色 logo + 左栏式信息汇总框。一次 sendMessage 打全。
     *
     * @param hybrid      是否混合端（决定配色 + 框头标识）
     * @param version     插件版本
     * @param carrier     载体显示名（{@code bindings.carrierName()}）
     * @param storage     存储后端
     * @param protection  保护模式描述
     * @param guildSource 宿主公会能力来源
     * @param schematic   Schematic 模板后端
     * @param ui          UI 后端
     * @param stats       运行时统计（如 "3 个公会 · 12 个庄园"）
     */
    public static String startupBanner(boolean hybrid, String version, String carrier, String storage,
                                       String protection, String guildSource, String schematic, String ui,
                                       String stats) {
        String accent = hybrid ? "&6" : "&b";   // 金橙 / 青绿
        String bright = hybrid ? "&e" : "&3";
        String tag    = hybrid ? "&6❖ 混合端原生增强" : "&b❖ 纯 Bukkit 通用";
        String bar    = accent + "▌ ";

        StringBuilder sb = new StringBuilder("\n");
        for (String line : LOGO) {
            sb.append(accent).append(line).append('\n');
        }
        sb.append('\n');
        sb.append(bar).append(bright).append("GuildShelter ").append("&7v").append(version)
          .append("  ").append(tag).append('\n');
        sb.append(bar).append("&7载体  ").append("&f").append(carrier).append('\n');
        sb.append(bar).append("&7存储  ").append("&f").append(storage).append('\n');
        sb.append(bar).append("&7保护  ").append("&f").append(protection).append('\n');
        sb.append(bar).append("&7宿主  ").append("&f").append(guildSource).append('\n');
        sb.append(bar).append("&7模板  ").append("&f").append(schematic).append('\n');
        sb.append(bar).append("&7界面  ").append("&f").append(ui).append('\n');
        sb.append(bar).append("&7数据  ").append("&f").append(stats).append('\n');
        sb.append(bar).append("&a✔ 已启用  &7输入 ").append("&f/gs help &7查看命令").append('\n');

        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}
