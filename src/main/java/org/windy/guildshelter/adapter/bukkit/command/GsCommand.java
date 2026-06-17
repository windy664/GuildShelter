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
import org.windy.guildshelter.adapter.bukkit.world.WorldManager;
import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.flag.FlagType;
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

    private static final String ADMIN_PERM = "guildshelter.admin";
    /** 受 guildshelter.command.<sub> 节点管控的玩家子命令（节点默认放行，见 plugin.yml）。 */
    private static final Set<String> PLAYER_SUBS = Set.of("home", "spawn", "upgrade", "info",
            "trust", "untrust", "member", "deny", "undeny", "list", "visit", "clear", "flag");

    private final WorldManager worlds;
    private final GuildRepository guilds;
    private final ManorRepository manors;
    private final GuildService service;
    private final GuildWorldRegistry registry;
    private final LevelRules levels;
    private final Logger logger;

    public GsCommand(WorldManager worlds, GuildRepository guilds, ManorRepository manors,
                     GuildService service, GuildWorldRegistry registry,
                     LevelRules levels, Logger logger) {
        this.worlds = worlds;
        this.guilds = guilds;
        this.manors = manors;
        this.service = service;
        this.registry = registry;
        this.levels = levels;
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
            case "admin" -> { /* 落到下面的管理分支 */ }
            default -> {
                sender.sendMessage("§e/gs <home|spawn|upgrade|info|trust|untrust|member|deny|undeny|list|visit|clear|flag>  §7玩家命令");
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

    /** /gs home：传送到自己地皮的实占范围中心。 */
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
        ChunkRegion active = new LayoutCalculator(gw.layout())
                .activeRegion(manor.slot(), manor.level())
                .shift(gw.originChunkX(), gw.originChunkZ());
        int cx = (active.minBlockX() + active.maxBlockX()) / 2;
        int cz = (active.minBlockZ() + active.maxBlockZ()) / 2;
        world.loadChunk(cx >> 4, cz >> 4, true);
        int cy = world.getHighestBlockYAt(cx, cz) + 1;
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
        sender.sendMessage("§7公会: §f" + manor.guild().value() + " §7(Lv" + gw.guildLevel()
                + "/" + levels.maxGuildLevel() + ", 成员 " + members + "/" + capacity + ")");
        sender.sendMessage("§7你的地皮: §f#" + manor.slot() + " §7庄园 Lv" + manor.level()
                + "/" + levels.manorMaxLevel() + " §7尺寸 " + side + "×" + side);
        sender.sendMessage("§7共建人(trusted): §f" + sizeOrNone(manor.coBuilders())
                + " §7成员(member): §f" + sizeOrNone(manor.members())
                + " §7黑名单: §c" + sizeOrNone(manor.denied()));
    }

    private static String sizeOrNone(Set<PlayerRef> set) {
        return set.isEmpty() ? "§8无" : set.size() + " 人";
    }

    /** /gs trust <玩家>：给自己的地皮加共建人。 */
    private void trust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /gs trust <玩家>");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。");
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

    /** /gs deny <玩家>：拉黑（禁止进入/交互，覆盖访客 flag；同时清除其共建人/成员身份）。 */
    private void deny(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /gs deny <玩家>");
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮。");
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

    /** /gs flag [set|unset <flag> [值] | (空)查看]：管理自己地皮的 flag（仅庄主）。 */
    private void flag(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家能用此命令。");
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        Manor manor = manors.findByOwnerAnywhere(ref).orElse(null);
        if (manor == null) {
            sender.sendMessage("§e你还没有地皮（只有庄主能设 flag）。");
            return;
        }
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
                    "member", "deny", "undeny", "list", "visit", "clear", "flag", "admin"}) {
                out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("flag")) {
            out.add("set");
            out.add("unset");
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
