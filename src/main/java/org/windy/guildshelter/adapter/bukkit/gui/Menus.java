package org.windy.guildshelter.adapter.bukkit.gui;

import org.bukkit.Material;
import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.rule.LevelRules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预置菜单构造器：创建各种管理菜单的 GuiSpec。
 */
public final class Menus {

    private Menus() {}

    /** 地皮信息面板。 */
    public static GuiSpec manorInfo(Manor manor, GuildWorld gw, LevelRules levels) {
        Map<Integer, GuiItem> items = new HashMap<>();
        int slot = 0;

        // 基本信息
        items.put(slot++, GuiItem.of(Material.BOOK,
                "§6地皮 #" + manor.slot(),
                List.of("§7公会: §f" + manor.guild().value(),
                        "§7等级: §f" + manor.level() + "/" + levels.manorMaxLevel(),
                        "§7庄主: §f" + manor.owner()),
                ""));

        // Flag 编辑按钮
        items.put(slot++, GuiItem.of(Material.REDSTONE_TORCH,
                "§e编辑 Flag",
                List.of("§7点击打开 flag 编辑器"),
                "menu.flags"));

        // 成员管理按钮
        items.put(slot++, GuiItem.of(Material.PLAYER_HEAD,
                "§e成员管理",
                List.of("§7trusted: §f" + manor.coBuilders().size(),
                        "§7member: §f" + manor.members().size(),
                        "§7denied: §c" + manor.denied().size()),
                "menu.members"));

        // 模板按钮
        items.put(slot++, GuiItem.of(Material.WRITABLE_BOOK,
                "§e权限模板",
                List.of("§7点击管理权限模板"),
                "menu.templates"));

        // 子领地按钮
        items.put(slot++, GuiItem.of(Material.MAP,
                "§e子领地",
                List.of("§7点击管理子领地"),
                "menu.subs"));

        // 分隔线
        for (int i = slot; i < 9; i++) {
            items.put(i, GuiItem.separator(Material.GRAY_STAINED_GLASS_PANE));
        }

        // 描述
        String desc = Flag.DESCRIPTION.resolveString(manor.flags());
        if (!desc.isBlank()) {
            items.put(9, GuiItem.of(Material.PAPER, "§7描述: §f" + desc, List.of(), ""));
        }

        // 入场费
        double price = Flag.PRICE.resolveDouble(manor.flags());
        if (price > 0) {
            items.put(10, GuiItem.of(Material.GOLD_INGOT, "§7入场费: §e" + price, List.of(), ""));
        }

        return new GuiSpec("manor_info", "§8[§6地皮管理§8] §7#" + manor.slot(), 3, items,
                Map.of("manor", manor, "guildWorld", gw));
    }

    /** Flag 编辑器（按类别分页）。 */
    public static GuiSpec flagEditor(Manor manor, int page) {
        Flag[] flags = Flag.values();
        int perPage = 27; // 3 行
        int start = page * perPage;
        int end = Math.min(start + perPage, flags.length);
        int maxPage = (flags.length + perPage - 1) / perPage - 1;

        Map<Integer, GuiItem> items = new HashMap<>();
        int slot = 0;
        for (int i = start; i < end; i++) {
            Flag f = flags[i];
            String current = manor.flags().getOrDefault(f.id(), f.defaultValue() + " §8(默认)");
            boolean isDefault = !manor.flags().containsKey(f.id());
            items.put(slot++, GuiItem.of(
                    f.type() == org.windy.guildshelter.domain.flag.FlagType.BOOLEAN ? Material.LEVER : Material.PAPER,
                    "§f" + f.id() + " §7= " + (isDefault ? "§8" + current : "§a" + current),
                    List.of("§8" + f.description(), "§7类型: " + f.type(), "", "§e左键: true  §c右键: false  §7中键: 重置"),
                    "flag.toggle." + f.id()));
        }

        // 翻页按钮
        if (page > 0) items.put(27, GuiItem.of(Material.ARROW, "§e上一页", "menu.flags.page." + (page - 1)));
        if (page < maxPage) items.put(35, GuiItem.of(Material.ARROW, "§e下一页", "menu.flags.page." + (page + 1)));

        // 返回按钮
        items.put(31, GuiItem.of(Material.BARRIER, "§c返回", "menu.info"));

        return new GuiSpec("flag_editor", "§8[§6Flag 编辑器§8] §7页 " + (page + 1) + "/" + (maxPage + 1), 4, items,
                Map.of("manor", manor, "page", page));
    }

    /** 成员管理面板。 */
    public static GuiSpec memberManager(Manor manor) {
        Map<Integer, GuiItem> items = new HashMap<>();
        int slot = 0;

        // Owner
        items.put(slot++, GuiItem.of(Material.GOLDEN_HELMET, "§6庄主", List.of("§f" + manor.owner()), ""));

        // Trusted
        items.put(slot++, GuiItem.of(Material.DIAMOND_HELMET, "§b共建人 (" + manor.coBuilders().size() + ")",
                manor.coBuilders().stream().map(r -> "§7- §f" + r).toList(), "menu.members.trusted"));

        // Members
        items.put(slot++, GuiItem.of(Material.IRON_HELMET, "§a成员 (" + manor.members().size() + ")",
                manor.members().stream().map(r -> "§7- §f" + r).toList(), "menu.members.members"));

        // Denied
        items.put(slot++, GuiItem.of(Material.REDSTONE_BLOCK, "§c黑名单 (" + manor.denied().size() + ")",
                manor.denied().stream().map(r -> "§7- §f" + r).toList(), "menu.members.denied"));

        // 返回
        items.put(8, GuiItem.of(Material.BARRIER, "§c返回", "menu.info"));

        return new GuiSpec("member_manager", "§8[§6成员管理§8]", 3, items, Map.of("manor", manor));
    }
}
