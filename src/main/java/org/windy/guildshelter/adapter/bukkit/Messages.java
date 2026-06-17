package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 多语言消息管理。从 plugins/GuildShelter/messages_{lang}.yml 加载，
 * 缺失 key 用中文 DEFAULTS 兜底。颜色码 § 保留在语言文件中。
 *
 * <p>key 命名：{category}.{command}.{detail}，如 {@code cmd.home.teleported}。
 */
public final class Messages {

    private static final Map<String, String> messages = new HashMap<>();
    private static String currentLang = "zh_CN";

    /** 中文兜底（语言文件缺失时用，保证不会显示裸 key）。 */
    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        // === 通用错误 ===
        put("error.player_only", "§c只有玩家能用此命令。");
        put("error.no_permission", "§c你没有权限。");
        put("error.no_permission_for", "§c你没有权限使用 /gs %s。");
        put("error.no_manor", "§e你还没有地皮。");
        put("error.no_manor_hint", "§e你还没有地皮。加入有营地的公会后会自动分配。");
        put("error.no_guild_joined", "§e你还没有加入任何有营地的公会。");
        put("error.not_in_guild_world", "§c你不在公会世界里。");
        put("error.not_on_plot", "§c你不在任何地皮上。");
        put("error.world_not_exist", "§c你的公会世界不存在。");
        put("error.world_load_failed", "§c世界加载失败: %s");
        put("error.not_owner", "§c只有庄主才能执行此操作。");
        put("error.unknown_flag", "§c未知 flag: %s（/gs flag 查看可用）");
        put("error.invalid_value", "§c值非法（布尔型需 true / false）。");
        put("error.player_offline", "§c玩家 %s 不在线。");
        put("error.cannot_self", "§e不能把自己加进去。");
        put("error.cannot_self_kick", "§e不能踢自己。");
        put("error.cannot_self_deny", "§e不能拉黑自己。");
        put("error.cannot_self_swap", "§e不能和自己换。");
        put("error.cannot_self_merge", "§e不能和自己的地皮合并。");
        put("error.slot_empty", "§cslot #%s 没有庄园。");
        put("error.not_adjacent", "§c两块地皮不相邻（中间必须只隔一条路）。");
        put("error.already_merged_to_other", "§c地皮 #%s 已被合并到地皮 #%s。");
        put("error.already_merged", "§e地皮 #%s 已经合并到你的地皮了。");
        put("error.not_merged", "§c地皮 #%s 没有被合并到你的地皮。");
        put("error.no_merges", "§e你的地皮没有合并任何其他地皮。");
        put("error.need_confirm", "§e⚠ 此操作需确认。30秒内输入 §6/gs confirm §e来执行。");
        put("error.confirm_expired", "§e确认已过期（30秒），请重新执行命令。");
        put("error.no_pending", "§e没有待确认的操作。");
        put("error.unknown_command", "§c未知命令。");
        put("error.plugin_unavailable", "§c插件实例不可用。");
        put("error.console_need_player", "§c控制台需指定玩家名: /gs card <玩家>");
        put("error.console_cannot_mine", "§c控制台不能用 mine。");
        put("error.not_in_own_world", "§c你不在自己的公会世界里。");
        put("error.days_must_be_int", "§c天数必须是整数。");
        put("error.days_must_be_positive", "§c天数必须 ≥ 1。");
        put("error.guild_not_exist", "§c公会 %s 不存在或还没营地。");
        put("error.guild_not_exist_short", "§c公会不存在。");
        put("error.guild_already_exist", "§e公会世界已存在: %s");
        put("error.guild_full", "§c公会已满（名额 %s）。");
        put("error.guild_full_with_level", "§c公会已满（名额 %s，当前 %s 级）。");
        put("error.number_must_be_int", "§c数量必须是整数。");
        put("error.offset_must_be_int", "§c偏移必须是整数。");
        put("error.score_must_be_int", "§c分数必须是 1-10 的整数。");
        put("error.score_range", "§c分数必须在 1-10 之间。");
        put("error.plot_not_found", "§c找不到该地皮（%s #%s）。");
        put("error.rate_need_world", "§c你不在公会世界里。用法: /gs rate <分数> <公会名> <slot号>");
        put("error.rate_need_plot", "§c你不在任何地皮上。用法: /gs rate <分数> <公会名> <slot号>");
        put("error.cannot_rate_self", "§e不能给自己的地皮打分。");
        put("error.no_plots_in_guild", "§e该公会还没有地皮。");
        put("error.no_ratings", "§e还没有任何地皮收到评分。");
        put("error.need_admin_perm", "§c你需要 admin.trust.other 权限。");
        put("error.target_has_manor", "§e%s 在该公会已有地皮 #%s，不能重复分配。");
        put("error.not_guild_world_target", "§e%s 不在你的公会世界里。");
        put("error.is_member_cannot_kick", "§e%s 是地皮成员，不能踢。用 /gs deny 拉黑。");
        put("error.export_failed", "§c导出失败: %s");
        put("error.template_not_exist", "§c模板 '%s' 不存在。");
        put("error.template_already_exist", "§e模板 '%s' 已存在。");
        put("error.sub_not_exist", "§c子领地 '%s' 不存在。");
        put("error.titles_not_enabled", "§c标题功能未启用。");
        put("error.toggle_unknown", "§c可切换: titles");
        put("error.only_owner_template", "§c只有庄主能管理模板。");
        put("error.only_owner_sub", "§c只有庄主能管理子领地。");
        put("error.no_template_yet", "§e该公会还没有模板。用 /gs template create <名称> 创建。");
        put("error.no_sub_yet", "§e该地皮没有子领地。用 /gs sub create <名称> <dx1> <dz1> <dx2> <dz2> 创建。");
        put("error.no_manor_for_command", "§e你还没有地皮（只有庄主能设 flag，或需要 per-flag 权限）。");
        put("error.flag_already_default", "§e该 flag 本就用默认值（%s）。");
        put("error.no_need_trust", "§e没有需要 trust 的人（所有人已是共建人或无其他成员）。");
        put("error.no_need_deny", "§e没有需要 deny 的人（所有人已在黑名单或无其他成员）。");
        put("error.no_comments", "§e你的地皮还没有收到任何留言。");
        put("error.target_no_manor", "§e%s 还没有地皮。");
        put("error.not_same_guild", "§c你们不在同一个公会。");
        put("error.only_player_upgrade", "§c只有玩家能升级自己的庄园。");
        put("error.only_player_tp", "§c只有玩家能传送。");
        put("error.only_player_claim", "§c只有玩家能领地皮。");
        put("error.only_player_regen", "§c只有玩家能执行此命令。");
        put("error.no_manor_in_guild", "§c你在该公会还没有庄园，先 /gs admin claim %s");
        put("error.already_max_level", "§e庄园已达满级 %s。");
        put("error.give_no_plots", "§c该公会没有地皮。");
        put("error.not_in_any_world", "§e你不在任何公会世界里（需站在目标公会世界中）。");
        put("error.batch_trust_perm", "§c你没有权限批量 trust（需要 %s）。");
        put("error.batch_deny_perm", "§c你没有权限批量 deny（需要 %s）。");
        put("error.flag_set_perm", "§c只有庄主才能设此 flag（trusted 可设交互类，或需要 %s 权限）。");

        // === 成功消息 ===
        put("success.home_teleported", "§a已回到你的地皮 #%s。");
        put("success.spawn_teleported", "§a已传送到公会主城。");
        put("success.middle_teleported", "§a已传送到地皮 #%s 正中心。");
        put("success.visit_teleported", "§a已到访公会 %s 的主城。");
        put("success.tp_teleported", "§a已传送到 %s 主城。");
        put("success.upgraded", "§a庄园已升至 %s / %s 级，新扩范围整地进行中。");
        put("success.trust_added", "§a已把 %s 加为地皮 #%s 的共建人。");
        put("success.trust_removed", "§a已移除共建人 %s。");
        put("success.trust_batch", "§a已批量把 %s 人加为地皮 #%s 的共建人。");
        put("success.member_added", "§a已把 %s 加为成员（仅你或共建人在线时其可建造/交互）。");
        put("success.member_removed", "§a已移除成员 %s。");
        put("success.denied_added", "§a已将 %s 加入地皮 #%s 黑名单。");
        put("success.denied_removed", "§a已将 %s 移出黑名单。");
        put("success.denied_batch", "§a已批量把 %s 人加入地皮 #%s 黑名单。");
        put("success.flag_set", "§a已设 §f%s = %s §7(地皮 #%s)");
        put("success.flag_unset", "§a已重置 §f%s §a为默认（%s）。");
        put("success.alias_set", "§a地皮别名已设为: §f%s");
        put("success.alias_cleared", "§a已清除地皮别名。");
        put("success.home_set", "§a/home 传送点已设为: §f%s, %s, %s");
        put("success.done_on", "§a已标记地皮为 §a✔ 已完工。");
        put("success.done_off", "§e已取消完工标记（建造中）。");
        put("success.kicked", "§a已将 %s 踢出你的地皮。");
        put("success.kicked_notify", "§e你被 %s 从地皮 #%s 踢出了。");
        put("success.cleared", "§a已清空你地皮 #%s 的地表建筑（清植被式，整地进行中）。");
        put("success.rated", "§a已给地皮 #%s 打 §e%s §a分（平均 §e%s §a，共 %s 人评分）");
        put("success.merged", "§a已将地皮 #%s 合并到你的地皮 #%s（路 chunk 已归属你的地皮）。");
        put("success.unmerged_one", "§a已取消地皮 #%s 的合并（路 chunk 已恢复）。");
        put("success.unmerged_all", "§a已取消地皮 #%s 的所有合并（共 %s 块，路 chunk 已恢复）。");
        put("success.desc_set", "§a地皮描述已设为: §f%s");
        put("success.desc_cleared", "§a已清除地皮描述。");
        put("success.regen", "§a地皮 #%s 地形已重置（清植被式整地进行中）。");
        put("success.export", "§a数据已导出: §fplugins/GuildShelter/export/%s");
        put("success.reload", "§aconfig.yml 已重载。部分设置需重启生效。");
        put("success.setowner", "§a已将地皮 #%s 的庄主转移给 %s。");
        put("success.purge", "§a已清除 %s 块超过 %s 天未登录的地皮。");
        put("success.create_world", "§a已创建公会世界（自然地形）: %s §7种子=%s 原点偏移=(%s,%s)");
        put("success.claim", "§a已分配地皮 #%s（等级 %s），整地进行中。你已被传送过去。");
        put("success.claim_hint", "§7提示: claim 对同一玩家幂等(一人一块)。要看多块分布用 /gs admin fill %s <数量>。网格图已打到控制台。");
        put("success.upgrade_guild", "§a公会已升至 %s / %s 级；成员名额 %s → %s，世界边界 %s → %s 方块。");
        put("success.delete", "§a已卸载并移除记录: %s（世界文件夹未删除，需手动清理）");
        put("success.fill", "§a已为 %s 填充 %s 块测试地皮。网格图见控制台。");
        put("success.fill_full", "§e达到当前等级名额上限 %s，已填 %s 块。先 /gs admin upgrade-guild %s 再继续。");
        put("success.map", "§a网格分布图已输出到控制台。");
        put("success.swap", "§a已与 %s 互换地皮（你的 #%s ↔ #%s）。");
        put("success.swap_notify", "§a%s 与你互换了地皮（你的 #%s ↔ #%s）。");
        put("success.grant", "§a已给 %s 分配一块新地皮。");
        put("success.template_created", "§a已创建模板 §f%s §a。用 /gs template setflag %s <flag> <值> 添加配置。");
        put("success.template_deleted", "§a已删除模板 §f%s");
        put("success.template_flag_set", "§a模板 §f%s §a: %s = %s");
        put("success.template_applied", "§a已将模板 §f%s §a(%s 个 flag) 应用到地皮 #%s");
        put("success.sub_created", "§a已创建子领地 §f%s §a(%sx%s 方块)。");
        put("success.sub_deleted", "§a已删除子领地 §f%s");
        put("success.sub_flag_set", "§a子领地 §f%s §a: %s = %s");
        put("success.titles_on", "§a已开启进出标题消息。");
        put("success.titles_off", "§e已关闭进出标题消息（改为聊天框显示）。");
        put("success.comment_added", "§a已给地皮 #%s 留言。");
        put("success.welcome", "§6==== 欢迎来到公会营地 ====\n§7你已被分配到 §f%s §7的地皮 §f#%s\n§7输入 §e/gs home §7传送到你的地皮\n§7输入 §e/gs help §7查看所有命令");

        // === 信息 ===
        put("info.no_guilds", "§e还没有任何公会营地。");
        put("info.guild_list_header", "§6==== %s公会营地 (%s) ====");
        put("info.guild_list_entry", "§7- §f%s §7Lv%s 成员 %s/%s §8(/gs visit %s)");
        put("info.near_header", "§6==== 附近地皮 ====");
        put("info.near_entry", "§7- §f%s §7庄主: §f%s §7距离: §e%s 格");
        put("info.top_header", "§6==== %s 地皮%s排行 ====");
        put("info.top_entry", "§e%s. §f%s §7庄主: §f%s §7%s: §e%s");
        put("info.inbox_header", "§6==== 收件箱（最近 20 条）====");
        put("info.inbox_entry", "§7[%s] §f%s §7→ 地皮#%s: §f%s");
        put("info.template_header", "§6==== 权限模板 ====");
        put("info.template_entry", "§7- §f%s §7(%s 个 flag)");
        put("info.sub_header", "§6==== 子领地列表 ====");
        put("info.sub_entry", "§7- §f%s §7(%sx%s) §7flags: %s");
        put("info.world_list", "§e已加载世界 (%s):");
        put("info.world_entry", "§7- §f%s §7env=%s spawn=(%s,%s,%s)");
        put("info.whereami", "§e你在世界 §f%s §7(%s,%s,%s)");
        put("info.help_header", "§6==== GuildShelter 命令 ====");
        put("info.help_entry", "§e/gs %s §7- %s");
        put("info.help_footer", "§8输入 /gs help <命令> 查看详细用法");
        put("info.help_cmd", "§6/gs %s §7- %s");
        put("info.help_unknown", "§c未知命令: %s");
        put("info.flag_header", "§6==== 地皮 #%s Flag ====");
        put("info.flag_usage", "§7用法: /gs flag set <flag> <值> | unset <flag>");
        put("info.flag_entry", "§7%s = %s §8- %s");
        put("info.flag_value_set", "§f%s");
        put("info.flag_value_default", "§8%s(默认)");
        put("info.card_header", "§6┌─────────── §e家园卡 §6───────────");
        put("info.card_plot", "§6│ §7地皮: §f%s §7公会: §f%s §7等级: §f%s%s");
        put("info.card_owner", "§6│ §7庄主: §f%s §7庄园Lv: §f%s/%s §7尺寸: §f%sx%s");
        put("info.card_alias", "§6│ §7别名: §f%s");
        put("info.card_desc", "§6│ §7描述: §f%s");
        put("info.card_entities", "§6│ §7实体: %s");
        put("info.card_members", "§6│ §7成员: §f%s/%s §7trusted: §f%s §7denied: §c%s");
        put("info.card_flags", "§6│ §7活跃flag: §f%s §7个");
        put("info.card_price", "§6│ §7入场费: §e%s");
        put("info.card_score", "§6│ §7活跃flag: §f%s §7个");
        put("info.card_footer", "§6│");
        put("info.card_score_line", "§6│ §e综合评分: §a§l%s §7分");
        put("info.card_bottom", "§6└─────────────────────────");
        put("info.done_status", "§a✔ 已完工");
        put("info.building_status", "§e🔨 建造中");
        put("info.no_alias", "§8无");
        put("info.entity_world_not_loaded", "§8(世界未加载)");
        put("info.entity_line", "§a%s §7动物 §c%s §7敌对 §b%s §7其它 §6%s §7载具");

        // === 帮助用法 ===
        put("usage.home", "用法: /gs home");
        put("usage.spawn", "用法: /gs spawn");
        put("usage.upgrade", "用法: /gs upgrade");
        put("usage.info", "用法: /gs info");
        put("usage.trust", "用法: /gs trust <玩家|*>");
        put("usage.untrust", "用法: /gs untrust <玩家>");
        put("usage.member", "用法: /gs member <add|remove> <玩家>");
        put("usage.deny", "用法: /gs deny <玩家|*>");
        put("usage.undeny", "用法: /gs undeny <玩家>");
        put("usage.visit", "用法: /gs visit <公会id>");
        put("usage.flag_set", "用法: /gs flag set <flag> <值>");
        put("usage.flag_unset", "用法: /gs flag unset <flag>");
        put("usage.kick", "用法: /gs kick <玩家>");
        put("usage.rate", "用法: /gs rate <1-10> [公会名 slot号]");
        put("usage.top", "§e用法: /gs top [公会名] [rating|level|members|entities]");
        put("usage.comment", "用法: /gs comment <留言内容>");
        put("usage.swap", "用法: /gs swap <玩家>");
        put("usage.grant", "用法: /gs grant <玩家>");
        put("usage.merge", "用法: /gs merge <slot号>（把该 slot 合并到你的地皮）");
        put("usage.unmerge", "用法: /gs unmerge [slot]");
        put("usage.template", "§e用法: /gs template <create|delete|apply|setflag|list> [名称] [参数]");
        put("usage.template_create", "用法: /gs template create <名称>");
        put("usage.template_delete", "用法: /gs template delete <名称>");
        put("usage.template_setflag", "用法: /gs template setflag <模板名> <flag> <值>");
        put("usage.template_apply", "用法: /gs apply <模板名>（把模板配置应用到当前地皮）");
        put("usage.sub", "§e用法: /gs sub <create|delete|setflag|list> [名称] [参数]");
        put("usage.sub_create", "用法: /gs sub create <名称> <dx1> <dz1> <dx2> <dz2>");
        put("usage.sub_create_hint", "  dx/dz 是相对于你当前位置的偏移（方块），如 -10 0 10 10");
        put("usage.sub_delete", "用法: /gs sub delete <名称>");
        put("usage.sub_setflag", "用法: /gs sub setflag <子领地名> <flag> <值>");
        put("usage.toggle", "用法: /gs toggle titles");
        put("usage.admin", "用法: /gs admin <create|tp|claim|fill|map|upgrade-manor|upgrade-guild|delete|worlds|whereami|reload|setowner|purge|regen|export> [公会id]");
        put("usage.admin_create", "用法: /gs admin create <公会id>");
        put("usage.admin_tp", "用法: /gs admin tp <公会id>");
        put("usage.admin_claim", "用法: /gs admin claim <公会id>");
        put("usage.admin_fill", "用法: /gs admin fill <公会id> <数量>");
        put("usage.admin_map", "用法: /gs admin map <公会id>");
        put("usage.admin_upgrade_manor", "用法: /gs admin upgrade-manor <公会id>");
        put("usage.admin_upgrade_guild", "用法: /gs admin upgrade-guild <公会id>");
        put("usage.admin_delete", "用法: /gs admin delete <公会id>");
        put("usage.admin_setowner", "用法: /gs admin setowner <公会id> <玩家>");
        put("usage.admin_purge", "用法: /gs admin purge <天数> [公会id]");
        put("usage.player_commands", "§e/gs <home|spawn|upgrade|info|trust|untrust|member|deny|undeny|list|visit|clear|flag|card|alias|sethome|done|kick|near|rate|top|middle|comment|inbox|swap|grant|merge|unmerge>  §7玩家命令");
        put("usage.admin_commands", "§7/gs admin ...  §8管理命令");

        // === MOTD ===
        put("motd.format", "§6[§e%s§6] §7%s");

        // === 监听器 ===
        put("listener.blacklisted", "§c你被列入这块地皮的黑名单，无法进入。");
        put("listener.deny_entry", "§c这块地皮谢绝访客进入。");
        put("listener.deny_exit", "§c你被困在这块地皮里，无法离开。");
        put("listener.cmd_blocked", "§c此地皮禁止使用 /%s 命令。");
        put("listener.price_charged", "§7已收取入场费 §e%s §7进入地皮 #%s");
        put("listener.price_no_money", "§c进入此地皮需要 %s，你的余额不足。");
        put("listener.build_denied", "§c这里不是你能改动的区域。");
    }

    private static void put(String key, String value) {
        DEFAULTS.put(key, value);
    }

    /**
     * 加载语言文件。lang = "zh_CN" / "en_US" 等。
     */
    public static void load(String lang, File dataFolder) {
        currentLang = lang;
        messages.clear();
        messages.putAll(DEFAULTS);

        // 先尝试从 resources 加载默认文件到 dataFolder
        String resourceName = "/messages_" + lang + ".yml";
        InputStream res = Messages.class.getResourceAsStream(resourceName);
        if (res != null) {
            File langFile = new File(dataFolder, "messages_" + lang + ".yml");
            if (!langFile.exists()) {
                try (InputStreamReader reader = new InputStreamReader(res, StandardCharsets.UTF_8)) {
                    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(reader);
                    cfg.save(langFile);
                } catch (IOException ignored) {}
            }
            // 从文件加载（覆盖 DEFAULTS）
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(langFile);
            for (String key : cfg.getKeys(true)) {
                if (cfg.isString(key)) {
                    messages.put(key, cfg.getString(key));
                }
            }
        }
    }

    /** 获取消息，%s 等占位符由调用方替换。key 不存在时返回 key 本身。 */
    public static String get(String key, Object... args) {
        String msg = messages.getOrDefault(key, key);
        if (args.length > 0) {
            try {
                msg = String.format(msg, args);
            } catch (Exception ignored) {}
        }
        return msg;
    }

    public static String lang() {
        return currentLang;
    }
}
