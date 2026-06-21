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
        put("error.no_manor", "§e你还没有庄园。");
        put("error.no_manor_hint", "§e你还没有庄园。加入有营地的公会后会自动分配。");
        put("error.tile_cap_reached", "§c本庄园方块实体已达上限，无法再放置（可联系管理员提升上限）。");
        put("error.machine_cap_reached", "§c本庄园「%s」数量已达上限，无法再放置（可联系管理员提升配额）。");
        put("error.no_guild_joined", "§e你还没有加入任何有营地的公会。");
        put("error.not_in_guild_world", "§c你不在公会营地里。");
        put("error.not_on_plot", "§c你不在任何庄园上。");
        put("error.world_not_exist", "§c你的公会营地不存在。");
        put("error.world_load_failed", "§c世界加载失败: %s");
        put("error.not_owner", "§c只有庄主才能执行此操作。");
        put("error.unknown_flag", "§c未知 flag: %s（/gs flag 查看可用）");
        put("error.invalid_value", "§c值非法（布尔型需 true / false）。");
        put("error.player_offline", "§c玩家 %s 不在线。");
        put("error.cannot_self", "§e不能把自己加进去。");
        put("error.cannot_self_kick", "§e不能踢自己。");
        put("error.cannot_self_deny", "§e不能拉黑自己。");
        put("error.cannot_self_swap", "§e不能和自己换。");
        put("error.cannot_self_merge", "§e不能和自己的庄园合并。");
        put("error.slot_empty", "§cslot #%s 没有庄园。");
        put("error.not_adjacent", "§c两块庄园不相邻（中间必须只隔一条路）。");
        put("error.already_merged_to_other", "§c庄园 #%s 已被合并到庄园 #%s。");
        put("error.already_merged", "§e庄园 #%s 已经合并到你的庄园了。");
        put("error.not_merged", "§c庄园 #%s 没有被合并到你的庄园。");
        put("error.no_merges", "§e你的庄园没有合并任何其他庄园。");
        put("error.need_confirm", "§e⚠ 此操作需确认。30秒内输入 §6/gs confirm §e来执行。");
        put("error.confirm_expired", "§e确认已过期（30秒），请重新执行命令。");
        put("error.no_pending", "§e没有待确认的操作。");
        put("error.unknown_command", "§c未知命令。");
        put("error.plugin_unavailable", "§c插件实例不可用。");
        put("error.console_need_player", "§c控制台需指定玩家名: /gs card <玩家>");
        put("error.console_cannot_mine", "§c控制台不能用 mine。");
        put("error.not_in_own_world", "§c你不在自己的公会营地里。");
        put("error.days_must_be_int", "§c天数必须是整数。");
        put("error.days_must_be_positive", "§c天数必须 ≥ 1。");
        put("error.guild_not_exist", "§c公会 %s 不存在或还没营地。");
        put("error.guild_not_exist_short", "§c公会不存在。");
        put("error.guild_already_exist", "§e公会营地已存在: %s");
        put("error.guild_full", "§c公会已满（名额 %s）。");
        put("error.guild_full_with_level", "§c公会已满（名额 %s，当前 %s 级）。");
        put("error.number_must_be_int", "§c数量必须是整数。");
        put("error.offset_must_be_int", "§c偏移必须是整数。");
        put("error.sub_out_of_bounds", "§c子领地超出你的庄园范围。");
        put("error.invalid_name", "§c名称只能包含字母、数字、下划线和横杠。");
        put("error.positive_required", "§c金额必须大于 0。");
        put("error.too_long", "§c内容过长（最多 %s 个字符）。");
        put("error.score_must_be_int", "§c分数必须是 1-10 的整数。");
        put("error.score_range", "§c分数必须在 1-10 之间。");
        put("error.plot_not_found", "§c找不到该庄园（%s #%s）。");
        put("error.rate_need_world", "§c你不在公会营地里。用法: /gs rate <分数> <公会名> <slot号>");
        put("error.rate_need_plot", "§c你不在任何庄园上。用法: /gs rate <分数> <公会名> <slot号>");
        put("error.cannot_rate_self", "§e不能给自己的庄园打分。");
        put("error.no_plots_in_guild", "§e该公会还没有庄园。");
        put("error.no_ratings", "§e还没有任何庄园收到评分。");
        put("error.need_admin_perm", "§c你需要 admin.trust.other 权限。");
        put("error.target_has_manor", "§e%s 在该公会已有庄园 #%s，不能重复分配。");
        put("error.not_guild_world_target", "§e%s 不在你的公会营地里。");
        put("error.is_member_cannot_kick", "§e%s 是庄园成员，不能踢。用 /gs deny 拉黑。");
        put("error.export_failed", "§c导出失败: %s");
        put("error.unknown_terrain", "§c未知地形类型: %s（可选: NONE, CLEAR_VEGETATION, FLATTEN, VOID, FLAT）");
        put("error.template_not_exist", "§c模板 '%s' 不存在。");
        put("error.template_already_exist", "§e模板 '%s' 已存在。");
        put("error.sub_not_exist", "§c子领地 '%s' 不存在。");
        put("error.titles_not_enabled", "§c标题功能未启用。");
        put("error.toggle_unknown", "§c可切换: titles");
        put("error.only_owner", "§c只有庄主才能执行此操作。");
        put("error.only_owner_template", "§c只有庄主能管理模板。");
        put("error.only_owner_sub", "§c只有庄主能管理子领地。");
        put("error.no_template_yet", "§e该公会还没有模板。用 /gs template create <名称> 创建。");
        put("error.no_sub_yet", "§e该庄园没有子领地。用 /gs sub create <名称> <dx1> <dz1> <dx2> <dz2> 创建。");
        put("error.no_manor_for_command", "§e你还没有庄园（只有庄主能设 flag，或需要 per-flag 权限）。");
        put("error.flag_already_default", "§e该 flag 本就用默认值（%s）。");
        put("error.no_need_trust", "§e没有需要 trust 的人（所有人已是共建人或无其他成员）。");
        put("error.no_need_deny", "§e没有需要 deny 的人（所有人已在黑名单或无其他成员）。");
        put("error.no_comments", "§e你的庄园还没有收到任何留言。");
        put("error.target_no_manor", "§e%s 还没有庄园。");
        put("error.not_same_guild", "§c你们不在同一个公会。");
        put("error.only_player_upgrade", "§c只有玩家能升级自己的庄园。");
        put("error.only_player_tp", "§c只有玩家能传送。");
        put("error.only_player_claim", "§c只有玩家能领庄园。");
        put("error.only_player_regen", "§c只有玩家能执行此命令。");
        put("error.no_manor_in_guild", "§c你在该公会还没有庄园，先 /gs admin claim %s");
        put("error.already_max_level", "§e庄园已达满级 %s。");
        put("error.give_no_plots", "§c该公会没有庄园。");
        put("error.not_in_any_world", "§e你不在任何公会营地里（需站在目标公会营地中）。");
        put("error.batch_trust_perm", "§c你没有权限批量 trust（需要 %s）。");
        put("error.batch_deny_perm", "§c你没有权限批量 deny（需要 %s）。");
        put("error.flag_set_perm", "§c只有庄主才能设此 flag（trusted 可设交互类，或需要 %s 权限）。");
        put("error.move_not_enabled", "§c搬家功能未启用。");
        put("error.move_no_manor", "§e你还没有庄园，无法搬家。");
        put("error.move_same_guild", "§e你已经在目标公会了。");
        put("error.move_cooldown", "§e搬家冷却中，还需等待 §f%s §e天。");
        put("error.move_not_enough_money", "§c搬家费用不足，需要 §e%s§c。");
        put("error.move_target_not_exist", "§c目标公会不存在或还没有营地。");
        put("error.move_target_full", "§c目标公会名额已满，无法加入。");
        put("error.move_copy_failed", "§c建筑复制失败，请联系管理员。");

        // === 成功消息 ===
        put("success.home_teleported", "§6✦ §a已回到你的庄园 §f#%s");
        put("success.spawn_teleported", "§6✦ §a已传送到公会主城");
        put("success.middle_teleported", "§6✦ §a已传送到庄园 §f#%s §a正中心");
        put("success.visit_teleported", "§6✦ §a欢迎来到 §f%s §a的营地");
        put("success.tp_teleported", "§6✦ §a已传送到 §f%s §a主城");
        put("success.upgraded", "§6⬆ §a庄园升级！§f%s §7/ §f%s §a级 §7— 新范围整地进行中...");
        put("success.trust_added", "§b🤝 §f%s §a已成为庄园 §f#%s §a的共建人");
        put("success.trust_added_already", "§e⚠ §f%s §7已经是共建人了");
        put("success.trust_removed", "§c✋ §a已移除共建人 §f%s");
        put("success.trust_removed_not", "§e⚠ §f%s §7不是共建人");
        put("success.trust_batch", "§b🤝 §a批量添加 §f%s §a人为庄园 §f#%s §a的共建人");
        put("success.member_added", "§b👤 §f%s §a已成为成员 §7(上级在线时可建造)");
        put("success.member_added_already", "§e⚠ §f%s §7已经是成员了");
        put("success.member_removed", "§c👤 §a已移除成员 §f%s");
        put("success.member_removed_not", "§e⚠ §f%s §7不是成员");
        put("success.denied_added", "§c🚫 §f%s §a已加入庄园 §f#%s §a黑名单");
        put("success.denied_already", "§e⚠ §f%s §7已在黑名单中");
        put("success.denied_removed", "§a✓ §f%s §a已从黑名单移除");
        put("success.denied_removed_not", "§e⚠ §f%s §7不在黑名单中");
        put("success.denied_batch", "§c🚫 §a批量拉黑 §f%s §a人（庄园 §f#%s§a）");
        put("success.flag_set", "§e⚙ §f%s §7= §a%s §8(庄园 #%s)");
        put("success.flag_unset", "§e⚙ §f%s §7已重置为默认 §8(%s)");
        put("success.alias_set", "§e✎ §a庄园别名: §f%s");
        put("success.alias_cleared", "§e✎ §7已清除庄园别名");
        put("success.home_set", "§d⌂ §a传送点已设为 §f%s, %s, %s");
        put("success.done_on", "§a✔ §a庄园已标记为 §f完工");
        put("success.done_off", "§e🔨 §7已取消完工标记（建造中）");
        put("success.kicked", "§c⏎ §a已将 §f%s §a踢出你的庄园");
        put("success.kicked_notify", "§c⚠ §7你被 §f%s §7从庄园 §f#%s §7踢出了");
        put("success.cleared", "§6✦ §a庄园 §f#%s §a地表已清空 §7— 整地进行中...");
        put("success.rated", "§6★ §a评分成功！庄园 §f#%s §7: §e%s §7分 §8(平均 §e%s§8, §f%s §8人评)");
        put("success.merged", "§6⊕ §a庄园 §f#%s §a已并入 §f#%s §7— 路 chunk 归属完成");
        put("success.unmerged_one", "§6⊖ §a庄园 §f#%s §a合并已取消 §7— 路 chunk 已恢复");
        put("success.unmerged_all", "§6⊖ §a已取消 §f#%s §a的全部合并 §8(共 %s 块)");
        put("success.desc_set", "§e✎ §a描述: §f%s");
        put("success.desc_cleared", "§e✎ §7已清除庄园描述");
        put("success.regen", "§6✦ §a庄园 §f#%s §a地形已重置 §7— 整地进行中...");
        put("success.export", "§6📦 §a导出完成: §f%s");
        put("success.reload", "§6♻ §a配置已重载 §7(部分设置需重启)");
        put("success.setowner", "§6👑 §a庄园 §f#%s §a庄主已转移给 §f%s");
        put("success.purge", "§6✦ §a已清理 §f%s §a块闲置庄园 §8(超过 %s 天未登录)");
        put("success.fund_added", "§6💰 §f%s §a充值 §e%s §7(余额 §f%s§7)");
        put("success.fund_set", "§6💰 §f%s §a资金已设为 §e%s");
        put("success.bulletin_set", "§6📢 §a公告: §f%s");
        put("success.bulletin_cleared", "§6📢 §7公告已清除");
        put("success.create_world", "§6✦ §a公会营地已创建 §f%s §8| §7种子=§f%s §8| §7偏移=§f(%s,%s)");
        put("success.claim", "§6✦ §a庄园 §f#%s §a已分配 §8(等级 %s) §7— 整地进行中，传送中...");
        put("success.claim_hint", "§8▸ §7提示: 一人一块，再领需 /gs admin fill");
        put("success.upgrade_guild", "§6⬆ §a公会升级！§f%s §7/ §f%s §a级 §8| §7名额 §f%s→%s §8| §7边界 §f%s→%s§a方块");
        put("success.delete", "§6✦ §a已卸载 §f%s §8(世界文件夹需手动清理)");
        put("success.fill", "§6✦ §a已为 §f%s §a填充 §f%s §a块测试庄园");
        put("success.fill_full", "§e⚠ §a已达上限 §f%s §a块 §8(已填 %s) §7— 先升级公会");
        put("success.map", "§6✦ §a网格图已输出到控制台");
        put("success.swap", "§6⇄ §a互换完成 §f#%s §7↔ §f#%s §8(与 %s)");
        put("success.swap_notify", "§6⇄ §f%s §7与你互换了庄园 §f#%s §7↔ §f#%s");
        put("success.grant", "§6✦ §a已为 §f%s §a分配新庄园");
        put("success.template_created", "§6✦ §a模板 §f%s §a已创建 §7— /gs template setflag %s <flag> <值>");
        put("success.template_deleted", "§6✦ §a模板 §f%s §a已删除");
        put("success.template_flag_set", "§e⚙ §7模板 §f%s§7: %s §7= §a%s");
        put("success.template_applied", "§6✦ §a模板 §f%s §a已应用到庄园 §f#%s §8(%s 个 flag)");
        put("success.sub_created", "§6✦ §a子领地 §f%s §a已创建 §8(%sx%s)");
        put("success.sub_deleted", "§6✦ §a子领地 §f%s §a已删除");
        put("success.sub_flag_set", "§e⚙ §7子领地 §f%s§7: %s §7= §a%s");
        put("success.titles_on", "§a✔ §a进出标题已开启");
        put("success.titles_off", "§e○ §7进出标题已关闭（改为聊天框）");
        put("success.comment_added", "§6✎ §a留言已发送到庄园 §f#%s");
        put("success.plot_opened", "§6🔓 §a庄园已临时开放 §f%s §7— 访客可自由进出");
        put("success.plot_closed", "§6🔒 §a庄园已关闭访客模式");
        put("error.plot_not_open", "§e⚠ §7你的庄园当前未开放");
        put("success.flower_sent", "§d🌸 §a你给 §f%s §a送了一朵花 §8(今日第 %s 朵)");
        put("success.flower_received", "§d🌸 §f%s §a给你的庄园 §f%s §a送了一朵花！");
        put("error.cannot_flower_self", "§e⚠ §7不能给自己的庄园送花");
        put("error.already_flowered_today", "§e⚠ §7你今天已经给这块庄园送过花了");
        put("success.gift_sent", "§6🎁 §a已送出 §f%s §a个 §f%s §a给 §f%s");
        put("success.gift_received", "§6🎁 §f%s §a送给你 §f%s §a个 §f%s");
        put("error.not_same_world", "§e⚠ §7你们不在同一个世界");
        put("error.no_item_in_hand", "§e⚠ §7你手上没有物品");
        put("error.no_worldedit", "§c⚠ §7未检测到 WorldEdit/FAWE，此功能不可用");
        put("error.template_save_failed", "§c⚠ §7保存 schematic 失败");
        put("success.template_saved", "§6✦ §a建筑模板 §f%s §a已保存 §7(从你当前庄园范围)");
        put("success.template_pasted", "§6✦ §a建筑模板 §f%s §a已粘贴到你的庄园");
        put("info.no_schematics", "§e⚠ §7还没有保存任何建筑模板");
        put("info.schematics_header", "§6§l━━ §e§l📐 建筑模板 §6§l━━━━━━━━━━━━");
        put("info.schematics_entry", "§6▸ §f%s");
        put("usage.template_save", "§e用法: /gs template save <名称> §7(把庄园存为建筑模板)");
        put("usage.template_paste", "§e用法: /gs template paste <名称> §7(把模板粘贴到庄园)");
        put("usage.open", "§e用法: /gs open [分钟数] §7(0=永久, 默认60分钟)");
        put("usage.close", "§e用法: /gs close");
        put("usage.flower", "§e用法: /gs flower [公会名 slot号]");
        put("usage.gift", "§e用法: /gs gift <玩家名>");
        put("success.welcome", "§6§l━━━━━ §e§l欢迎来到公会营地 §6§l━━━━━━\n§7你被分配到 §f%s §7的庄园 §f#%s\n\n§7  §e/gs home §8» §7传送到你的庄园\n§7  §e/gs help  §8» §7查看所有命令\n§6§l━━━━━━━━━━━━━━━━━━━━━━━");
        put("success.cross_server", "§6✦ §a正在传送到 §f%s §a...");
        put("success.manor_moved", "§6✦ §a搬家完成！§f%s §7→ §f%s §8(slot #%s)§a\n§7建筑已复制，旧位置已清空。");
        put("success.move_cost_deducted", "§6💰 §7搬家费用 §e%s §7已扣除。");

        // === 信息 ===
        put("info.no_guilds", "§e还没有任何公会营地。");
        put("info.guild_list_header", "§6==== %s公会营地 (%s) ====");
        put("info.guild_list_entry", "§7- §f%s §7Lv%s 成员 %s/%s §8(/gs visit %s)");
        put("info.near_header", "§6==== 附近庄园 ====");
        put("info.near_entry", "§7- §f%s §7庄主: §f%s §7距离: §e%s 格");
        put("info.top_header", "§6==== %s 庄园%s排行 ====");
        put("info.top_entry", "§e%s. §f%s §7庄主: §f%s §7%s: §e%s");
        put("info.inbox_header", "§6==== 收件箱（最近 20 条）====");
        put("info.inbox_entry", "§7[%s] §f%s §7→ 庄园#%s: §f%s");
        put("info.template_header", "§6==== 权限模板 ====");
        put("info.template_entry", "§7- §f%s §7(%s 个 flag)");
        put("info.sub_header", "§6==== 子领地列表 ====");
        put("info.sub_entry", "§7- §f%s §7(%sx%s) §7flags: %s");
        put("info.world_list", "§e已加载世界 (%s):");
        put("info.fund_check", "§7公会 §f%s §7资金: §e%s");
        put("info.bulletin_empty", "§e该公会还没有公告。");
        put("info.bulletin_show", "§6[§e%s§6] §7%s");
        put("info.world_entry", "§7- §f%s §7env=%s spawn=(%s,%s,%s)");
        put("info.whereami", "§e你在世界 §f%s §7(%s,%s,%s)");
        put("info.help_header", "§6==== GuildShelter 命令 ====");
        put("info.help_entry", "§e/gs %s §7- %s");
        put("info.help_footer", "§8输入 /gs help <命令> 查看详细用法");
        put("info.help_cmd", "§6/gs %s §7- %s");
        put("info.help_unknown", "§c未知命令: %s");
        put("info.flag_header", "§6==== 庄园 #%s Flag ====");
        put("info.guild_info_header", "§6==== 公会营地信息 ====");
        put("info.guild_line", "§7公会: §f%s §7(Lv%s/%s, 成员 %s/%s)");
        put("info.plot_line", "§7你的庄园: §f%s §7庄园 Lv%s/%s §7尺寸 %sx%s%s");
        put("info.trusted_line", "§7共建人(trusted): §f%s §7成员(member): §f%s §7黑名单: §c%s");
        put("info.blocked_cmds_line", "§7禁用命令: §c/%s");
        put("info.keep_line", "§7退会保留: §a是");
        put("info.list_entry", "§7- §f%s §7Lv%s 成员 %s/%s §8(/gs visit %s)");
        put("info.near_entry", "§7- §f%s §7庄主: §f%s §7距离: §e%s 格");
        put("info.top_entry", "§e%s. §f%s §7庄主: §f%s §7%s: §e%s");
        put("info.inbox_entry", "§7[%s] §f%s §7→ 庄园#%s: §f%s");
        put("info.template_entry", "§7- §f%s §7(%s 个 flag)");
        put("info.sub_entry", "§7- §f%s §7(%sx%s) §7flags: %s");
        put("info.flag_usage", "§7用法: /gs flag set <flag> <值> | unset <flag>");
        put("info.flag_entry", "§7%s = %s §8- %s");
        put("info.flag_value_set", "§f%s");
        put("info.flag_value_default", "§8%s(默认)");
        put("info.card_header", "§6§l┌── §e§l🏡 家园卡 §6§l────────────────────");
        put("info.card_plot", "§6│ §7庄园 §f%s §8· §7公会 §f%s §8· §7等级 §f%s%s");
        put("info.card_owner", "§6│ §7庄主 §f%s §8· §7庄园§fLv %s§7/§f%s §8· §7尺寸§f %sx%s");
        put("info.card_alias", "§6│ §7别名 §f%s");
        put("info.card_desc", "§6│ §7描述 §f%s");
        put("info.card_entities", "§6│ §7实体 %s");
        put("info.card_members", "§6│ §7成员§f %s§7/§f%s §8· §7共建§f %s §8· §7黑名单§c %s");
        put("info.card_flags", "§6│ §7活跃flag§f %s §7个");
        put("info.card_price", "§6│ §7入场费§e %s");
        put("info.card_score", "§6│ §7活跃flag§f %s §7个");
        put("info.card_footer", "§6│");
        put("info.card_score_line", "§6│ §e⭐ 综合评分 §a§l%s §7分");
        put("info.card_visits", "§6│ §7👁 访问 §e%s §7次");
        put("info.card_bottom", "§6§l└────────────────────────────────");
        put("info.done_status", "§a✔ 已完工");
        put("info.building_status", "§e🔨 建造中");
        put("info.no_alias", "§8无");
        put("info.entity_world_not_loaded", "§8(世界未加载)");
        put("info.entity_line", "§a%s §7动物 §c%s §7敌对 §b%s §7其它 §6%s §7载具");
        put("info.board_header", "§6§l┌── §e§l📝 留言墙 §f%s §6§l────────────────");
        put("info.board_entry", "§6│ §8[%s] §f%s§7: %s");
        put("info.board_footer", "§6§l└────────────────────────────────────");
        put("info.board_empty", "§e⚠ §7这块庄园还没有留言");

        // === 帮助用法 ===
        // --- /gs help 分类标题（沿用原有 §6==== 标题 ==== 风格）---
        put("info.help_cat_teleport", "§6==== 传送 ====");
        put("info.help_cat_info", "§6==== 庄园信息 ====");
        put("info.help_cat_people", "§6==== 人员管理 ====");
        put("info.help_cat_settings", "§6==== 庄园设置 ====");
        put("info.help_cat_social", "§6==== 社交 ====");
        put("info.help_cat_advanced", "§6==== 高级操作 ====");
        put("info.help_cat_admin", "§c==== 管理员命令 ====");
        // --- /gs help 各命令行（沿用 §e/gs %s §7- %s 风格）---
        put("info.help_ln_home", "§e/gs home §7- 传送到自己庄园");
        put("info.help_ln_spawn", "§e/gs spawn §7- 传送到公会主城");
        put("info.help_ln_middle", "§e/gs middle §7- 传送到庄园正中心");
        put("info.help_ln_sethome", "§e/gs sethome §7- 设置 home 传送点");
        put("info.help_ln_visit", "§e/gs visit <公会> §7- 到访公会主城");
        put("info.help_ln_info", "§e/gs info §7- 查看庄园+公会信息");
        put("info.help_ln_card", "§e/gs card [玩家] §7- 查看庄园档案卡");
        put("info.help_ln_near", "§e/gs near §7- 列出附近庄园");
        put("info.help_ln_list", "§e/gs list [mine] §7- 列出公会营地");
        put("info.help_ln_board", "§e/gs board §7- 查看留言墙");
        put("info.help_ln_top", "§e/gs top [公会] [排序] §7- 排行榜");
        put("info.help_ln_trust", "§e/gs trust <玩家|*> §7- 加共建人");
        put("info.help_ln_untrust", "§e/gs untrust <玩家> §7- 移除共建人");
        put("info.help_ln_member", "§e/gs member <add|remove> <玩家> §7- 管理成员");
        put("info.help_ln_deny", "§e/gs deny <玩家|*> §7- 拉黑");
        put("info.help_ln_undeny", "§e/gs undeny <玩家> §7- 移出黑名单");
        put("info.help_ln_kick", "§e/gs kick <玩家> §7- 踢出非成员");
        put("info.help_ln_flag", "§e/gs flag §7- 查看/设置 flag");
        put("info.help_ln_flag_set", "§e/gs flag set <flag> <值> §7- 设置 flag");
        put("info.help_ln_alias", "§e/gs alias <名称> §7- 设置别名");
        put("info.help_ln_desc", "§e/gs desc <描述> §7- 设置描述");
        put("info.help_ln_done", "§e/gs done §7- 切换完工标记");
        put("info.help_ln_toggle", "§e/gs toggle titles §7- 开关进出标题");
        put("info.help_ln_open", "§e/gs open [分钟] §7- 临时开放庄园");
        put("info.help_ln_close", "§e/gs close §7- 关闭访客模式");
        put("info.help_ln_comment", "§e/gs comment <留言> §7- 给庄园留言");
        put("info.help_ln_inbox", "§e/gs inbox §7- 查看收到的留言");
        put("info.help_ln_rate", "§e/gs rate <1-10> [公会 slot] §7- 给庄园打分");
        put("info.help_ln_flower", "§e/gs flower [公会 slot] §7- 给庄园送花");
        put("info.help_ln_gift", "§e/gs gift <玩家> §7- 送出手持物品");
        put("info.help_ln_bulletin", "§e/gs bulletin <set|show|clear> §7- 公告板");
        put("info.help_ln_upgrade", "§e/gs upgrade §7- 升级庄园");
        put("info.help_ln_clear", "§e/gs clear §7- 清空地表（需确认）");
        put("info.help_ln_swap", "§e/gs swap <玩家> §7- 互换 slot（需确认）");
        put("info.help_ln_merge", "§e/gs merge <slot> §7- 合并相邻庄园（需确认）");
        put("info.help_ln_unmerge", "§e/gs unmerge [slot] §7- 取消合并（需确认）");
        put("info.help_ln_move", "§e/gs move <公会> §7- 搬家（需确认）");
        put("info.help_ln_template", "§e/gs template §7- 权限模板管理");
        put("info.help_ln_sub", "§e/gs sub §7- 子领地管理");
        put("info.help_ln_confirm", "§e/gs confirm §7- 确认危险操作");
        put("info.help_ln_admin_create", "§c/gs admin create <公会> [地形] §7- 创建公会营地");
        put("info.help_ln_admin_tp", "§c/gs admin tp <公会> §7- 传送到公会主城");
        put("info.help_ln_admin_claim", "§c/gs admin claim <公会> §7- 给自己分配庄园");
        put("info.help_ln_admin_fill", "§c/gs admin fill <公会> <数量> §7- 批量填充测试庄园");
        put("info.help_ln_admin_map", "§c/gs admin map <公会> §7- 输出网格图");
        put("info.help_ln_admin_upgrade_manor", "§c/gs admin upgrade-manor <公会> §7- 升级庄园");
        put("info.help_ln_admin_upgrade_guild", "§c/gs admin upgrade-guild <公会> §7- 升级公会");
        put("info.help_ln_admin_delete", "§c/gs admin delete <公会> §7- 卸载公会营地");
        put("info.help_ln_admin_worlds", "§c/gs admin worlds §7- 列出已加载世界");
        put("info.help_ln_admin_whereami", "§c/gs admin whereami §7- 显示当前坐标");
        put("info.help_ln_admin_reload", "§c/gs admin reload §7- 热重载配置");
        put("info.help_ln_admin_setowner", "§c/gs admin setowner <公会> <玩家> §7- 转移所有权");
        put("info.help_ln_admin_purge", "§c/gs admin purge <天数> [公会] §7- 清理闲置庄园");
        put("info.help_ln_admin_regen", "§c/gs admin regen §7- 重置脚下庄园地形");
        put("info.help_ln_admin_export", "§c/gs admin export §7- 导出数据到 CSV");
        put("info.help_ln_admin_fund", "§c/gs admin fund <公会> <操作> [金额] §7- 管理资金");
        put("info.help_ln_admin_citywall", "§c/gs admin citywall <公会> §7- 补建主城围墙");

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
        put("usage.merge", "用法: /gs merge <slot号>（把该 slot 合并到你的庄园）");
        put("usage.move", "§e用法: /gs move <公会名> §7(搬家到另一个公会，保留建筑)");
        put("usage.unmerge", "用法: /gs unmerge [slot]");
        put("usage.template", "§e用法: /gs template <create|delete|apply|setflag|list> [名称] [参数]");
        put("usage.template_create", "用法: /gs template create <名称>");
        put("usage.template_delete", "用法: /gs template delete <名称>");
        put("usage.template_setflag", "用法: /gs template setflag <模板名> <flag> <值>");
        put("usage.template_apply", "用法: /gs apply <模板名>（把模板配置应用到当前庄园）");
        put("usage.sub", "§e用法: /gs sub <create|delete|setflag|list> [名称] [参数]");
        put("usage.sub_create", "用法: /gs sub create <名称> <dx1> <dz1> <dx2> <dz2>");
        put("usage.sub_create_hint", "  dx/dz 是相对于你当前位置的偏移（方块），如 -10 0 10 10");
        put("usage.sub_delete", "用法: /gs sub delete <名称>");
        put("usage.sub_setflag", "用法: /gs sub setflag <子领地名> <flag> <值>");
        put("usage.toggle", "用法: /gs toggle titles");
        put("usage.admin", "用法: /gs admin <create|tp|claim|fill|map|upgrade-manor|upgrade-guild|delete|worlds|whereami|reload|setowner|purge|regen|export> [公会id]");
        put("usage.admin_create", "用法: /gs admin create <公会id> [地形: NONE|CLEAR_VEGETATION|FLATTEN|VOID|FLAT]");
        put("usage.admin_tp", "用法: /gs admin tp <公会id>");
        put("usage.admin_claim", "用法: /gs admin claim <公会id>");
        put("usage.admin_fill", "用法: /gs admin fill <公会id> <数量>");
        put("usage.admin_map", "用法: /gs admin map <公会id>");
        put("usage.admin_upgrade_manor", "用法: /gs admin upgrade-manor <公会id>");
        put("usage.admin_upgrade_guild", "用法: /gs admin upgrade-guild <公会id>");
        put("usage.admin_delete", "用法: /gs admin delete <公会id>");
        put("usage.admin_setowner", "用法: /gs admin setowner <公会id> <玩家>");
        put("usage.admin_purge", "用法: /gs admin purge <天数> [公会id]");
        put("usage.admin_fund", "用法: /gs admin fund <公会> <add|check|set> [金额]");
        put("usage.bulletin", "用法: /gs bulletin <set|show|clear> [内容]");
        put("usage.player_commands", "§e/gs <home|spawn|upgrade|info|trust|untrust|member|deny|undeny|list|visit|clear|flag|card|alias|sethome|done|kick|near|rate|top|middle|comment|inbox|swap|grant|merge|unmerge>  §7玩家命令");
        put("usage.admin_commands", "§7/gs admin ...  §8管理命令");

        // === MOTD ===
        put("motd.format", "§6[§e%s§6] §7%s");

        // === 监听器 ===
        put("listener.blacklisted", "§c🚫 §7你在这块庄园的黑名单中，无法进入");
        put("listener.deny_entry", "§c🔒 §7这块庄园谢绝访客");
        put("listener.deny_exit", "§c🔒 §7你被困在这块庄园里了");
        put("listener.cmd_blocked", "§c🚫 §7此庄园禁止使用 §f/%s");
        put("listener.price_charged", "§6💰 §7入场费 §e%s §7已扣除 §8(庄园 #%s)");
        put("listener.price_no_money", "§c💰 §7进入需要 §e%s §7，余额不足");
        put("listener.build_denied", "§c⚠ §7这里不是你能改动的区域");
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

    /** 获取消息，%s 等占位符由调用方替换。key 不存在时返回 key 本身。无参数时跳过 String.format。 */
    public static String get(String key, Object... args) {
        String msg = messages.getOrDefault(key, key);
        if (args.length == 0) {
            return msg; // 快速路径：无参数，跳过 format
        }
        try {
            return String.format(msg, args);
        } catch (Exception ignored) {
            return msg;
        }
    }

    public static String lang() {
        return currentLang;
    }
}
