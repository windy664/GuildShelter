package org.windy.guildshelter.adapter.bukkit.gui;

import org.windy.guildshelter.GuildShelterPlugin;
import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.port.ui.UiIcon;
import org.windy.guildshelter.domain.port.ui.UiItem;
import org.windy.guildshelter.domain.port.ui.UiView;
import org.windy.guildshelter.domain.rule.LevelRules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预置菜单构造器：创建各种管理菜单的 {@link UiView}。
 * 优先从 gui.yml 加载（服主可自定义），缺失时用硬编码默认值。
 *
 * <p>平台中立——图标用 {@link UiIcon}（物品名字符串），不引用 {@code org.bukkit.Material}，
 * 由具体后端翻译。<b>注：尚无命令调用这些构造器，GUI 入口待接</b>（见 {@code UiActionRouter}）。
 */
public final class Menus {

    private Menus() {}

    /**
     * 尝试从 YAML 加载菜单，未定义则返回 null（调用方 fallback 到硬编码）。
     */
    public static UiView fromYaml(String menuId, Map<String, Object> context) {
        YamlGuiLoader loader = GuildShelterPlugin.guiLoader();
        return loader != null ? loader.loadMenu(menuId, context) : null;
    }

    /** 庄园信息面板（YAML 优先，硬编码兜底）。 */
    public static UiView manorInfoYaml(Manor manor, GuildWorld gw, LevelRules levels) {
        Map<String, Object> ctx = Map.of("manor", manor, "guildWorld", gw);
        UiView yaml = fromYaml("manor_info", ctx);
        if (yaml != null) return yaml;
        return manorInfo(manor, gw, levels);
    }

    /** 成员管理面板（YAML 优先，硬编码兜底）。 */
    public static UiView memberManagerYaml(Manor manor) {
        Map<String, Object> ctx = Map.of("manor", manor);
        UiView yaml = fromYaml("member_manager", ctx);
        if (yaml != null) return yaml;
        return memberManager(manor);
    }

    /** 庄园信息面板。 */
    public static UiView manorInfo(Manor manor, GuildWorld gw, LevelRules levels) {
        Map<Integer, UiItem> items = new HashMap<>();
        int slot = 0;

        // 基本信息
        items.put(slot++, UiItem.of(UiIcon.of("book"),
                "§6庄园 #" + manor.slot(),
                List.of("§7公会: §f" + manor.guild().value(),
                        "§7等级: §f" + manor.level() + "/" + levels.manorMaxLevel(),
                        "§7庄主: §f" + manor.owner()),
                ""));

        // Flag 编辑按钮
        items.put(slot++, UiItem.of(UiIcon.of("redstone_torch"),
                "§e编辑 Flag",
                List.of("§7点击打开 flag 编辑器"),
                "menu.flags"));

        // 成员管理按钮
        items.put(slot++, UiItem.of(UiIcon.of("player_head"),
                "§e成员管理",
                List.of("§7trusted: §f" + manor.coBuilders().size(),
                        "§7member: §f" + manor.members().size(),
                        "§7denied: §c" + manor.denied().size()),
                "menu.members"));

        // 模板按钮
        items.put(slot++, UiItem.of(UiIcon.of("writable_book"),
                "§e权限模板",
                List.of("§7点击管理权限模板"),
                "menu.templates"));

        // 子领地按钮
        items.put(slot++, UiItem.of(UiIcon.of("map"),
                "§e子领地",
                List.of("§7点击管理子领地"),
                "menu.subs"));

        // 分隔线
        for (int i = slot; i < 9; i++) {
            items.put(i, UiItem.separator(UiIcon.of("gray_stained_glass_pane")));
        }

        // 描述
        String desc = Flag.DESCRIPTION.resolveString(manor.flags());
        if (!desc.isBlank()) {
            items.put(9, UiItem.of(UiIcon.of("paper"), "§7描述: §f" + desc, List.of(), ""));
        }

        // 入场费
        double price = Flag.PRICE.resolveDouble(manor.flags());
        if (price > 0) {
            items.put(10, UiItem.of(UiIcon.of("gold_ingot"), "§7入场费: §e" + price, List.of(), ""));
        }

        return new UiView("manor_info", "§8[§6庄园管理§8] §7#" + manor.slot(), 3, items,
                Map.of("manor", manor, "guildWorld", gw));
    }

    /** Flag 编辑器（按类别分页）。 */
    public static UiView flagEditor(Manor manor, int page) {
        Flag[] flags = Flag.values();
        int perPage = 27; // 3 行
        int start = page * perPage;
        int end = Math.min(start + perPage, flags.length);
        int maxPage = (flags.length + perPage - 1) / perPage - 1;

        Map<Integer, UiItem> items = new HashMap<>();
        int slot = 0;
        for (int i = start; i < end; i++) {
            Flag f = flags[i];
            String current = manor.flags().getOrDefault(f.id(), f.defaultValue() + " §8(默认)");
            boolean isDefault = !manor.flags().containsKey(f.id());
            items.put(slot++, UiItem.of(
                    UiIcon.of(f.type() == org.windy.guildshelter.domain.flag.FlagType.BOOLEAN ? "lever" : "paper"),
                    "§f" + f.id() + " §7= " + (isDefault ? "§8" + current : "§a" + current),
                    List.of("§8" + f.description(), "§7类型: " + f.type(), "", "§e左键: true  §c右键: false  §7中键: 重置"),
                    "flag.toggle." + f.id()));
        }

        // 翻页按钮
        if (page > 0) items.put(27, UiItem.of(UiIcon.of("arrow"), "§e上一页", "menu.flags.page." + (page - 1)));
        if (page < maxPage) items.put(35, UiItem.of(UiIcon.of("arrow"), "§e下一页", "menu.flags.page." + (page + 1)));

        // 返回按钮
        items.put(31, UiItem.of(UiIcon.of("barrier"), "§c返回", "menu.info"));

        return new UiView("flag_editor", "§8[§6Flag 编辑器§8] §7页 " + (page + 1) + "/" + (maxPage + 1), 4, items,
                Map.of("manor", manor, "page", page));
    }

    /** 成员管理面板。 */
    public static UiView memberManager(Manor manor) {
        Map<Integer, UiItem> items = new HashMap<>();
        int slot = 0;

        // Owner
        items.put(slot++, UiItem.of(UiIcon.of("golden_helmet"), "§6庄主", List.of("§f" + manor.owner()), ""));

        // Trusted
        items.put(slot++, UiItem.of(UiIcon.of("diamond_helmet"), "§b共建人 (" + manor.coBuilders().size() + ")",
                manor.coBuilders().stream().map(r -> "§7- §f" + r).toList(), "menu.members.trusted"));

        // Members
        items.put(slot++, UiItem.of(UiIcon.of("iron_helmet"), "§a成员 (" + manor.members().size() + ")",
                manor.members().stream().map(r -> "§7- §f" + r).toList(), "menu.members.members"));

        // Denied
        items.put(slot++, UiItem.of(UiIcon.of("redstone_block"), "§c黑名单 (" + manor.denied().size() + ")",
                manor.denied().stream().map(r -> "§7- §f" + r).toList(), "menu.members.denied"));

        // 返回
        items.put(8, UiItem.of(UiIcon.of("barrier"), "§c返回", "menu.info"));

        return new UiView("member_manager", "§8[§6成员管理§8]", 3, items, Map.of("manor", manor));
    }
}
