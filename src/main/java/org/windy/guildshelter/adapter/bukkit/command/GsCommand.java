package org.windy.guildshelter.adapter.bukkit.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.windy.guildshelter.adapter.bukkit.GridAsciiMap;
import org.windy.guildshelter.adapter.bukkit.GuildWorldRegistry;
import org.windy.guildshelter.adapter.bukkit.ManorEntityCensus;
import org.windy.guildshelter.adapter.bukkit.ManorRoles;
import org.windy.guildshelter.adapter.bukkit.MergeAwareClassifier;
import org.windy.guildshelter.adapter.bukkit.Permissions;
import org.windy.guildshelter.adapter.bukkit.world.WorldManager;
import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.flag.FlagType;
import org.windy.guildshelter.domain.layout.Classification;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;
import org.windy.guildshelter.domain.rule.LevelRules;
import org.windy.guildshelter.service.GuildFullException;
import org.windy.guildshelter.service.GuildService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * /gs 命令分发（验证 + 管理）。
 *
 * <p>admin 子命令：create 建公会世界、tp 进主城、claim 给自己分配一块地皮（自动整地）、
 * delete 卸载、worlds/whereami 诊断。普通玩家命令将在接入 GuildProvider 后补全。
 */
public final class GsCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERM = Permissions.ADMIN;
    /** 受 guildshelter.command.<sub> 节点管控的玩家子命令（节点默认放行，见 plugin.yml）。 */
    private static final Set<String> PLAYER_SUBS = Set.of("home", "spawn", "upgrade", "info",
            "trust", "untrust", "member", "deny", "undeny", "list", "visit", "clear", "flag", "card",
            "alias", "sethome", "done", "kick", "near", "rate", "top", "middle",
            "comment", "inbox", "swap", "grant", "merge");

    private final WorldManager worlds;
    private final GuildRepository guilds;
    private final ManorRepository manors;
    private final GuildService service;
    private final GuildWorldRegistry registry;
    private final LevelRules levels;
    private final ManorEntityCensus census;
    private final Logger logger;

    public GsCommand(WorldManager worlds, GuildRepository guilds, ManorRepository manors,
                     GuildService service, GuildWorldRegistry registry,
                     LevelRules levels, ManorEntityCensus census, Logger logger) {
        this.worlds = worlds;
        this.guilds = guilds;
        this.manors = manors;
        this.service = service;
        this.registry = registry;
        this.levels = levels;
        this.census = census;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length >= 1 ? args[0].toLowerCase() : "";
        // 玩家子命令权限节点把关(默认放行)；admin 另由 ADMIN_PERM 管，未知子命令落到帮助。
        if (PLAYER_SUBS.contains(sub) && !sender.hasPermission("guildshelter.command." + sub)) {
            sender.sendMessage("§c你没有权限使用 /gs " + sub + "。");
            return true;
        }
        switch (sub) {
            case "home" -> { home(sender); return true; }
            case "spawn" -> { spawn(sender); return true; }
            case "upgrade" -> { upgrade(sender); return true; }
            case "info" -> { info(sender); return true; }
            case "trust" -> { trust(sender, args); return true; }
            case "untrust" -> { untrust(sender, args); return true; }
            case "member" -> { member(sender, args); return true; }
            case "deny" -> { deny(sender, args); return true; }
            case "undeny" -> { undeny(sender, args); return true; }
            case "list" -> { list(sender); return true; }
            case "visit" -> { visit(sender, args); return true; }
            case "clear" -> { clear(sender); return true; }
            case "flag" -> { flag(sender, args); return true; }
            case "card" -> { card(sender, args); return true; }
            case "alias" -> { alias(sender, args); return true; }
            case "sethome" -> { sethome(sender); return true; }
            case "done" -> { done(sender); return true; }
            case "kick" -> { kick(sender, args); return true; }
            case "near" -> { near(sender); return true; }
            case "rate" -> { rate(sender, args); return true; }
            case "top" -> { top(sender); return true; }
            case "middle" -> { middle(sender); return true; }
            case "comment" -> { comment(sender, args); return true; }
            case "inbox" -> { inbox(sender); return true; }
            case "swap" -> { swap(sender, args); return true; }
            case "grant" -> { grant(sender, args); return true; }
            case "merge" -> { mergeCmd(sender, args); return true; }
            case "admin" -> { /* 落到下面的管理分支 */ }
            default -> {
                sender.sendMessage("§e/gs <home|spawn|upgrade|info|trust|untrust|member|deny|undeny|list|visit|clear|flag|card|alias|sethome|done|kick|near|rate|top|middle|comment|inbox|swap|grant|merge>  §7玩家命令");
                sender.sendMessage("§7/gs admin ...  §8管理命令");
                return true;
            }
        }
        if (!sender.hasPermission(ADMIN_PERM) && !sender.isOp()) {
            sender.sendMessage("§c你没有权限。");
            return true;
        }
        String action = args.length >= 2 ? args[1].toLowerCase() : "";
        switch (action) {
            case "create" -> create(sender, args);
            case "tp" -> tp(sender, args);
            case "claim" -> claim(sender, args);
            case "fill" -> fill(sender, args);
            case "map" -> map(sender, args);
            case "upgrade-manor" -> upgradeManor(sender, args);
            case "upgrade-guild" -> upgradeGuild(sender, args);
            case "delete" -> delete(sender, args);
            case "worlds" -> listWorlds(sender);
            case "whereami" -> whereami(sender);
            default -> sender.sendMessage("用法: /gs admin <create|tp|claim|fill|map|upgrade-manor|upgrade-guild|delete|worlds|whereami> [公会id]");
        }
        return true;
    }

    // ===== 玩家命令（自管模式：以"拥有地皮"判定归属公会）=====

    /** /gs home：传送到自己地皮（优先用 sethome 坐标，否则实占中心）。 */
    private void home(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。加入有营地的公会后会自动分配。");
            return;
        }
        GuildWorld gw = ensureLoadedWorld(sender, manor.guild());
        if (gw == null) {
            return;
        }
        World world = org.bukkit.Bukkit.getWorld(gw.worldName());
        int hx = Flag.HOME_X.resolveInt(manor.flags());
        int hz = Flag.HOME_Z.resolveInt(manor.flags());
        int cx, cz, cy;
        if (hx != 0 || hz != 0) {
            // 有自定义 home 坐标
            cx = hx;
            cz = hz;
            cy = Flag.HOME_Y.resolveInt(manor.flags());
            if (cy == 0) {
                cy = world.getHighestBlockYAt(cx, cz) + 1;
            }
        } else {
            // 默认：实占中心
            ChunkRegion active = new LayoutCalculator(gw.layout())
                    .activeRegion(manor.slot(), manor.level())
                    .shift(gw.originChunkX(), gw.originChunkZ());
            cx = (active.minBlockX() + active.maxBlockX()) / 2;
            cz = (active.minBlockZ() + active.maxBlockZ()) / 2;
            world.loadChunk(cx >> 4, cz >> 4, true);
            cy = world.getHighestBlockYAt(cx, cz) + 1;
        }
        world.loadChunk(cx >> 4, cz >> 4, true);
        player.teleport(new Location(world, cx + 0.5, cy, cz + 0.5));
        sender.sendMessage("§a已回到你的地皮 #" + manor.slot() + "。");
    }

    /** /gs spawn：传送到自己公会的主城。 */
    private void spawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有加入任何有营地的公会。");
            return;
        }
        GuildWorld gw = ensureLoadedWorld(sender, manor.guild());
        if (gw == null) {
            return;
        }
        player.teleport(worlds.safeSpawn(org.bukkit.Bukkit.getWorld(gw.worldName()), gw));
        sender.sendMessage("§a已传送到公会主城。");
    }

    /** /gs upgrade：升级自己的庄园一级（只受物理满级限制，成员自己的事）。 */
    private void upgrade(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        Manor manor = manors.findByOwnerAnywhere(ref).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。");
            return;
        }
        int cap = levels.manorMaxLevel();
        if (service.upgradeManor(manor.guild(), ref)) {
            Manor now = manors.findByOwnerAnywhere(ref).orElse(manor);
            sender.sendMessage("§a庄园已升至 " + now.level() + " / " + cap + " 级，新扩范围整地进行中。");
        } else {
            sender.sendMessage("§e庄园已达满级 " + cap + "。");
        }
    }

    /** /gs info：显示自己的地皮 + 公会信息。 */
    private void info(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。");
            return;
        }
        GuildWorld gw = guilds.find(manor.guild()).orElse(null);
        if (gw == null) {
            sender.sendMessage("§c你的公会世界不存在。");
            return;
        }
        int side = gw.layout().plotChunksByLevel(manor.level()) * 16;
        int capacity = levels.maxMembers(gw.guildLevel());
        int members = manors.findAll(manor.guild()).size();
        sender.sendMessage("§6==== 公会营地信息 ====");
        String alias = Flag.ALIAS.resolveString(manor.flags());
        String title = alias.isBlank() ? "地皮 #" + manor.slot() : alias + " (#" + manor.slot() + ")";
        sender.sendMessage("§7公会: §f" + manor.guild().value() + " §7(Lv" + gw.guildLevel()
                + "/" + levels.maxGuildLevel() + ", 成员 " + members + "/" + capacity + ")");
        sender.sendMessage("§7你的地皮: §f" + title + " §7庄园 Lv" + manor.level()
                + "/" + levels.manorMaxLevel() + " §7尺寸 " + side + "×" + side
                + (Flag.DONE.resolveBool(manor.flags()) ? " §a✔ 已完工" : " §e🔨 建造中"));
        sender.sendMessage("§7共建人(trusted): §f" + sizeOrNone(manor.coBuilders())
                + " §7成员(member): §f" + sizeOrNone(manor.members())
                + " §7黑名单: §c" + sizeOrNone(manor.denied()));
        String desc = Flag.DESCRIPTION.resolveString(manor.flags());
        if (!desc.isBlank()) {
            sender.sendMessage("§7描述: §f" + desc);
        }
        double price = Flag.PRICE.resolveDouble(manor.flags());
        if (price > 0) {
            sender.sendMessage("§7入场费: §e" + price);
        }
        String blocked = Flag.BLOCKED_CMDS.resolveString(manor.flags());
        if (!blocked.isBlank()) {
            sender.sendMessage("§7禁用命令: §c/" + blocked.replace(",", " /"));
        }
        if (Flag.KEEP.resolveBool(manor.flags())) {
            sender.sendMessage("§7退会保留: §a是");
        }
    }

    private static String sizeOrNone(Set<PlayerRef> set) {
        return set.isEmpty() ? "§8无" : set.size() + " 人";
    }

    /** /gs trust <玩家|*>：给自己的地皮加共建人（* = 批量，需 trust.everyone 权限）。 */
    private void trust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /gs trust <玩家|*>");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。");
            return;
        }
        // 批量 trust *
        if (args[1].equals("*")) {
            if (!player.hasPermission(Permissions.TRUST_EVERYONE)
                    && !Permissions.hasAdminPerm(player, Permissions.ADMIN_TRUST_OTHER)) {
                sender.sendMessage("§c你没有权限批量 trust（需要 " + Permissions.TRUST_EVERYONE + "）。");
                return;
            }
            GuildId guild = manor.guild();
            int count = 0;
            for (Manor m : manors.findAll(guild)) {
                PlayerRef tref = m.owner();
                if (tref.equals(PlayerRef.of(player.getUniqueId()))) {
                    continue; // 跳过庄主自己
                }
                if (manor.coBuilders().contains(tref)) {
                    continue; // 已经是 trusted
                }
                Set<PlayerRef> co = new HashSet<>(manor.coBuilders());
                Set<PlayerRef> members = new HashSet<>(manor.members());
                Set<PlayerRef> denied = new HashSet<>(manor.denied());
                co.add(tref);
                members.remove(tref);
                denied.remove(tref);
                manor = manor.withCoBuilders(co).withMembers(members).withDenied(denied);
                count++;
            }
            if (count == 0) {
                sender.sendMessage("§e没有需要 trust 的人（所有人已是共建人或无其他成员）。");
                return;
            }
            manors.save(manor);
            sender.sendMessage("§a已批量把 " + count + " 人加为地皮 #" + manor.slot() + " 的共建人。");
            return;
        }
        org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage("§e不用把自己加进去。");
            return;
        }
        PlayerRef tref = PlayerRef.of(target.getUniqueId());
        Set<PlayerRef> co = new HashSet<>(manor.coBuilders());
        if (!co.add(tref)) {
            sender.sendMessage("§e" + args[1] + " 已经是共建人了。");
            return;
        }
        // 身份互斥：升为 trusted 时清除其 member/denied 身份。
        Set<PlayerRef> members = new HashSet<>(manor.members());
        Set<PlayerRef> denied = new HashSet<>(manor.denied());
        members.remove(tref);
        denied.remove(tref);
        manors.save(manor.withCoBuilders(co).withMembers(members).withDenied(denied));
        sender.sendMessage("§a已把 " + args[1] + " 加为地皮 #" + manor.slot() + " 的共建人。");
    }

    /** /gs untrust <玩家>：移除共建人。 */
    private void untrust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /gs untrust <玩家>");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。");
            return;
        }
        org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
        Set<PlayerRef> co = new HashSet<>(manor.coBuilders());
        if (!co.remove(PlayerRef.of(target.getUniqueId()))) {
            sender.sendMessage("§e" + args[1] + " 不是共建人。");
            return;
        }
        manors.save(manor.withCoBuilders(co));
        sender.sendMessage("§a已移除共建人 " + args[1] + "。");
    }

    /** /gs member <add|remove> <玩家>：管理受限成员（仅 owner/trusted 在线时才有建造/交互权）。 */
    private void member(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        if (args.length < 3 || (!args[1].equalsIgnoreCase("add") && !args[1].equalsIgnoreCase("remove"))) {
            sender.sendMessage("用法: /gs member <add|remove> <玩家>");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。");
            return;
        }
        org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[2]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage("§e不用把自己加进去。");
            return;
        }
        PlayerRef tref = PlayerRef.of(target.getUniqueId());
        Set<PlayerRef> members = new HashSet<>(manor.members());
        if (args[1].equalsIgnoreCase("add")) {
            if (!members.add(tref)) {
                sender.sendMessage("§e" + args[2] + " 已经是成员了。");
                return;
            }
            // 身份互斥：设为 member 时清除其 trusted/denied 身份。
            Set<PlayerRef> co = new HashSet<>(manor.coBuilders());
            Set<PlayerRef> denied = new HashSet<>(manor.denied());
            co.remove(tref);
            denied.remove(tref);
            manors.save(manor.withCoBuilders(co).withMembers(members).withDenied(denied));
            sender.sendMessage("§a已把 " + args[2] + " 加为成员（仅你或共建人在线时其可建造/交互）。");
        } else {
            if (!members.remove(tref)) {
                sender.sendMessage("§e" + args[2] + " 不是成员。");
                return;
            }
            manors.save(manor.withMembers(members));
            sender.sendMessage("§a已移除成员 " + args[2] + "。");
        }
    }

    /** /gs deny <玩家|*>：拉黑（* = 批量，需 deny.everyone 权限；同时清除共建人/成员身份）。 */
    private void deny(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /gs deny <玩家|*>");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。");
            return;
        }
        // 批量 deny *
        if (args[1].equals("*")) {
            if (!player.hasPermission(Permissions.DENY_EVERYONE)
                    && !Permissions.hasAdminPerm(player, Permissions.ADMIN_TRUST_OTHER)) {
                sender.sendMessage("§c你没有权限批量 deny（需要 " + Permissions.DENY_EVERYONE + "）。");
                return;
            }
            GuildId guild = manor.guild();
            int count = 0;
            for (Manor m : manors.findAll(guild)) {
                PlayerRef tref = m.owner();
                if (tref.equals(PlayerRef.of(player.getUniqueId()))) {
                    continue; // 跳过庄主自己
                }
                if (manor.denied().contains(tref)) {
                    continue; // 已在黑名单
                }
                Set<PlayerRef> co = new HashSet<>(manor.coBuilders());
                Set<PlayerRef> members = new HashSet<>(manor.members());
                Set<PlayerRef> denied = new HashSet<>(manor.denied());
                denied.add(tref);
                co.remove(tref);
                members.remove(tref);
                manor = manor.withCoBuilders(co).withMembers(members).withDenied(denied);
                count++;
            }
            if (count == 0) {
                sender.sendMessage("§e没有需要 deny 的人（所有人已在黑名单或无其他成员）。");
                return;
            }
            manors.save(manor);
            sender.sendMessage("§a已批量把 " + count + " 人加入地皮 #" + manor.slot() + " 黑名单。");
            return;
        }
        org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage("§e不能拉黑自己。");
            return;
        }
        PlayerRef tref = PlayerRef.of(target.getUniqueId());
        Set<PlayerRef> denied = new HashSet<>(manor.denied());
        if (!denied.add(tref)) {
            sender.sendMessage("§e" + args[1] + " 已在黑名单。");
            return;
        }
        // 拉黑时清除其共建人/成员身份，避免身份冲突。
        Set<PlayerRef> co = new HashSet<>(manor.coBuilders());
        Set<PlayerRef> members = new HashSet<>(manor.members());
        co.remove(tref);
        members.remove(tref);
        manors.save(manor.withCoBuilders(co).withMembers(members).withDenied(denied));
        sender.sendMessage("§a已将 " + args[1] + " 加入地皮 #" + manor.slot() + " 黑名单。");
    }

    /** /gs undeny <玩家>：移出黑名单。 */
    private void undeny(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /gs undeny <玩家>");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。");
            return;
        }
        org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
        Set<PlayerRef> denied = new HashSet<>(manor.denied());
        if (!denied.remove(PlayerRef.of(target.getUniqueId()))) {
            sender.sendMessage("§e" + args[1] + " 不在黑名单。");
            return;
        }
        manors.save(manor.withDenied(denied));
        sender.sendMessage("§a已将 " + args[1] + " 移出黑名单。");
    }

    /** /gs list：列出所有公会营地。 */
    private void list(CommandSender sender) {
        List<GuildWorld> all = guilds.findAll();
        if (all.isEmpty()) {
            sender.sendMessage("§e还没有任何公会营地。");
            return;
        }
        sender.sendMessage("§6==== 公会营地 (" + all.size() + ") ====");
        for (GuildWorld gw : all) {
            int members = manors.findAll(gw.guild()).size();
            int cap = levels.maxMembers(gw.guildLevel());
            sender.sendMessage("§7- §f" + gw.guild().value() + " §7Lv" + gw.guildLevel()
                    + " 成员 " + members + "/" + cap + " §8(/gs visit " + gw.guild().value() + ")");
        }
    }

    /** /gs visit <公会>：到访某公会的主城。 */
    private void visit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /gs visit <公会id>");
            return;
        }
        GuildId guild = new GuildId(args[1]);
        GuildWorld gw = guilds.find(guild).orElse(null);
        if (gw == null) {
            sender.sendMessage("§c公会 " + guild.value() + " 不存在或还没营地。");
            return;
        }
        gw = worlds.ensureWorld(gw);
        guilds.save(gw);
        registry.register(gw);
        World world = Bukkit.getWorld(gw.worldName());
        if (world == null) {
            sender.sendMessage("§c世界加载失败: " + gw.worldName());
            return;
        }
        player.teleport(worlds.safeSpawn(world, gw));
        sender.sendMessage("§a已到访公会 " + guild.value() + " 的主城。");
    }

    /** /gs clear：清空自己地皮的地表建筑。 */
    private void clear(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        Manor manor = manors.findByOwnerAnywhere(ref).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。");
            return;
        }
        service.clearManor(manor.guild(), ref);
        sender.sendMessage("§a已清空你地皮 #" + manor.slot() + " 的地表建筑（清植被式，整地进行中）。");
    }

    /** /gs flag [set|unset <flag> [值] | (空)查看]：管理地皮 flag（庄主 / 有 per-flag 权限者）。 */
    private void flag(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        Manor manor = manors.findByOwnerAnywhere(ref).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮（只有庄主能设 flag，或需要 per-flag 权限）。");
            return;
        }
        boolean isOwner = manor.owner().equals(ref);
        String action = args.length >= 2 ? args[1].toLowerCase() : "";
        switch (action) {
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage("用法: /gs flag set <flag> <值>");
                    return;
                }
                Flag f = Flag.byId(args[2]).orElse(null);
                if (f == null) {
                    sender.sendMessage("§c未知 flag: " + args[2] + "（/gs flag 查看可用）");
                    return;
                }
                // 权限检查：庄主直接放行；否则需要 per-flag 权限或 admin.flag.other
                if (!isOwner && !player.hasPermission(Permissions.flagSet(f.id()))
                        && !Permissions.hasAdminPerm(player, Permissions.ADMIN_FLAG_OTHER)) {
                    sender.sendMessage("§c只有庄主才能设此 flag（需要 " + Permissions.flagSet(f.id()) + " 权限）。");
                    return;
                }
                // 字符串型(greeting/farewell)取后面所有词；布尔/整数取单个。
                String raw = f.type() == FlagType.STRING
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length))
                        : args[3];
                String value = f.normalize(raw).orElse(null);
                if (value == null) {
                    sender.sendMessage("§c值非法（布尔型需 true / false）。");
                    return;
                }
                Map<String, String> flags = new HashMap<>(manor.flags());
                flags.put(f.id(), value);
                manors.save(manor.withFlags(flags));
                sender.sendMessage("§a已设 §f" + f.id() + " = " + value + " §7(地皮 #" + manor.slot() + ")");
            }
            case "unset" -> {
                if (args.length < 3) {
                    sender.sendMessage("用法: /gs flag unset <flag>");
                    return;
                }
                Flag f = Flag.byId(args[2]).orElse(null);
                if (f == null) {
                    sender.sendMessage("§c未知 flag: " + args[2]);
                    return;
                }
                // 权限检查：同 set
                if (!isOwner && !player.hasPermission(Permissions.flagSet(f.id()))
                        && !Permissions.hasAdminPerm(player, Permissions.ADMIN_FLAG_OTHER)) {
                    sender.sendMessage("§c只有庄主才能设此 flag（需要 " + Permissions.flagSet(f.id()) + " 权限）。");
                    return;
                }
                Map<String, String> flags = new HashMap<>(manor.flags());
                if (flags.remove(f.id()) == null) {
                    sender.sendMessage("§e该 flag 本就用默认值（" + f.defaultValue() + "）。");
                    return;
                }
                manors.save(manor.withFlags(flags));
                sender.sendMessage("§a已重置 §f" + f.id() + " §a为默认（" + f.defaultValue() + "）。");
            }
            default -> {
                sender.sendMessage("§6==== 地皮 #" + manor.slot() + " Flag ====");
                sender.sendMessage("§7用法: /gs flag set <flag> <值> | unset <flag>");
                for (Flag f : Flag.values()) {
                    String cur = manor.flags().get(f.id());
                    String shown = cur != null ? "§f" + cur : "§8" + f.defaultValue() + "(默认)";
                    sender.sendMessage("§7" + f.id() + " = " + shown + " §8- " + f.description());
                }
            }
        }
    }

    /** /gs card [玩家]：展示地皮档案卡（基础信息+实体统计+成员+描述+评分）。 */
    private void card(CommandSender sender, String[] args) {
        PlayerRef targetRef;
        String targetName;
        if (args.length >= 2) {
            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
            targetRef = PlayerRef.of(target.getUniqueId());
            targetName = args[1];
        } else if (sender instanceof Player player) {
            targetRef = PlayerRef.of(player.getUniqueId());
            targetName = player.getName();
        } else {
            sender.sendMessage("§c控制台需指定玩家名: /gs card <玩家>");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(targetRef).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e" + targetName + " 还没有地皮。");
            return;
        }
        GuildWorld gw = guilds.find(manor.guild()).orElse(null);
        if (gw == null) {
            sender.sendMessage("§c公会世界不存在。");
            return;
        }

        int side = gw.layout().plotChunksByLevel(manor.level()) * 16;
        int capacity = levels.maxMembers(gw.guildLevel());
        int memberCount = manors.findAll(manor.guild()).size();
        String desc = Flag.DESCRIPTION.resolveString(manor.flags());

        // 实体统计（需世界已加载）
        String entityLine = "§8(世界未加载)";
        int score = manor.level() * 100 + memberCount * 10 + manor.flags().size() * 2;
        World world = Bukkit.getWorld(gw.worldName());
        if (world != null && census != null) {
            ManorEntityCensus.Census c = census.countAt(world, manor);
            entityLine = "§a" + c.animals() + " §7动物 §c" + c.hostiles() + " §7敌对 §b"
                    + c.otherMobs() + " §7其它 §6" + c.vehicles() + " §7载具";
            score += c.livingTotal() * 5;
        }

        // 活跃 flag（非默认值的）
        long activeFlags = manor.flags().entrySet().stream()
                .filter(e -> {
                    Flag f = Flag.byId(e.getKey()).orElse(null);
                    return f != null && !e.getValue().equals(f.defaultValue());
                }).count();

        String alias = Flag.ALIAS.resolveString(manor.flags());
        String title = alias.isBlank() ? "#" + manor.slot() : alias + " (#" + manor.slot() + ")";
        boolean done = Flag.DONE.resolveBool(manor.flags());

        sender.sendMessage("§6┌─────────── §e家园卡 §6───────────");
        sender.sendMessage("§6│ §7地皮: §f" + title + "  §7公会: §f" + manor.guild().value()
                + "  §7等级: §f" + gw.guildLevel()
                + (done ? "  §a✔ 已完工" : "  §e🔨 建造中"));
        sender.sendMessage("§6│ §7庄主: §f" + targetName + "  §7庄园Lv: §f" + manor.level()
                + "/" + levels.manorMaxLevel() + "  §7尺寸: §f" + side + "×" + side);
        if (!alias.isBlank()) {
            sender.sendMessage("§6│ §7别名: §f" + alias);
        }
        if (!desc.isBlank()) {
            sender.sendMessage("§6│ §7描述: §f" + desc);
        }
        sender.sendMessage("§6│ §7实体: " + entityLine);
        sender.sendMessage("§6│ §7成员: §f" + memberCount + "/" + capacity
                + "  §7trusted: §f" + manor.coBuilders().size()
                + "  §7denied: §c" + manor.denied().size());
        sender.sendMessage("§6│ §7活跃flag: §f" + activeFlags + " §7个");
        double price = Flag.PRICE.resolveDouble(manor.flags());
        if (price > 0) {
            sender.sendMessage("§6│ §7入场费: §e" + price);
        }
        sender.sendMessage("§6│");
        sender.sendMessage("§6│ §e综合评分: §a§l" + score + " §7分");
        sender.sendMessage("§6└─────────────────────────");
    }

    // ===== 新增命令：alias / sethome / done / kick =====

    /** /gs alias <名称>：设置地皮别名（空参清除）。 */
    private void alias(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。");
            return;
        }
        String name = args.length >= 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : "";
        Map<String, String> flags = new HashMap<>(manor.flags());
        if (name.isBlank()) {
            flags.remove(Flag.ALIAS.id());
            manors.save(manor.withFlags(flags));
            sender.sendMessage("§a已清除地皮别名。");
        } else {
            flags.put(Flag.ALIAS.id(), name.replace(';', ','));
            manors.save(manor.withFlags(flags));
            sender.sendMessage("§a地皮别名已设为: §f" + name);
        }
    }

    /** /gs sethome：把当前位置设为 /gs home 的传送点。 */
    private void sethome(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。");
            return;
        }
        // 检查是否在自己的地皮上
        GuildWorld gw = registry.get(player.getWorld().getName());
        if (gw == null || !gw.guild().equals(manor.guild())) {
            sender.sendMessage("§c你不在自己的公会世界里。");
            return;
        }
        Location loc = player.getLocation();
        Map<String, String> flags = new HashMap<>(manor.flags());
        flags.put(Flag.HOME_X.id(), Integer.toString(loc.getBlockX()));
        flags.put(Flag.HOME_Y.id(), Integer.toString(loc.getBlockY()));
        flags.put(Flag.HOME_Z.id(), Integer.toString(loc.getBlockZ()));
        manors.save(manor.withFlags(flags));
        sender.sendMessage("§a/home 传送点已设为: §f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
    }

    /** /gs done：切换地皮完工标记。 */
    private void done(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。");
            return;
        }
        boolean current = Flag.DONE.resolveBool(manor.flags());
        Map<String, String> flags = new HashMap<>(manor.flags());
        flags.put(Flag.DONE.id(), Boolean.toString(!current));
        manors.save(manor.withFlags(flags));
        if (current) {
            sender.sendMessage("§e已取消完工标记（建造中）。");
        } else {
            sender.sendMessage("§a已标记地皮为 §a✔ 已完工。");
        }
    }

    /** /gs kick <玩家>：把非成员从你的地皮上踢出去（传送到边界外）。 */
    private void kick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /gs kick <玩家>");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§c玩家 " + args[1] + " 不在线。");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage("§e不能踢自己。");
            return;
        }
        // 检查目标是否在自己的地皮上
        GuildWorld gw = registry.get(target.getWorld().getName());
        if (gw == null || !gw.guild().equals(manor.guild())) {
            sender.sendMessage("§e" + target.getName() + " 不在你的公会世界里。");
            return;
        }
        PlayerRef targetRef = PlayerRef.of(target.getUniqueId());
        // 成员不能踢（用 deny 拉黑）
        if (ManorRoles.isMemberOrAbove(manor, targetRef)) {
            sender.sendMessage("§e" + target.getName() + " 是地皮成员，不能踢。用 /gs deny 拉黑。");
            return;
        }
        // 传送到公会主城
        World world = Bukkit.getWorld(gw.worldName());
        if (world != null) {
            target.teleport(worlds.safeSpawn(world, gw));
            target.sendMessage("§e你被 " + player.getName() + " 从地皮 #" + manor.slot() + " 踢出了。");
            sender.sendMessage("§a已将 " + target.getName() + " 踢出你的地皮。");
        }
    }

    // ===== near / rate / top / middle =====

    /** /gs near：列出附近地皮（按距离排序，显示庄主+距离）。 */
    private void near(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        GuildWorld gw = registry.get(player.getWorld().getName());
        if (gw == null) {
            sender.sendMessage("§c你不在公会世界里。");
            return;
        }
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        int px = (player.getLocation().getBlockX() >> 4) - gw.originChunkX();
        int pz = (player.getLocation().getBlockZ() >> 4) - gw.originChunkZ();
        List<Manor> all = manors.findAll(gw.guild());
        if (all.isEmpty()) {
            sender.sendMessage("§e该公会还没有地皮。");
            return;
        }
        // 按距离排序
        record SlotDist(Manor manor, double dist) {}
        List<SlotDist> sorted = new ArrayList<>();
        for (Manor m : all) {
            ChunkRegion active = layout.activeRegion(m.slot(), m.level());
            int cx = (active.minChunkX() + active.maxChunkX()) / 2;
            int cz = (active.minChunkZ() + active.maxChunkZ()) / 2;
            double dist = Math.sqrt((cx - px) * (cx - px) + (cz - pz) * (cz - pz));
            sorted.add(new SlotDist(m, dist));
        }
        sorted.sort((a, b) -> Double.compare(a.dist(), b.dist()));
        sender.sendMessage("§6==== 附近地皮 ====");
        int show = Math.min(sorted.size(), 10);
        for (int i = 0; i < show; i++) {
            SlotDist sd = sorted.get(i);
            String alias = Flag.ALIAS.resolveString(sd.manor().flags());
            String name = alias.isBlank() ? "#" + sd.manor().slot() : alias + " (#" + sd.manor().slot() + ")";
            String ownerName = Bukkit.getOfflinePlayer(sd.manor().owner().uuid()).getName();
            sender.sendMessage("§7- §f" + name + " §7庄主: §f" + (ownerName != null ? ownerName : "?")
                    + " §7距离: §e" + String.format("%.1f", sd.dist() * 16) + " 格");
        }
    }

    /** /gs rate <分数>：给当前所在地皮打分（1-10）。 */
    private void rate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /gs rate <1-10>");
            return;
        }
        int score;
        try {
            score = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c分数必须是 1-10 的整数。");
            return;
        }
        if (score < 1 || score > 10) {
            sender.sendMessage("§c分数必须在 1-10 之间。");
            return;
        }
        GuildWorld gw = registry.get(player.getWorld().getName());
        if (gw == null) {
            sender.sendMessage("§c你不在公会世界里。");
            return;
        }
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        int bx = player.getLocation().getBlockX();
        int bz = player.getLocation().getBlockZ();
        int lx = (bx >> 4) - gw.originChunkX();
        int lz = (bz >> 4) - gw.originChunkZ();
        Classification c = mergeClassify(layout, gw.guild(), lx, lz);
        if (!c.isPlot()) {
            sender.sendMessage("§c你不在任何地皮上。");
            return;
        }
        Manor manor = manors.findBySlot(gw.guild(), c.slot()).orElse(null);
        if (manor == null) {
            sender.sendMessage("§c该 slot 没有庄园。");
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        if (manor.owner().equals(ref)) {
            sender.sendMessage("§e不能给自己的地皮打分。");
            return;
        }
        manors.rate(gw.guild(), c.slot(), ref, score);
        double avg = manors.getAverageRating(gw.guild(), c.slot());
        int count = manors.getRatingCount(gw.guild(), c.slot());
        sender.sendMessage("§a已给地皮 #" + c.slot() + " 打 §e" + score + " §a分"
                + "（平均 §e" + String.format("%.1f", avg) + " §a，共 " + count + " 人评分）");
    }

    /** /gs top：按评分排行。 */
    private void top(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。");
            return;
        }
        List<Integer> topSlots = manors.getTopRatedSlots(manor.guild(), 10);
        if (topSlots.isEmpty()) {
            sender.sendMessage("§e还没有任何地皮收到评分。用 /gs rate <1-10> 给别人的地皮打分。");
            return;
        }
        sender.sendMessage("§6==== 地皮评分排行 ====");
        int rank = 1;
        for (int slot : topSlots) {
            Manor m = manors.findBySlot(manor.guild(), slot).orElse(null);
            if (m == null) continue;
            double avg = manors.getAverageRating(manor.guild(), slot);
            int count = manors.getRatingCount(manor.guild(), slot);
            String alias = Flag.ALIAS.resolveString(m.flags());
            String name = alias.isBlank() ? "#" + slot : alias + " (#" + slot + ")";
            String ownerName = Bukkit.getOfflinePlayer(m.owner().uuid()).getName();
            sender.sendMessage("§e" + rank + ". §f" + name + " §7庄主: §f" + (ownerName != null ? ownerName : "?")
                    + " §7评分: §e" + String.format("%.1f", avg) + " §7(" + count + "人)");
            rank++;
        }
    }

    /** /gs middle：传送到地皮正中心（无视 sethome）。 */
    private void middle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。");
            return;
        }
        GuildWorld gw = ensureLoadedWorld(sender, manor.guild());
        if (gw == null) return;
        World world = Bukkit.getWorld(gw.worldName());
        ChunkRegion active = new LayoutCalculator(gw.layout())
                .activeRegion(manor.slot(), manor.level())
                .shift(gw.originChunkX(), gw.originChunkZ());
        int cx = (active.minBlockX() + active.maxBlockX()) / 2;
        int cz = (active.minBlockZ() + active.maxBlockZ()) / 2;
        world.loadChunk(cx >> 4, cz >> 4, true);
        int cy = world.getHighestBlockYAt(cx, cz) + 1;
        player.teleport(new Location(world, cx + 0.5, cy, cz + 0.5));
        sender.sendMessage("§a已传送到地皮 #" + manor.slot() + " 正中心。");
    }

    // ===== comment / inbox =====

    /** /gs comment <留言>：给当前所在地皮留言。 */
    private void comment(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /gs comment <留言内容>");
            return;
        }
        GuildWorld gw = registry.get(player.getWorld().getName());
        if (gw == null) {
            sender.sendMessage("§c你不在公会世界里。");
            return;
        }
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        int lx = (player.getLocation().getBlockX() >> 4) - gw.originChunkX();
        int lz = (player.getLocation().getBlockZ() >> 4) - gw.originChunkZ();
        Classification c = mergeClassify(layout, gw.guild(), lx, lz);
        if (!c.isPlot()) {
            sender.sendMessage("§c你不在任何地皮上。");
            return;
        }
        String msg = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        manors.addComment(gw.guild(), c.slot(), PlayerRef.of(player.getUniqueId()), msg);
        sender.sendMessage("§a已给地皮 #" + c.slot() + " 留言。");
    }

    /** /gs inbox：查看自己地皮收到的留言。 */
    private void inbox(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        List<ManorRepository.CommentEntry> entries = manors.getInbox(ref, 20);
        if (entries.isEmpty()) {
            sender.sendMessage("§e你的地皮还没有收到任何留言。");
            return;
        }
        sender.sendMessage("§6==== 收件箱（最近 20 条）====");
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm");
        for (ManorRepository.CommentEntry e : entries) {
            String authorName = Bukkit.getOfflinePlayer(e.author().uuid()).getName();
            String time = sdf.format(new java.util.Date(e.timestamp()));
            sender.sendMessage("§7[" + time + "] §f" + (authorName != null ? authorName : "?")
                    + " §7→ 地皮#" + e.slot() + ": §f" + e.message());
        }
    }

    // ===== swap / grant / merge =====

    /** /gs swap <玩家>：与对方互换地皮 slot。 */
    private void swap(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /gs swap <玩家>");
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        Manor myManor = manors.findByOwnerAnywhere(ref).orElse(null);
        if (myManor == null) {
            sender.sendMessage("§e你还没有地皮。");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§c玩家 " + args[1] + " 不在线。");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage("§e不能和自己换。");
            return;
        }
        PlayerRef targetRef = PlayerRef.of(target.getUniqueId());
        Manor targetManor = manors.findByOwnerAnywhere(targetRef).orElse(null);
        if (targetManor == null) {
            sender.sendMessage("§e" + target.getName() + " 还没有地皮。");
            return;
        }
        if (!myManor.guild().equals(targetManor.guild())) {
            sender.sendMessage("§c你们不在同一个公会。");
            return;
        }
        // 交换 slot：保存对方 slot 的数据给你，你的给对方
        int mySlot = myManor.slot();
        int theirSlot = targetManor.slot();
        Manor newMine = new Manor(theirSlot, myManor.guild(), ref, myManor.level(),
                myManor.coBuilders(), myManor.members(), myManor.denied(), myManor.flags());
        Manor newTheirs = new Manor(mySlot, targetManor.guild(), targetRef, targetManor.level(),
                targetManor.coBuilders(), targetManor.members(), targetManor.denied(), targetManor.flags());
        manors.save(newMine);
        manors.save(newTheirs);
        sender.sendMessage("§a已与 " + target.getName() + " 互换地皮（你的 #" + mySlot + " ↔ #" + theirSlot + "）。");
        target.sendMessage("§a" + player.getName() + " 与你互换了地皮（你的 #" + theirSlot + " ↔ #" + mySlot + "）。");
    }

    /** /gs grant <玩家>：给玩家分配额外地皮（需 admin）。 */
    private void grant(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        if (!Permissions.hasAdminPerm(player, Permissions.ADMIN_TRUST_OTHER)) {
            sender.sendMessage("§c你需要 admin.trust.other 权限。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /gs grant <玩家>");
            return;
        }
        Manor myManor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (myManor == null) {
            sender.sendMessage("§e你不在任何公会世界里（需站在目标公会世界中）。");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§c玩家 " + args[1] + " 不在线。");
            return;
        }
        PlayerRef targetRef = PlayerRef.of(target.getUniqueId());
        GuildId guild = myManor.guild();
        // 检查目标是否已有该公会的地皮
        if (manors.findByOwner(guild, targetRef).isPresent()) {
            sender.sendMessage("§e" + target.getName() + " 在该公会已有地皮。");
            return;
        }
        try {
            service.assignManor(guild, targetRef);
            sender.sendMessage("§a已给 " + target.getName() + " 分配一块新地皮。");
            target.sendMessage("§a你被分配了一块新的公会地皮。");
        } catch (GuildFullException e) {
            sender.sendMessage("§c公会已满（名额 " + e.capacity() + "）。");
        }
    }

    /** /gs merge <slot>：把相邻地皮合并到自己的地皮（砍掉中间的路）。 */
    private void mergeCmd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /gs merge <slot号>（把该 slot 合并到你的地皮）");
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        Manor myManor = manors.findByOwnerAnywhere(ref).orElse(null);
        if (myManor == null) {
            sender.sendMessage("§e你还没有地皮。");
            return;
        }
        int absorbedSlot;
        try {
            absorbedSlot = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cslot 必须是整数。");
            return;
        }
        if (absorbedSlot == myManor.slot()) {
            sender.sendMessage("§e不能和自己的地皮合并。");
            return;
        }
        Manor absorbed = manors.findBySlot(myManor.guild(), absorbedSlot).orElse(null);
        if (absorbed == null) {
            sender.sendMessage("§cslot #" + absorbedSlot + " 没有庄园。");
            return;
        }
        // 检查是否相邻（slot 在螺旋上相邻 = 距离 1）
        GuildWorld gw = guilds.find(myManor.guild()).orElse(null);
        if (gw == null) {
            sender.sendMessage("§c公会世界不存在。");
            return;
        }
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        ChunkRegion myRegion = layout.activeRegion(myManor.slot(), myManor.level());
        ChunkRegion theirRegion = layout.activeRegion(absorbedSlot, absorbed.level());
        // 相邻判定：两个 region 的曼哈顿距离 ≤ 1 chunk（中间只隔一条路）
        int dx = Math.min(Math.abs(myRegion.maxChunkX() - theirRegion.minChunkX()),
                Math.abs(theirRegion.maxChunkX() - myRegion.minChunkX()));
        int dz = Math.min(Math.abs(myRegion.maxChunkZ() - theirRegion.minChunkZ()),
                Math.abs(theirRegion.maxChunkZ() - myRegion.minChunkZ()));
        boolean adjacent = (dx <= 1 && dz == 0) || (dz <= 1 && dx == 0);
        if (!adjacent) {
            sender.sendMessage("§c两块地皮不相邻（中间必须只隔一条路）。");
            return;
        }
        manors.merge(myManor.slot(), absorbedSlot, myManor.guild());
        sender.sendMessage("§a已将地皮 #" + absorbedSlot + " 合并到你的地皮 #" + myManor.slot()
                + "（路 chunk 已归属你的地皮）。");
    }

    /** 合并感知的 classify：ROAD chunk 若在合并路带上，返回主地皮的 PLOT。 */
    private Classification mergeClassify(LayoutCalculator layout, GuildId guild, int chunkX, int chunkZ) {
        Classification raw = layout.classify(chunkX, chunkZ);
        if (raw.type() != org.windy.guildshelter.domain.layout.RegionType.ROAD) {
            return raw;
        }
        MergeAwareClassifier merger = new MergeAwareClassifier(layout, manors, guild);
        return merger.classify(chunkX, chunkZ);
    }

    /** 确保该公会世界已加载并登记；失败返回 null 并已提示。 */
    private GuildWorld ensureLoadedWorld(CommandSender sender, GuildId guild) {
        GuildWorld gw = guilds.find(guild).orElse(null);
        if (gw == null) {
            sender.sendMessage("§c你的公会世界不存在。");
            return null;
        }
        gw = worlds.ensureWorld(gw);
        guilds.save(gw);
        registry.register(gw);
        if (org.bukkit.Bukkit.getWorld(gw.worldName()) == null) {
            sender.sendMessage("§c世界加载失败: " + gw.worldName());
            return null;
        }
        return gw;
    }

    private void create(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("用法: /gs admin create <公会id>");
            return;
        }
        GuildId guild = new GuildId(args[2]);
        if (guilds.exists(guild)) {
            sender.sendMessage("§e公会世界已存在: " + guild.value());
            return;
        }
        long seed = ThreadLocalRandom.current().nextLong();
        GuildWorld gw = service.createGuild(guild, seed);
        registry.register(gw);
        sender.sendMessage("§a已创建公会世界（自然地形）: " + gw.worldName()
                + " §7种子=" + gw.seed() + " 原点偏移=(" + gw.originChunkX() + "," + gw.originChunkZ() + ")");
        logMap(guild);
    }

    private void tp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能传送。");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("用法: /gs admin tp <公会id>");
            return;
        }
        GuildWorld gw = guilds.find(new GuildId(args[2])).orElse(null);
        if (gw == null) {
            sender.sendMessage("§c公会世界不存在，先 /gs admin create " + args[2]);
            return;
        }
        gw = worlds.ensureWorld(gw);
        guilds.save(gw);
        registry.register(gw);
        World world = Bukkit.getWorld(gw.worldName());
        if (world == null) {
            sender.sendMessage("§c世界加载失败: " + gw.worldName());
            return;
        }
        player.teleport(worlds.safeSpawn(world, gw));
        sender.sendMessage("§a已传送到 " + gw.worldName() + " 主城。");
    }

    /** 给自己在该公会分配下一块地皮（自动整地），并传送过去观察。 */
    private void claim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能领地皮。");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("用法: /gs admin claim <公会id>");
            return;
        }
        GuildId guild = new GuildId(args[2]);
        GuildWorld gw = guilds.find(guild).orElse(null);
        if (gw == null) {
            sender.sendMessage("§c公会世界不存在，先 /gs admin create " + guild.value());
            return;
        }
        gw = worlds.ensureWorld(gw);
        registry.register(gw);

        Manor manor;
        try {
            manor = service.assignManor(guild, PlayerRef.of(player.getUniqueId()));
        } catch (GuildFullException e) {
            sender.sendMessage("§c公会已满（名额 " + e.capacity() + "，当前 " + e.guildLevel()
                    + " 级）。先 /gs admin upgrade-guild " + guild.value() + " 放开更多名额。");
            return;
        }
        // 分配后 allocatedSlots 可能变化，重新读
        gw = guilds.find(guild).orElse(gw);
        registry.register(gw);

        World world = Bukkit.getWorld(gw.worldName());
        if (world == null) {
            sender.sendMessage("§c世界加载失败: " + gw.worldName());
            return;
        }
        ChunkRegion active = new LayoutCalculator(gw.layout())
                .activeRegion(manor.slot(), manor.level())
                .shift(gw.originChunkX(), gw.originChunkZ());
        int cx = (active.minBlockX() + active.maxBlockX()) / 2;
        int cz = (active.minBlockZ() + active.maxBlockZ()) / 2;
        world.loadChunk(cx >> 4, cz >> 4, true);
        int cy = world.getHighestBlockYAt(cx, cz) + 1;
        player.teleport(new Location(world, cx + 0.5, cy, cz + 0.5));
        sender.sendMessage("§a已分配地皮 #" + manor.slot() + "（等级 " + manor.level()
                + "），整地进行中。你已被传送过去。");
        sender.sendMessage("§7提示: claim 对同一玩家幂等(一人一块)。要看多块分布用 /gs admin fill "
                + guild.value() + " <数量>。网格图已打到控制台。");
        logMap(guild);
    }

    /** 升级发令玩家在该公会的庄园一级（成员自己的事，只受物理满级限制），升级后对新扩范围整地。 */
    private void upgradeManor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能升级自己的庄园。");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("用法: /gs admin upgrade-manor <公会id>");
            return;
        }
        GuildId guild = new GuildId(args[2]);
        if (guilds.find(guild).isEmpty()) {
            sender.sendMessage("§c公会世界不存在，先 /gs admin create " + guild.value());
            return;
        }
        int cap = levels.manorMaxLevel();
        try {
            boolean ok = service.upgradeManor(guild, PlayerRef.of(player.getUniqueId()));
            if (ok) {
                Manor m = service.assignManor(guild, PlayerRef.of(player.getUniqueId())); // 幂等读回当前庄园
                sender.sendMessage("§a庄园已升至 " + m.level() + " / " + cap
                        + " 级，新扩范围整地进行中。");
            } else {
                sender.sendMessage("§e庄园已达满级 " + cap + "。");
            }
        } catch (NoSuchElementException e) {
            sender.sendMessage("§c你在该公会还没有庄园，先 /gs admin claim " + guild.value());
        }
    }

    /** 测试用：给随机 UUID 批量分配 n 块地皮，把网格填出来便于观察分布（满了即停）。 */
    private void fill(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("用法: /gs admin fill <公会id> <数量>");
            return;
        }
        GuildId guild = new GuildId(args[2]);
        int n;
        try {
            n = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c数量必须是整数。");
            return;
        }
        GuildWorld gw = guilds.find(guild).orElse(null);
        if (gw == null) {
            sender.sendMessage("§c公会世界不存在，先 /gs admin create " + guild.value());
            return;
        }
        gw = worlds.ensureWorld(gw);
        guilds.save(gw);
        registry.register(gw);

        int done = 0;
        for (int i = 0; i < n; i++) {
            try {
                service.assignManor(guild, PlayerRef.of(UUID.randomUUID()));
                done++;
            } catch (GuildFullException e) {
                sender.sendMessage("§e达到当前等级名额上限 " + e.capacity() + "，已填 " + done
                        + " 块。先 /gs admin upgrade-guild " + guild.value() + " 再继续。");
                break;
            }
        }
        sender.sendMessage("§a已为 " + guild.value() + " 填充 " + done + " 块测试地皮。网格图见控制台。");
        logMap(guild);
    }

    /** 仅把网格分布图打到控制台（不改任何数据）。 */
    private void map(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("用法: /gs admin map <公会id>");
            return;
        }
        GuildId guild = new GuildId(args[2]);
        if (guilds.find(guild).isEmpty()) {
            sender.sendMessage("§c公会世界不存在，先 /gs admin create " + guild.value());
            return;
        }
        logMap(guild);
        sender.sendMessage("§a网格分布图已输出到控制台。");
    }

    /** 读取当前实际庄园占用，渲染 ASCII 网格图并逐行打到控制台日志。 */
    private void logMap(GuildId guild) {
        GuildWorld gw = guilds.find(guild).orElse(null);
        if (gw == null) {
            return;
        }
        Set<Integer> occupied = new HashSet<>();
        for (Manor m : manors.findAll(guild)) {
            occupied.add(m.slot());
        }
        int capacity = levels.maxMembers(gw.guildLevel());
        LayoutCalculator layout = new LayoutCalculator(gw.layout()); // 用该世界冻结的布局
        int cityHalf = gw.layout().cityHalfAtLevel(gw.guildLevel(), levels.maxGuildLevel());
        for (String line : GridAsciiMap.render(layout, gw, occupied, capacity, cityHalf)) {
            logger.info(line);
        }
    }

    /** 升级公会一级（放开更多成员名额），并按新等级容量扩世界边界。 */
    private void upgradeGuild(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("用法: /gs admin upgrade-guild <公会id>");
            return;
        }
        GuildId guild = new GuildId(args[2]);
        GuildWorld before = guilds.find(guild).orElse(null);
        if (before == null) {
            sender.sendMessage("§c公会世界不存在，先 /gs admin create " + guild.value());
            return;
        }
        LayoutCalculator layout = new LayoutCalculator(before.layout()); // 用该世界冻结的布局
        double oldBorder = layout.borderSizeBlocks(
                Math.max(before.allocatedSlots(), levels.maxMembers(before.guildLevel())));
        boolean ok = service.upgradeGuild(guild);
        if (!ok) {
            sender.sendMessage("§e公会已达最高等级 " + levels.maxGuildLevel() + "。");
            return;
        }
        GuildWorld after = guilds.find(guild).orElse(before);
        double newBorder = layout.borderSizeBlocks(
                Math.max(after.allocatedSlots(), levels.maxMembers(after.guildLevel())));
        sender.sendMessage("§a公会已升至 " + after.guildLevel() + " / " + levels.maxGuildLevel()
                + " 级；成员名额 " + levels.maxMembers(before.guildLevel())
                + " → " + levels.maxMembers(after.guildLevel())
                + "，世界边界 " + (int) oldBorder + " → " + (int) newBorder + " 方块。");
        logMap(guild);
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("用法: /gs admin delete <公会id>");
            return;
        }
        GuildId guild = new GuildId(args[2]);
        worlds.unloadGuild(guild);
        guilds.delete(guild);
        registry.unregister(worlds.worldName(guild));
        sender.sendMessage("§a已卸载并移除记录: " + worlds.worldName(guild)
                + "（世界文件夹未删除，需手动清理）");
    }

    private void listWorlds(CommandSender sender) {
        List<World> all = Bukkit.getWorlds();
        sender.sendMessage("§e已加载世界 (" + all.size() + "):");
        for (World w : all) {
            Location s = w.getSpawnLocation();
            sender.sendMessage(String.format("§7- §f%s §7env=%s spawn=(%d,%d,%d)",
                    w.getName(), w.getEnvironment(), s.getBlockX(), s.getBlockY(), s.getBlockZ()));
        }
    }

    private void whereami(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家有所在世界。");
            return;
        }
        Location l = player.getLocation();
        sender.sendMessage("§e你在世界 §f" + l.getWorld().getName()
                + " §7(" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() + ")");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[]{"home", "spawn", "upgrade", "info", "trust", "untrust",
                    "member", "deny", "undeny", "list", "visit", "clear", "flag", "card",
                    "alias", "sethome", "done", "kick", "near", "rate", "top", "middle",
                    "comment", "inbox", "swap", "grant", "merge", "admin"}) {
                out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("flag")) {
            out.add("set");
            out.add("unset");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("card")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("kick")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("swap")
                || args[0].equalsIgnoreCase("grant"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("rate")) {
            for (int i = 1; i <= 10; i++) {
                out.add(String.valueOf(i));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("flag")) {
            for (Flag f : Flag.values()) {
                out.add(f.id());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("member")) {
            out.add("add");
            out.add("remove");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("trust")
                || args[0].equalsIgnoreCase("untrust") || args[0].equalsIgnoreCase("deny")
                || args[0].equalsIgnoreCase("undeny"))) {
            if (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("deny")) {
                out.add("*");
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("member")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("visit")) {
            for (GuildWorld gw : guilds.findAll()) {
                out.add(gw.guild().value());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            for (String s : new String[]{"create", "tp", "claim", "fill", "map", "upgrade-manor",
                    "upgrade-guild", "delete", "worlds", "whereami"}) {
                out.add(s);
            }
        }
        return out;
    }
}
