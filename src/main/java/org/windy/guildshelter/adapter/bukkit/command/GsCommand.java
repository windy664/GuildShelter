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
import org.windy.guildshelter.adapter.bukkit.MergeRegistry;
import org.windy.guildshelter.adapter.bukkit.Messages;
import org.windy.guildshelter.adapter.bukkit.Permissions;
import org.windy.guildshelter.adapter.bukkit.listener.ManorAccessListener;
import org.windy.guildshelter.adapter.bukkit.world.WorldManager;
import org.windy.guildshelter.adapter.bungee.ProxyChannel;
import org.windy.guildshelter.domain.port.SchematicStore;
import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.flag.FlagType;
import org.windy.guildshelter.domain.layout.Classification;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.model.TerrainPrepMode;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * /gs 命令分发（验证 + 管理）。
 *
 * <p>admin 子命令：create 建公会营地、tp 进主城、claim 给自己分配一块庄园（自动整地）、
 * delete 卸载、worlds/whereami 诊断。普通玩家命令将在接入 GuildProvider 后补全。
 */
public final class GsCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERM = Permissions.ADMIN;
    /** 受 guildshelter.command.<sub> 节点管控的玩家子命令（节点默认放行，见 plugin.yml）。 */
    private static final Set<String> PLAYER_SUBS = Set.of("home", "spawn", "upgrade", "info",
            "trust", "untrust", "member", "deny", "undeny", "list", "visit", "clear", "flag", "card",
            "alias", "sethome", "done", "kick", "near", "rate", "top", "middle",
            "comment", "inbox", "swap", "grant", "merge", "unmerge", "confirm", "help", "desc", "toggle", "template", "sub", "bulletin", "open", "close", "flower", "gift", "board", "move",
            "citytrust", "cityuntrust");

    /** 需要确认的危险操作。 */
    private static final Set<String> CONFIRM_REQUIRED = Set.of(
            "deny", "clear", "merge", "unmerge", "swap", "grant", "move");

    /** trusted 共建人可设的 flag（交互类常用 flag，庄主不用每件小事都亲自改）。 */
    private static final Set<String> TRUSTED_FLAG_SET = Set.of(
            "pvp", "pve", "pve-monster", "pve-player", "use", "container", "item-frame", "vehicle-use",
            "greeting", "farewell", "titles", "notify-enter", "notify-leave",
            "fly", "feed", "heal", "invincible",
            "deny-entry", "deny-exit", "description");

    /** 玩家待确认操作队列。 */
    private record PendingAction(String sub, String[] args, long expireAt) {
        boolean expired() { return System.currentTimeMillis() > expireAt; }
    }
    private final Map<UUID, PendingAction> pendingConfirm = new ConcurrentHashMap<>();

    /** 庄园临时开放状态："guildId:slot" → 过期时间戳。0 = 永久开放（手动关闭）。 */
    private final Map<String, Long> openPlots = new ConcurrentHashMap<>();

    private final WorldManager worlds;
    private final GuildRepository guilds;
    private final ManorRepository manors;
    private final GuildService service;
    private final GuildWorldRegistry registry;
    private final LevelRules levels;
    private final ManorEntityCensus census;
    private final MergeRegistry merges;
    private ManorAccessListener accessListener; // 可选，toggle 需要
    private SchematicStore schematicStore; // 可选，模板系统
    private final ProxyChannel proxyChannel;
    private final String serverName;
    private final Logger logger;
    private final org.bukkit.plugin.Plugin plugin;

    public GsCommand(WorldManager worlds, GuildRepository guilds, ManorRepository manors,
                     GuildService service, GuildWorldRegistry registry,
                     LevelRules levels, ManorEntityCensus census, MergeRegistry merges,
                     ProxyChannel proxyChannel, String serverName, Logger logger,
                     org.bukkit.plugin.Plugin plugin) {
        this.worlds = worlds;
        this.guilds = guilds;
        this.manors = manors;
        this.service = service;
        this.registry = registry;
        this.levels = levels;
        this.census = census;
        this.merges = merges;
        this.proxyChannel = proxyChannel;
        this.serverName = serverName;
        this.logger = logger;
        this.plugin = plugin;
    }

    /** 设置访问监听器引用（toggle titles 需要）。 */
    public void setAccessListener(ManorAccessListener listener) {
        this.accessListener = listener;
    }

    /** 庄园开放状态 Map（供 ManorAccessListener 检查 deny-entry 豁免）。 */
    public java.util.Map<String, Long> openPlots() { return openPlots; }

    /** 注入 SchematicStore（模板系统）。 */
    public void setSchematicStore(SchematicStore store) { this.schematicStore = store; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length >= 1 ? args[0].toLowerCase() : "";

        // confirm 命令：执行队列中的待确认操作
        if ("confirm".equals(sub)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.get("error.player_only"));
                return true;
            }
            PendingAction pending = pendingConfirm.remove(player.getUniqueId());
            if (pending == null) {
                sender.sendMessage(Messages.get("error.no_pending"));
            } else if (pending.expired()) {
                sender.sendMessage(Messages.get("error.confirm_expired"));
            } else {
                // 重新执行原始命令（跳过确认检查）
                executeSub(sender, pending.sub(), pending.args());
            }
            return true;
        }

        // 危险命令拦截：存入待确认队列
        if (CONFIRM_REQUIRED.contains(sub) && sender instanceof Player player) {
            UUID id = player.getUniqueId();
            PendingAction existing = pendingConfirm.get(id);
            if (existing != null && !existing.expired() && existing.sub().equals(sub)) {
                // 同一命令连续执行两次 = 直接确认
                pendingConfirm.remove(id);
            } else {
                pendingConfirm.put(id, new PendingAction(sub, args, System.currentTimeMillis() + 30_000));
                sender.sendMessage(Messages.get("error.need_confirm"));
                return true;
            }
        }

        // 玩家子命令权限节点把关(默认放行)；admin 另由 ADMIN_PERM 管，未知子命令落到帮助。
        if (PLAYER_SUBS.contains(sub) && !sender.hasPermission("guildshelter.command." + sub)) {
            sender.sendMessage(Messages.get("error.no_permission_for", sub));
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
            case "list" -> { list(sender, args); return true; }
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
            case "top" -> { top(sender, args); return true; }
            case "middle" -> { middle(sender); return true; }
            case "comment" -> { comment(sender, args); return true; }
            case "inbox" -> { inbox(sender); return true; }
            case "swap" -> { swap(sender, args); return true; }
            case "grant" -> { grant(sender, args); return true; }
            case "merge" -> { mergeCmd(sender, args); return true; }
            case "unmerge" -> { unmerge(sender, args); return true; }
            case "help" -> { help(sender, args); return true; }
            case "desc" -> { desc(sender, args); return true; }
            case "toggle" -> { toggle(sender, args); return true; }
            case "template" -> { template(sender, args); return true; }
            case "sub" -> { sub(sender, args); return true; }
            case "bulletin" -> { bulletin(sender, args); return true; }
            case "open" -> { openPlot(sender, args); return true; }
            case "close" -> { closePlot(sender); return true; }
            case "flower" -> { flower(sender, args); return true; }
            case "gift" -> { gift(sender, args); return true; }
            case "board" -> { board(sender); return true; }
            case "move" -> { move(sender, args); return true; }
            case "citytrust" -> { cityTrust(sender, args, true); return true; }
            case "cityuntrust" -> { cityTrust(sender, args, false); return true; }
            case "admin" -> { /* 落到下面的管理分支 */ }
            default -> {
                sender.sendMessage(Messages.get("usage.player_commands"));
                sender.sendMessage(Messages.get("usage.admin_commands"));
                return true;
            }
        }
        if (!sender.hasPermission(ADMIN_PERM) && !sender.isOp()) {
            sender.sendMessage(Messages.get("error.no_permission"));
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
            case "reload" -> adminReload(sender);
            case "setowner" -> setOwner(sender, args);
            case "purge" -> purge(sender, args);
            case "regen" -> regen(sender, args);
            case "export" -> exportData(sender);
            case "fund" -> fund(sender, args);
            case "citywall" -> citywall(sender, args);
            default -> sender.sendMessage(Messages.get("usage.admin"));
        }
        return true;
    }

    // ===== 玩家命令（自管模式：以"拥有庄园"判定归属公会）=====

    /** /gs home：传送到自己庄园（优先用 sethome 坐标，否则实占中心）。 */
    private void home(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor_hint"));
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
            // 默认：庄园的【锚定角】（最小角那个 chunk 的中心）。与庄园"从角落向外扩"一致，
            // 该点固定不随升级漂移、且 1 级就已解锁/整好地——比"实占中心"(随等级移动)更稳。
            ChunkRegion active = new LayoutCalculator(gw.layout())
                    .activeRegion(manor.slot(), manor.level())
                    .shift(gw.originChunkX(), gw.originChunkZ());
            cx = active.minBlockX() + 8;
            cz = active.minBlockZ() + 8;
            world.loadChunk(cx >> 4, cz >> 4, true);
            cy = world.getHighestBlockYAt(cx, cz) + 1;
        }
        world.loadChunk(cx >> 4, cz >> 4, true);
        player.teleport(new Location(world, cx + 0.5, cy, cz + 0.5));
        sender.sendMessage(Messages.get("success.home_teleported", manor.slot()));
    }

    /** /gs spawn：传送到自己公会的主城。 */
    private void spawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_guild_joined"));
            return;
        }
        GuildWorld gw = ensureLoadedWorld(sender, manor.guild());
        if (gw == null) {
            return;
        }
        player.teleport(worlds.safeSpawn(org.bukkit.Bukkit.getWorld(gw.worldName()), gw));
        sender.sendMessage(Messages.get("success.spawn_teleported"));
    }

    /** /gs upgrade：升级自己的庄园一级（只受物理满级限制，成员自己的事）。 */
    private void upgrade(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        Manor manor = manors.findByOwnerAnywhere(ref).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        int cap = levels.manorMaxLevel();
        if (service.upgradeManor(manor.guild(), ref)) {
            Manor now = manors.findByOwnerAnywhere(ref).orElse(manor);
            sender.sendMessage(Messages.get("success.upgraded", now.level(), cap));
            // 升级小特效：脚下冒经验值粒子
            player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING,
                    player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0);
        } else {
            sender.sendMessage(Messages.get("error.already_max_level", cap));
        }
    }

    /** /gs info：显示自己的庄园 + 公会信息。 */
    private void info(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        GuildWorld gw = guilds.find(manor.guild()).orElse(null);
        if (gw == null) {
            sender.sendMessage(Messages.get("error.world_not_exist"));
            return;
        }
        int side = gw.layout().plotChunksByLevel(manor.level()) * 16;
        int capacity = service.effectiveCapacity(gw); // 与发地口径一致(宿主有上限跟宿主)
        int members = manors.findAll(manor.guild()).size();
        sender.sendMessage(Messages.get("info.guild_info_header"));
        String alias = Flag.ALIAS.resolveString(manor.flags());
        String title = alias.isBlank() ? "庄园 #" + manor.slot() : alias + " (#" + manor.slot() + ")";
        sender.sendMessage(Messages.get("info.guild_line", manor.guild().value(), gw.guildLevel(), levels.maxGuildLevel(), members, capacity));
        sender.sendMessage(Messages.get("info.plot_line", title, manor.level(), levels.manorMaxLevel(), side, side,
                Flag.DONE.resolveBool(manor.flags()) ? Messages.get("info.done_status") : Messages.get("info.building_status")));
        sender.sendMessage(Messages.get("info.trusted_line", sizeOrNone(manor.coBuilders()), sizeOrNone(manor.members()), sizeOrNone(manor.denied())));
        String desc = Flag.DESCRIPTION.resolveString(manor.flags());
        if (!desc.isBlank()) {
            sender.sendMessage(Messages.get("info.card_desc", desc));
        }
        double price = Flag.PRICE.resolveDouble(manor.flags());
        if (price > 0) {
            sender.sendMessage(Messages.get("info.card_price", price));
        }
        String blocked = Flag.BLOCKED_CMDS.resolveString(manor.flags());
        if (!blocked.isBlank()) {
            sender.sendMessage(Messages.get("info.blocked_cmds_line", blocked.replace(",", " /")));
        }
        if (Flag.KEEP.resolveBool(manor.flags())) {
            sender.sendMessage(Messages.get("info.keep_line"));
        }
    }

    private static String sizeOrNone(Set<PlayerRef> set) {
        return set.isEmpty() ? "§8无" : set.size() + " 人";
    }

    /** /gs trust <玩家|*>：给自己的庄园加共建人（* = 批量，需 trust.everyone 权限）。 */
    private void trust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.trust"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        // 批量 trust *
        if (args[1].equals("*")) {
            if (!player.hasPermission(Permissions.TRUST_EVERYONE)
                    && !Permissions.hasAdminPerm(player, Permissions.ADMIN_TRUST_OTHER)) {
                sender.sendMessage(Messages.get("error.batch_trust_perm", Permissions.TRUST_EVERYONE));
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
                sender.sendMessage(Messages.get("error.no_need_trust"));
                return;
            }
            manors.save(manor);
            sender.sendMessage(Messages.get("success.trust_batch", count, manor.slot()));
            return;
        }
        org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage(Messages.get("error.cannot_self"));
            return;
        }
        PlayerRef tref = PlayerRef.of(target.getUniqueId());
        Set<PlayerRef> co = new HashSet<>(manor.coBuilders());
        if (!co.add(tref)) {
            sender.sendMessage(Messages.get("success.trust_added_already", args[1]));
            return;
        }
        // 身份互斥：升为 trusted 时清除其 member/denied 身份。
        Set<PlayerRef> members = new HashSet<>(manor.members());
        Set<PlayerRef> denied = new HashSet<>(manor.denied());
        members.remove(tref);
        denied.remove(tref);
        manors.save(manor.withCoBuilders(co).withMembers(members).withDenied(denied));
        sender.sendMessage(Messages.get("success.trust_added", args[1], manor.slot()));
    }

    /** /gs untrust <玩家>：移除共建人。 */
    private void untrust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.untrust"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
        Set<PlayerRef> co = new HashSet<>(manor.coBuilders());
        if (!co.remove(PlayerRef.of(target.getUniqueId()))) {
            sender.sendMessage(Messages.get("success.trust_removed_not", args[1]));
            return;
        }
        manors.save(manor.withCoBuilders(co));
        sender.sendMessage(Messages.get("success.trust_removed", args[1]));
    }

    /** /gs member <add|remove> <玩家>：管理受限成员（仅 owner/trusted 在线时才有建造/交互权）。 */
    private void member(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (args.length < 3 || (!args[1].equalsIgnoreCase("add") && !args[1].equalsIgnoreCase("remove"))) {
            sender.sendMessage(Messages.get("usage.member"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[2]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage(Messages.get("error.cannot_self"));
            return;
        }
        PlayerRef tref = PlayerRef.of(target.getUniqueId());
        Set<PlayerRef> members = new HashSet<>(manor.members());
        if (args[1].equalsIgnoreCase("add")) {
            if (!members.add(tref)) {
                sender.sendMessage(Messages.get("success.member_added_already", args[2]));
                return;
            }
            // 身份互斥：设为 member 时清除其 trusted/denied 身份。
            Set<PlayerRef> co = new HashSet<>(manor.coBuilders());
            Set<PlayerRef> denied = new HashSet<>(manor.denied());
            co.remove(tref);
            denied.remove(tref);
            manors.save(manor.withCoBuilders(co).withMembers(members).withDenied(denied));
            sender.sendMessage(Messages.get("success.member_added", args[2]));
        } else {
            if (!members.remove(tref)) {
                sender.sendMessage(Messages.get("success.member_removed_not", args[2]));
                return;
            }
            manors.save(manor.withMembers(members));
            sender.sendMessage(Messages.get("success.member_removed", args[2]));
        }
    }

    /** /gs deny <玩家|*>：拉黑（* = 批量，需 deny.everyone 权限；同时清除共建人/成员身份）。 */
    private void deny(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.deny"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        // 批量 deny *
        if (args[1].equals("*")) {
            if (!player.hasPermission(Permissions.DENY_EVERYONE)
                    && !Permissions.hasAdminPerm(player, Permissions.ADMIN_TRUST_OTHER)) {
                sender.sendMessage(Messages.get("error.batch_deny_perm", Permissions.DENY_EVERYONE));
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
                sender.sendMessage(Messages.get("error.no_need_deny"));
                return;
            }
            manors.save(manor);
            sender.sendMessage(Messages.get("success.denied_batch", count, manor.slot()));
            return;
        }
        org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage(Messages.get("error.cannot_self_deny"));
            return;
        }
        PlayerRef tref = PlayerRef.of(target.getUniqueId());
        Set<PlayerRef> denied = new HashSet<>(manor.denied());
        if (!denied.add(tref)) {
            sender.sendMessage(Messages.get("success.denied_already", args[1]));
            return;
        }
        // 拉黑时清除其共建人/成员身份，避免身份冲突。
        Set<PlayerRef> co = new HashSet<>(manor.coBuilders());
        Set<PlayerRef> members = new HashSet<>(manor.members());
        co.remove(tref);
        members.remove(tref);
        manors.save(manor.withCoBuilders(co).withMembers(members).withDenied(denied));
        sender.sendMessage(Messages.get("success.denied_added", args[1], manor.slot()));
    }

    /** /gs undeny <玩家>：移出黑名单。 */
    private void undeny(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.undeny"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
        Set<PlayerRef> denied = new HashSet<>(manor.denied());
        if (!denied.remove(PlayerRef.of(target.getUniqueId()))) {
            sender.sendMessage(Messages.get("success.denied_removed_not", args[1]));
            return;
        }
        manors.save(manor.withDenied(denied));
        sender.sendMessage(Messages.get("success.denied_removed", args[1]));
    }

    /** /gs list [mine]：列出公会营地（mine=只看自己有庄园的）。 */
    private void list(CommandSender sender, String[] args) {
        List<GuildWorld> all = guilds.findAll();
        boolean mineOnly = args.length >= 2 && args[1].equalsIgnoreCase("mine");
        if (mineOnly) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.get("error.console_cannot_mine"));
                return;
            }
            PlayerRef myRef = PlayerRef.of(player.getUniqueId());
            all = all.stream()
                    .filter(gw -> manors.findByOwner(gw.guild(), myRef).isPresent())
                    .toList();
        }
        if (all.isEmpty()) {
            sender.sendMessage(Messages.get(mineOnly ? "error.no_guild_joined" : "info.no_guilds"));
            return;
        }
        sender.sendMessage(Messages.get("info.guild_list_header", mineOnly ? "我的" : "", all.size()));
        for (GuildWorld gw : all) {
            int members = manors.findAll(gw.guild()).size();
            int cap = levels.maxMembers(gw.guildLevel());
            sender.sendMessage(Messages.get("info.list_entry", gw.guild().value(), gw.guildLevel(), members, cap, gw.guild().value()));
        }
    }

    /** /gs visit <公会>：到访某公会的主城。 */
    private void visit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.visit"));
            return;
        }
        GuildId guild = new GuildId(args[1]);
        GuildWorld gw = guilds.find(guild).orElse(null);
        if (gw == null) {
            sender.sendMessage(Messages.get("error.guild_not_exist", guild.value()));
            return;
        }
        // 跨服检查：目标世界在别的服务器上
        if (proxyChannel.isAvailable() && !gw.serverName().isEmpty() && !gw.serverName().equals(serverName)) {
            proxyChannel.sendToServer(player, gw.serverName());
            sender.sendMessage(Messages.get("success.cross_server", gw.serverName()));
            return;
        }
        gw = worlds.ensureWorld(gw);
        guilds.save(gw);
        registry.register(gw);
        World world = Bukkit.getWorld(gw.worldName());
        if (world == null) {
            sender.sendMessage(Messages.get("error.world_load_failed", gw.worldName()));
            return;
        }
        player.teleport(worlds.safeSpawn(world, gw));
        sender.sendMessage(Messages.get("success.visit_teleported", guild.value()));
    }

    /** /gs clear：清空自己庄园的地表建筑。 */
    private void clear(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        Manor manor = manors.findByOwnerAnywhere(ref).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        service.clearManor(manor.guild(), ref);
        sender.sendMessage(Messages.get("success.cleared", manor.slot()));
    }

    /** /gs flag [set|unset <flag> [值] | (空)查看]：管理庄园 flag（庄主 / 有 per-flag 权限者）。 */
    private void flag(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        Manor manor = manors.findByOwnerAnywhere(ref).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor_for_command"));
            return;
        }
        boolean isOwner = manor.owner().equals(ref);
        String action = args.length >= 2 ? args[1].toLowerCase() : "";
        switch (action) {
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(Messages.get("usage.flag_set"));
                    return;
                }
                Flag f = Flag.byId(args[2]).orElse(null);
                if (f == null) {
                    sender.sendMessage(Messages.get("error.unknown_flag", args[2]));
                    return;
                }
                // 权限检查：庄主直接放行；trusted 可设交互类 flag；否则需 per-flag 权限或 admin
                boolean isTrusted = manor.coBuilders().contains(ref);
                if (!isOwner
                        && !(isTrusted && TRUSTED_FLAG_SET.contains(f.id()))
                        && !player.hasPermission(Permissions.flagSet(f.id()))
                        && !Permissions.hasAdminPerm(player, Permissions.ADMIN_FLAG_OTHER)) {
                    sender.sendMessage(Messages.get("error.flag_set_perm", Permissions.flagSet(f.id())));
                    return;
                }
                // 字符串型(greeting/farewell)取后面所有词；布尔/整数取单个。
                String raw = f.type() == FlagType.STRING
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length))
                        : args[3];
                String value = f.normalize(raw).orElse(null);
                if (value == null) {
                    sender.sendMessage(Messages.get("error.invalid_value"));
                    return;
                }
                Map<String, String> flags = new HashMap<>(manor.flags());
                flags.put(f.id(), value);
                manors.save(manor.withFlags(flags));
                sender.sendMessage(Messages.get("success.flag_set", f.id(), value, manor.slot()));
            }
            case "unset" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.get("usage.flag_unset"));
                    return;
                }
                Flag f = Flag.byId(args[2]).orElse(null);
                if (f == null) {
                    sender.sendMessage(Messages.get("error.unknown_flag", args[2]));
                    return;
                }
                // 权限检查：同 set
                boolean isTrustedUnset = manor.coBuilders().contains(ref);
                if (!isOwner
                        && !(isTrustedUnset && TRUSTED_FLAG_SET.contains(f.id()))
                        && !player.hasPermission(Permissions.flagSet(f.id()))
                        && !Permissions.hasAdminPerm(player, Permissions.ADMIN_FLAG_OTHER)) {
                    sender.sendMessage(Messages.get("error.flag_set_perm", Permissions.flagSet(f.id())));
                    return;
                }
                Map<String, String> flags = new HashMap<>(manor.flags());
                if (flags.remove(f.id()) == null) {
                    sender.sendMessage(Messages.get("error.flag_already_default", f.defaultValue()));
                    return;
                }
                manors.save(manor.withFlags(flags));
                sender.sendMessage(Messages.get("success.flag_unset", f.id(), f.defaultValue()));
            }
            default -> {
                sender.sendMessage(Messages.get("info.flag_header", manor.slot()));
                sender.sendMessage(Messages.get("info.flag_usage"));
                for (Flag f : Flag.values()) {
                    String cur = manor.flags().get(f.id());
                    String shown = cur != null ? "§f" + cur : "§8" + f.defaultValue() + "(默认)";
                    sender.sendMessage(Messages.get("info.flag_entry", f.id(), shown, f.description()));
                }
            }
        }
    }

    /** /gs card [玩家]：展示庄园档案卡（基础信息+实体统计+成员+描述+评分）。 */
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
            sender.sendMessage(Messages.get("error.console_need_player"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(targetRef).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.target_no_manor", targetName));
            return;
        }
        GuildWorld gw = guilds.find(manor.guild()).orElse(null);
        if (gw == null) {
            sender.sendMessage(Messages.get("error.world_not_exist"));
            return;
        }

        int side = gw.layout().plotChunksByLevel(manor.level()) * 16;
        int capacity = service.effectiveCapacity(gw); // 与发地口径一致(宿主有上限跟宿主)
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

        sender.sendMessage(Messages.get("info.card_header"));
        sender.sendMessage(Messages.get("info.card_plot", title, manor.guild().value(), gw.guildLevel(),
                done ? Messages.get("info.done_status") : Messages.get("info.building_status")));
        sender.sendMessage(Messages.get("info.card_owner", targetName, manor.level(), levels.manorMaxLevel(), side, side));
        if (!alias.isBlank()) {
            sender.sendMessage(Messages.get("info.card_alias", alias));
        }
        if (!desc.isBlank()) {
            sender.sendMessage(Messages.get("info.card_desc", desc));
        }
        sender.sendMessage(Messages.get("info.card_entities", entityLine));
        sender.sendMessage(Messages.get("info.card_members", memberCount, capacity, manor.coBuilders().size(), manor.denied().size()));
        sender.sendMessage(Messages.get("info.card_flags", activeFlags));
        double price = Flag.PRICE.resolveDouble(manor.flags());
        if (price > 0) {
            sender.sendMessage(Messages.get("info.card_price", price));
        }
        sender.sendMessage(Messages.get("info.card_footer"));
        sender.sendMessage(Messages.get("info.card_score_line", score));
        sender.sendMessage(Messages.get("info.card_bottom"));
    }

    // ===== 新增命令：alias / sethome / done / kick =====

    /** /gs alias <名称>：设置庄园别名（空参清除）。 */
    private void alias(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        String name = args.length >= 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : "";
        if (name.length() > 50) {
            sender.sendMessage(Messages.get("error.too_long", 50));
            return;
        }
        Map<String, String> flags = new HashMap<>(manor.flags());
        if (name.isBlank()) {
            flags.remove(Flag.ALIAS.id());
            manors.save(manor.withFlags(flags));
            sender.sendMessage(Messages.get("success.alias_cleared"));
        } else {
            flags.put(Flag.ALIAS.id(), name.replace(';', ','));
            manors.save(manor.withFlags(flags));
            sender.sendMessage(Messages.get("success.alias_set", name));
        }
    }

    /** /gs sethome：把当前位置设为 /gs home 的传送点。 */
    private void sethome(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        // 检查是否在自己的庄园上
        GuildWorld gw = registry.get(player.getWorld().getName());
        if (gw == null || !gw.guild().equals(manor.guild())) {
            sender.sendMessage(Messages.get("error.not_in_own_world"));
            return;
        }
        Location loc = player.getLocation();
        Map<String, String> flags = new HashMap<>(manor.flags());
        flags.put(Flag.HOME_X.id(), Integer.toString(loc.getBlockX()));
        flags.put(Flag.HOME_Y.id(), Integer.toString(loc.getBlockY()));
        flags.put(Flag.HOME_Z.id(), Integer.toString(loc.getBlockZ()));
        manors.save(manor.withFlags(flags));
        sender.sendMessage(Messages.get("success.home_set", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    /** /gs done：切换庄园完工标记。 */
    private void done(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        boolean current = Flag.DONE.resolveBool(manor.flags());
        Map<String, String> flags = new HashMap<>(manor.flags());
        flags.put(Flag.DONE.id(), Boolean.toString(!current));
        manors.save(manor.withFlags(flags));
        if (current) {
            sender.sendMessage(Messages.get("success.done_off"));
        } else {
            sender.sendMessage(Messages.get("success.done_on"));
        }
    }

    /** /gs kick <玩家>：把非成员从你的庄园上踢出去（传送到边界外）。 */
    private void kick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.kick"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Messages.get("error.player_offline", args[1]));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage(Messages.get("error.cannot_self_kick"));
            return;
        }
        // 检查目标是否在自己的庄园上
        GuildWorld gw = registry.get(target.getWorld().getName());
        if (gw == null || !gw.guild().equals(manor.guild())) {
            sender.sendMessage(Messages.get("error.not_guild_world_target", target.getName()));
            return;
        }
        PlayerRef targetRef = PlayerRef.of(target.getUniqueId());
        // 成员不能踢（用 deny 拉黑）
        if (ManorRoles.isMemberOrAbove(manor, targetRef)) {
            sender.sendMessage(Messages.get("error.is_member_cannot_kick", target.getName()));
            return;
        }
        // 传送到公会主城
        World world = Bukkit.getWorld(gw.worldName());
        if (world != null) {
            target.teleport(worlds.safeSpawn(world, gw));
            target.sendMessage(Messages.get("success.kicked_notify", player.getName(), manor.slot()));
            sender.sendMessage(Messages.get("success.kicked", target.getName()));
        }
    }

    // ===== help / desc / reload / setowner =====

    /** /gs help [命令]：显示帮助（分类展示，走 i18n）。 */
    private void help(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            String cmd = args[1].toLowerCase();
            // 模糊匹配：去掉参数部分只比较子命令名
            String desc = COMMAND_HELP.get(cmd);
            if (desc == null) {
                for (var e : COMMAND_HELP.entrySet()) {
                    if (e.getKey().startsWith(cmd) || e.getKey().split(" ")[0].equals(cmd)) {
                        desc = e.getValue();
                        break;
                    }
                }
            }
            if (desc != null) {
                sender.sendMessage(Messages.get("info.help_cmd", cmd, desc));
            } else {
                sender.sendMessage(Messages.get("info.help_unknown", cmd));
            }
            return;
        }
        boolean isAdmin = sender.isOp() || Permissions.hasAdminPerm(sender, Permissions.ADMIN);
        sender.sendMessage(Messages.get("info.help_header"));
        sender.sendMessage(Messages.get("info.help_cat_teleport"));
        helpLine(sender, "home"); helpLine(sender, "spawn"); helpLine(sender, "middle");
        helpLine(sender, "sethome"); helpLine(sender, "visit");
        sender.sendMessage(Messages.get("info.help_cat_info"));
        helpLine(sender, "info"); helpLine(sender, "card"); helpLine(sender, "near");
        helpLine(sender, "list"); helpLine(sender, "board"); helpLine(sender, "top");
        sender.sendMessage(Messages.get("info.help_cat_people"));
        helpLine(sender, "trust"); helpLine(sender, "untrust"); helpLine(sender, "member");
        helpLine(sender, "deny"); helpLine(sender, "undeny"); helpLine(sender, "kick");
        sender.sendMessage(Messages.get("info.help_cat_settings"));
        helpLine(sender, "flag"); helpLine(sender, "flag_set"); helpLine(sender, "alias");
        helpLine(sender, "desc"); helpLine(sender, "done"); helpLine(sender, "toggle");
        helpLine(sender, "open"); helpLine(sender, "close");
        sender.sendMessage(Messages.get("info.help_cat_social"));
        helpLine(sender, "comment"); helpLine(sender, "inbox"); helpLine(sender, "rate");
        helpLine(sender, "flower"); helpLine(sender, "gift"); helpLine(sender, "bulletin");
        sender.sendMessage(Messages.get("info.help_cat_advanced"));
        helpLine(sender, "upgrade"); helpLine(sender, "clear"); helpLine(sender, "swap");
        helpLine(sender, "merge"); helpLine(sender, "unmerge"); helpLine(sender, "move");
        helpLine(sender, "template"); helpLine(sender, "sub"); helpLine(sender, "confirm");
        if (isAdmin) {
            sender.sendMessage(Messages.get("info.help_cat_admin"));
            helpLine(sender, "admin_create"); helpLine(sender, "admin_tp"); helpLine(sender, "admin_claim");
            helpLine(sender, "admin_fill"); helpLine(sender, "admin_map");
            helpLine(sender, "admin_upgrade_manor"); helpLine(sender, "admin_upgrade_guild");
            helpLine(sender, "admin_delete"); helpLine(sender, "admin_worlds");
            helpLine(sender, "admin_whereami"); helpLine(sender, "admin_reload");
            helpLine(sender, "admin_setowner"); helpLine(sender, "admin_purge");
            helpLine(sender, "admin_regen"); helpLine(sender, "admin_export");
            helpLine(sender, "admin_fund"); helpLine(sender, "admin_citywall");
        }
        sender.sendMessage(Messages.get("info.help_footer"));
    }

    /** 输出一行帮助条目（从 i18n 取，不存在则静默跳过）。 */
    private void helpLine(CommandSender sender, String key) {
        String msg = Messages.get("info.help_ln_" + key);
        // Messages.get 找不到时返回 key 本身，跳过
        if (msg.startsWith("info.help_ln_")) return;
        sender.sendMessage(msg);
    }

    /** 命令帮助文本表（按分类有序）。 */
    private static final Map<String, String> COMMAND_HELP = new java.util.LinkedHashMap<>();
    static {
        // ── 传送 ──
        COMMAND_HELP.put("home", "传送到自己庄园（优先 sethome 坐标）");
        COMMAND_HELP.put("spawn", "传送到公会主城");
        COMMAND_HELP.put("middle", "传送到庄园正中心（无视 sethome）");
        COMMAND_HELP.put("sethome", "把当前位置设为 home 传送点");
        COMMAND_HELP.put("visit <公会>", "到访某公会的主城");
        // ── 庄园信息 ──
        COMMAND_HELP.put("info", "查看自己庄园+公会信息");
        COMMAND_HELP.put("card [玩家]", "查看庄园档案卡（实体/成员/评分）");
        COMMAND_HELP.put("near", "列出附近庄园（按距离排序）");
        COMMAND_HELP.put("list [mine]", "列出所有公会营地（mine=只看自己的）");
        COMMAND_HELP.put("board", "查看脚下庄园的留言墙");
        COMMAND_HELP.put("top [公会] [排序]", "排行榜（rating/level/members/entities/visits）");
        // ── 人员管理 ──
        COMMAND_HELP.put("trust <玩家|*>", "加共建人（可建造/交互，*=批量）");
        COMMAND_HELP.put("untrust <玩家>", "移除共建人");
        COMMAND_HELP.put("citytrust <玩家>", "会长信任会内成员建造公会主城");
        COMMAND_HELP.put("cityuntrust <玩家>", "撤销会内成员的主城建造信任");
        COMMAND_HELP.put("member <add|remove> <玩家>", "管理受限成员（上级在线时才有权）");
        COMMAND_HELP.put("deny <玩家|*>", "拉黑（禁止进入，*=批量，需确认）");
        COMMAND_HELP.put("undeny <玩家>", "移出黑名单");
        COMMAND_HELP.put("kick <玩家>", "把非成员踢出你的庄园");
        // ── 庄园设置 ──
        COMMAND_HELP.put("flag [set|unset] [flag] [值]", "查看/设置庄园 flag");
        COMMAND_HELP.put("alias <名称>", "设置庄园别名（空参清除）");
        COMMAND_HELP.put("desc <描述>", "设置庄园描述（空参清除）");
        COMMAND_HELP.put("done", "切换完工标记");
        COMMAND_HELP.put("toggle titles", "个人开关进出标题消息");
        COMMAND_HELP.put("open [分钟]", "临时开放庄园给访客（0=永久，默认60分钟）");
        COMMAND_HELP.put("close", "关闭庄园访客模式");
        // ── 社交 ──
        COMMAND_HELP.put("comment <留言>", "给当前所在庄园留言");
        COMMAND_HELP.put("inbox", "查看自己庄园收到的留言");
        COMMAND_HELP.put("rate <1-10> [公会 slot]", "给庄园打分");
        COMMAND_HELP.put("flower [公会 slot]", "给庄园送花（每天每块限一次）");
        COMMAND_HELP.put("gift <玩家>", "把手持物品送给同世界的玩家");
        COMMAND_HELP.put("bulletin <set|show|clear>", "公会公告板管理");
        // ── 高级 ──
        COMMAND_HELP.put("upgrade", "升级自己的庄园一级");
        COMMAND_HELP.put("clear", "清空自己庄园的地表建筑（需确认）");
        COMMAND_HELP.put("swap <玩家>", "与对方互换庄园 slot（需确认）");
        COMMAND_HELP.put("merge <slot>", "合并相邻庄园到自己的庄园（需确认）");
        COMMAND_HELP.put("unmerge [slot]", "取消合并（不填=全部）（需确认）");
        COMMAND_HELP.put("move <公会>", "搬家到另一个公会（保留建筑，需确认）");
        COMMAND_HELP.put("template <子命令>", "权限模板管理（create/delete/apply/setflag/list）");
        COMMAND_HELP.put("sub <子命令>", "子领地管理（create/delete/setflag/list）");
        COMMAND_HELP.put("confirm", "确认待执行的危险操作");
        // ── 管理命令 ──
        COMMAND_HELP.put("admin create <公会> [地形]", "创建公会营地");
        COMMAND_HELP.put("admin tp <公会>", "传送到公会主城");
        COMMAND_HELP.put("admin claim <公会>", "给自己分配一块庄园");
        COMMAND_HELP.put("admin fill <公会> <数量>", "批量填充测试庄园");
        COMMAND_HELP.put("admin map <公会>", "输出网格图到控制台");
        COMMAND_HELP.put("admin upgrade-manor <公会>", "升级自己庄园一级");
        COMMAND_HELP.put("admin upgrade-guild <公会>", "升级公会等级（扩名额+边界）");
        COMMAND_HELP.put("admin delete <公会>", "卸载公会营地");
        COMMAND_HELP.put("admin worlds", "列出所有已加载世界");
        COMMAND_HELP.put("admin whereami", "显示当前坐标");
        COMMAND_HELP.put("admin reload", "热重载 config.yml");
        COMMAND_HELP.put("admin setowner <公会> <玩家>", "转移庄园所有权");
        COMMAND_HELP.put("admin purge <天数> [公会]", "清理闲置庄园");
        COMMAND_HELP.put("admin regen", "重置脚下庄园地形");
        COMMAND_HELP.put("admin export", "导出数据到 CSV");
        COMMAND_HELP.put("admin fund <公会> <add|check|set> [金额]", "管理公会资金");
        COMMAND_HELP.put("admin citywall <公会>", "为主城补建围墙");
        // ── 通用 ──
        COMMAND_HELP.put("help [命令]", "显示帮助（加命令名看详细用法）");
    }

    /** /gs desc <描述>：快捷设置庄园描述（等效 /gs flag set description）。 */
    private void desc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        String text = args.length >= 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : "";
        Map<String, String> flags = new HashMap<>(manor.flags());
        if (text.isBlank()) {
            flags.remove(Flag.DESCRIPTION.id());
            manors.save(manor.withFlags(flags));
            sender.sendMessage(Messages.get("success.desc_cleared"));
        } else {
            flags.put(Flag.DESCRIPTION.id(), text.replace(';', ','));
            manors.save(manor.withFlags(flags));
            sender.sendMessage(Messages.get("success.desc_set", text));
        }
    }

    /** /gs toggle titles：个人开关进出标题消息。 */
    private void toggle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.toggle"));
            return;
        }
        String what = args[1].toLowerCase();
        if ("titles".equals(what)) {
            if (accessListener == null) {
                sender.sendMessage(Messages.get("error.titles_not_enabled"));
                return;
            }
            boolean now = accessListener.toggleTitles(player.getUniqueId());
            sender.sendMessage(now ? "§a已开启进出标题消息。" : "§e已关闭进出标题消息（改为聊天框显示）。");
        } else {
            sender.sendMessage(Messages.get("error.toggle_unknown"));
        }
    }

    /** /gs template <create|delete|apply|setflag|list> ...：权限模板管理。 */
    private void template(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        if (!manor.owner().equals(PlayerRef.of(player.getUniqueId()))) {
            sender.sendMessage(Messages.get("error.only_owner_template"));
            return;
        }
        GuildId guild = manor.guild();
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.template"));
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "save" -> {
                if (schematicStore == null) { sender.sendMessage(Messages.get("error.no_worldedit")); return; }
                if (args.length < 3) { sender.sendMessage(Messages.get("usage.template_save")); return; }
                String name = args[2].toLowerCase();
                if (!name.matches("[a-zA-Z0-9_\\-]+")) { sender.sendMessage(Messages.get("error.invalid_name")); return; }
                GuildWorld gw = guilds.find(guild).orElse(null);
                if (gw == null) { sender.sendMessage(Messages.get("error.world_not_exist")); return; }
                LayoutCalculator layout = new LayoutCalculator(gw.layout());
                ChunkRegion active = layout.activeRegion(manor.slot(), manor.level())
                        .shift(gw.originChunkX(), gw.originChunkZ());
                int minY = player.getWorld().getMinHeight();
                int maxY = player.getWorld().getMaxHeight();
                var path = schematicStore.save(gw.worldName(), name, active.minBlockX(), minY, active.minBlockZ(),
                        active.maxBlockX() + 15, maxY, active.maxBlockZ() + 15);
                sender.sendMessage(path != null ? Messages.get("success.template_saved", name) : Messages.get("error.template_save_failed"));
            }
            case "paste" -> {
                if (schematicStore == null) { sender.sendMessage(Messages.get("error.no_worldedit")); return; }
                if (args.length < 3) { sender.sendMessage(Messages.get("usage.template_paste")); return; }
                String name = args[2].toLowerCase();
                GuildWorld gw = guilds.find(guild).orElse(null);
                if (gw == null) { sender.sendMessage(Messages.get("error.world_not_exist")); return; }
                LayoutCalculator layout = new LayoutCalculator(gw.layout());
                ChunkRegion active = layout.activeRegion(manor.slot(), manor.level())
                        .shift(gw.originChunkX(), gw.originChunkZ());
                int x = active.minBlockX(), z = active.minBlockZ();
                int y = player.getWorld().getHighestBlockYAt(x, z);
                boolean async = org.bukkit.Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
                schematicStore.paste(gw.worldName(), name, x, y, z, async);
                sender.sendMessage(Messages.get("success.template_pasted", name));
            }
            case "list-schematics" -> {
                if (schematicStore == null) { sender.sendMessage(Messages.get("error.no_worldedit")); return; }
                var schematics = schematicStore.list();
                if (schematics.isEmpty()) { sender.sendMessage(Messages.get("info.no_schematics")); return; }
                sender.sendMessage(Messages.get("info.schematics_header"));
                for (String n : schematics) sender.sendMessage(Messages.get("info.schematics_entry", n));
            }
            case "create" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.get("usage.template_create"));
                    return;
                }
                String name = args[2].toLowerCase();
                if (!name.matches("[a-zA-Z0-9_\\-]+")) {
                    sender.sendMessage(Messages.get("error.invalid_name"));
                    return;
                }
                if (manors.getTemplate(guild, name).isPresent()) {
                    sender.sendMessage(Messages.get("error.template_already_exist", name));
                    return;
                }
                manors.saveTemplate(guild, name, Map.of());
                sender.sendMessage(Messages.get("success.template_created", name, name));
            }
            case "delete" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.get("usage.template_delete"));
                    return;
                }
                manors.deleteTemplate(guild, args[2].toLowerCase());
                sender.sendMessage(Messages.get("success.template_deleted", args[2]));
            }
            case "setflag" -> {
                if (args.length < 5) {
                    sender.sendMessage(Messages.get("usage.template_setflag"));
                    return;
                }
                String name = args[2].toLowerCase();
                java.util.Map<String, String> tpl = manors.getTemplate(guild, name).orElse(null);
                if (tpl == null) {
                    sender.sendMessage(Messages.get("error.template_not_exist", name));
                    return;
                }
                Flag f = Flag.byId(args[3]).orElse(null);
                if (f == null) {
                    sender.sendMessage(Messages.get("error.unknown_flag", args[3]));
                    return;
                }
                String value = f.normalize(args[4]).orElse(null);
                if (value == null) {
                    sender.sendMessage(Messages.get("error.invalid_value"));
                    return;
                }
                java.util.Map<String, String> updated = new java.util.HashMap<>(tpl);
                updated.put(f.id(), value);
                manors.saveTemplate(guild, name, updated);
                sender.sendMessage(Messages.get("success.template_flag_set", name, f.id(), value));
            }
            case "apply" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.get("usage.template_apply"));
                    return;
                }
                String name = args[2].toLowerCase();
                java.util.Map<String, String> tpl = manors.getTemplate(guild, name).orElse(null);
                if (tpl == null) {
                    sender.sendMessage(Messages.get("error.template_not_exist", name));
                    return;
                }
                java.util.Map<String, String> flags = new java.util.HashMap<>(manor.flags());
                flags.putAll(tpl);
                manors.save(manor.withFlags(flags));
                sender.sendMessage(Messages.get("success.template_applied", name, tpl.size(), manor.slot()));
            }
            case "list" -> {
                java.util.List<String> names = manors.listTemplates(guild);
                if (names.isEmpty()) {
                    sender.sendMessage(Messages.get("error.no_template_yet"));
                    return;
                }
                sender.sendMessage(Messages.get("info.template_header"));
                for (String n : names) {
                    java.util.Map<String, String> tpl = manors.getTemplate(guild, n).orElse(Map.of());
                    sender.sendMessage(Messages.get("info.template_entry", n, tpl.size()));
                }
            }
            default -> sender.sendMessage(Messages.get("usage.template"));
        }
    }

    /** /gs sub <create|delete|setflag|list> ...：子领地管理。 */
    private void sub(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null || !manor.owner().equals(PlayerRef.of(player.getUniqueId()))) {
            sender.sendMessage(Messages.get("error.only_owner_sub"));
            return;
        }
        GuildId guild = manor.guild();
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.sub"));
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "create" -> {
                if (args.length < 7) {
                    sender.sendMessage(Messages.get("usage.sub_create"));
                    sender.sendMessage(Messages.get("usage.sub_create_hint"));
                    return;
                }
                String name = args[2].toLowerCase();
                int bx = player.getLocation().getBlockX();
                int bz = player.getLocation().getBlockZ();
                int dx1, dz1, dx2, dz2;
                try {
                    dx1 = Integer.parseInt(args[3]);
                    dz1 = Integer.parseInt(args[4]);
                    dx2 = Integer.parseInt(args[5]);
                    dz2 = Integer.parseInt(args[6]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Messages.get("error.offset_must_be_int"));
                    return;
                }
                int minX = Math.min(bx + dx1, bx + dx2);
                int minZ = Math.min(bz + dz1, bz + dz2);
                int maxX = Math.max(bx + dx1, bx + dx2);
                int maxZ = Math.max(bz + dz1, bz + dz2);
                // 边界检查：sub 必须在自己的庄园实占范围内
                GuildWorld subGw = guilds.find(manor.guild()).orElse(null);
                if (subGw == null) {
                    sender.sendMessage(Messages.get("error.world_not_exist"));
                    return;
                }
                LayoutCalculator subLayout = new LayoutCalculator(subGw.layout());
                ChunkRegion active = subLayout.activeRegion(manor.slot(), manor.level())
                        .shift(subGw.originChunkX(), subGw.originChunkZ());
                int aMinX = active.minBlockX();
                int aMaxX = active.maxBlockX() + 15;
                int aMinZ = active.minBlockZ();
                int aMaxZ = active.maxBlockZ() + 15;
                if (minX < aMinX || maxX > aMaxX || minZ < aMinZ || maxZ > aMaxZ) {
                    sender.sendMessage(Messages.get("error.sub_out_of_bounds"));
                    return;
                }
                // 名称校验：只允许字母数字下划线横杠
                if (!name.matches("[a-zA-Z0-9_\\-]+")) {
                    sender.sendMessage(Messages.get("error.invalid_name"));
                    return;
                }
                manors.saveSub(guild, manor.slot(), name, minX, minZ, maxX, maxZ, Map.of());
                sender.sendMessage(Messages.get("success.sub_created", name, maxX - minX + 1, maxZ - minZ + 1));
            }
            case "delete" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.get("usage.sub_delete"));
                    return;
                }
                manors.deleteSub(guild, manor.slot(), args[2].toLowerCase());
                sender.sendMessage(Messages.get("success.sub_deleted", args[2]));
            }
            case "setflag" -> {
                if (args.length < 5) {
                    sender.sendMessage(Messages.get("usage.sub_setflag"));
                    return;
                }
                String name = args[2].toLowerCase();
                java.util.List<ManorRepository.SubEntry> subs = manors.getSubs(guild, manor.slot());
                ManorRepository.SubEntry target = subs.stream().filter(s -> s.name().equals(name)).findFirst().orElse(null);
                if (target == null) {
                    sender.sendMessage(Messages.get("error.sub_not_exist", name));
                    return;
                }
                Flag f = Flag.byId(args[3]).orElse(null);
                if (f == null) {
                    sender.sendMessage(Messages.get("error.unknown_flag", args[3]));
                    return;
                }
                String value = f.normalize(args[4]).orElse(null);
                if (value == null) {
                    sender.sendMessage(Messages.get("error.invalid_value"));
                    return;
                }
                java.util.Map<String, String> flags = new java.util.HashMap<>(target.flags());
                flags.put(f.id(), value);
                manors.saveSub(guild, manor.slot(), name, target.minX(), target.minZ(), target.maxX(), target.maxZ(), flags);
                sender.sendMessage(Messages.get("success.sub_flag_set", name, f.id(), value));
            }
            case "list" -> {
                java.util.List<ManorRepository.SubEntry> subs = manors.getSubs(guild, manor.slot());
                if (subs.isEmpty()) {
                    sender.sendMessage(Messages.get("error.no_sub_yet"));
                    return;
                }
                sender.sendMessage(Messages.get("info.sub_header"));
                for (ManorRepository.SubEntry s : subs) {
                    sender.sendMessage(Messages.get("info.sub_entry", s.name(), s.maxX() - s.minX() + 1, s.maxZ() - s.minZ() + 1, s.flags().size()));
                }
            }
            default -> sender.sendMessage(Messages.get("usage.sub"));
        }
    }

    /** /gs bulletin <set|show|clear> [内容]：公会公告板管理。 */
    private void bulletin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        GuildWorld gw = guilds.find(manor.guild()).orElse(null);
        if (gw == null) {
            sender.sendMessage(Messages.get("error.world_not_exist"));
            return;
        }
        // 只有庄主或 admin 能管理公告
        if (!manor.owner().equals(PlayerRef.of(player.getUniqueId()))
                && !Permissions.hasAdminPerm(player, Permissions.ADMIN_FLAG_OTHER)) {
            sender.sendMessage(Messages.get("error.only_owner"));
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase() : "show";
        switch (action) {
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.get("usage.bulletin"));
                    return;
                }
                String text = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                if (text.length() > 200) {
                    sender.sendMessage(Messages.get("error.too_long", 200));
                    return;
                }
                guilds.save(gw.withBulletin(text.replace(';', ',')));
                sender.sendMessage(Messages.get("success.bulletin_set", text));
            }
            case "clear" -> {
                guilds.save(gw.withBulletin(""));
                sender.sendMessage(Messages.get("success.bulletin_cleared"));
            }
            case "show" -> {
                String bulletin = gw.bulletin();
                if (bulletin == null || bulletin.isBlank()) {
                    sender.sendMessage(Messages.get("info.bulletin_empty"));
                } else {
                    sender.sendMessage(Messages.get("info.bulletin_show", manor.guild().value(), bulletin));
                }
            }
            default -> sender.sendMessage(Messages.get("usage.bulletin"));
        }
    }

    /** /gs board：查看脚下庄园的留言墙（格式化展示最近 10 条）。 */
    private void board(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        GuildWorld gw = registry.get(player.getWorld().getName());
        if (gw == null) { sender.sendMessage(Messages.get("error.not_in_guild_world")); return; }
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        int lx = (player.getLocation().getBlockX() >> 4) - gw.originChunkX();
        int lz = (player.getLocation().getBlockZ() >> 4) - gw.originChunkZ();
        Classification c = mergeClassify(layout, gw.guild(), lx, lz);
        if (!c.isPlot()) { sender.sendMessage(Messages.get("error.not_on_plot")); return; }
        var comments = manors.getComments(gw.guild(), c.slot(), 10);
        if (comments.isEmpty()) {
            sender.sendMessage(Messages.get("info.board_empty"));
            return;
        }
        String alias = ""; // 获取庄园别名
        Manor m = manors.findBySlot(gw.guild(), c.slot()).orElse(null);
        if (m != null) alias = Flag.ALIAS.resolveString(m.flags());
        String title = alias.isBlank() ? "#" + c.slot() : alias + " (#" + c.slot() + ")";
        sender.sendMessage(Messages.get("info.board_header", title));
        for (var entry : comments) {
            String authorName = Bukkit.getOfflinePlayer(entry.author().uuid()).getName();
            if (authorName == null) authorName = "???";
            String time = new java.text.SimpleDateFormat("MM-dd HH:mm").format(new java.util.Date(entry.timestamp()));
            sender.sendMessage(Messages.get("info.board_entry", time, authorName, entry.message()));
        }
        sender.sendMessage(Messages.get("info.board_footer"));
    }

    /** /gs gift <玩家>：把手持物品送给同公会营地的玩家。 */
    private void gift(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.gift"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Messages.get("error.player_offline", args[1]));
            return;
        }
        if (target.equals(player)) {
            sender.sendMessage(Messages.get("error.cannot_self"));
            return;
        }
        // 必须在同一公会营地
        if (!player.getWorld().equals(target.getWorld())) {
            sender.sendMessage(Messages.get("error.not_same_world"));
            return;
        }
        var item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == org.bukkit.Material.AIR || item.getAmount() == 0) {
            sender.sendMessage(Messages.get("error.no_item_in_hand"));
            return;
        }
        // 给目标玩家物品（满了掉地上）
        var leftover = target.getInventory().addItem(item.clone());
        for (var drop : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), drop);
        }
        player.getInventory().setItemInMainHand(null);
        sender.sendMessage(Messages.get("success.gift_sent", item.getAmount(), item.getType().name(), target.getName()));
        target.sendMessage(Messages.get("success.gift_received", player.getName(), item.getAmount(), item.getType().name()));
    }

    /** /gs flower [公会名 slot]：给庄园送花（每天每块庄园限送一次）。 */
    private void flower(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        GuildId targetGuild = null;
        int targetSlot = -1;

        // 解析参数：/gs flower 或 /gs flower <公会名> <slot>
        if (args.length >= 3) {
            targetGuild = new GuildId(args[1]);
            try { targetSlot = Integer.parseInt(args[2]); } catch (NumberFormatException e) {
                sender.sendMessage(Messages.get("usage.flower")); return;
            }
        } else {
            // 默认：给脚下庄园送花
            GuildWorld gw = registry.get(player.getWorld().getName());
            if (gw == null) { sender.sendMessage(Messages.get("error.not_in_guild_world")); return; }
            LayoutCalculator layout = new LayoutCalculator(gw.layout());
            int lx = (player.getLocation().getBlockX() >> 4) - gw.originChunkX();
            int lz = (player.getLocation().getBlockZ() >> 4) - gw.originChunkZ();
            var slotOpt = layout.slotAt(lx, lz);
            if (slotOpt.isEmpty()) { sender.sendMessage(Messages.get("error.not_on_plot")); return; }
            targetGuild = gw.guild();
            targetSlot = slotOpt.getAsInt();
        }

        // 不能给自己送花
        Manor target = manors.findBySlot(targetGuild, targetSlot).orElse(null);
        if (target == null) { sender.sendMessage(Messages.get("error.slot_empty", targetSlot)); return; }
        if (target.owner().equals(ref)) { sender.sendMessage(Messages.get("error.cannot_flower_self")); return; }

        // 检查今天是否已送过
        if (manors.hasSentFlowerToday(targetGuild, targetSlot, ref)) {
            sender.sendMessage(Messages.get("error.already_flowered_today"));
            return;
        }

        manors.sendFlower(targetGuild, targetSlot, ref);
        int todayCount = manors.getTodayFlowerCount(targetGuild, targetSlot);
        String alias = Flag.ALIAS.resolveString(target.flags());
        String name = alias.isBlank() ? targetGuild.value() + " #" + targetSlot : alias;
        sender.sendMessage(Messages.get("success.flower_sent", name, todayCount));

        // 通知庄园主人（如果在线）
        Player owner = Bukkit.getPlayer(target.owner().uuid());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(Messages.get("success.flower_received", player.getName(), name));
        }
    }

    /** /gs open [时长]：临时开放庄园给访客（默认 1 小时，0=永久）。 */
    private void openPlot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) { sender.sendMessage(Messages.get("error.no_manor")); return; }
        long durationMs = 3600_000L; // 默认 1 小时
        if (args.length >= 2) {
            String raw = args[1].toLowerCase();
            if (raw.equals("0") || raw.equals("perm") || raw.equals("permanent")) {
                durationMs = 0;
            } else {
                try {
                    long minutes = Long.parseLong(raw.replaceAll("[^0-9]", ""));
                    durationMs = minutes * 60_000L;
                } catch (NumberFormatException e) {
                    sender.sendMessage(Messages.get("usage.open"));
                    return;
                }
            }
        }
        String key = manor.guild().value() + ":" + manor.slot();
        long expireAt = durationMs > 0 ? System.currentTimeMillis() + durationMs : 0;
        openPlots.put(key, expireAt);
        // 自动关闭定时器
        if (durationMs > 0) {
            final String k = key;
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override public void run() {
                    Long exp = openPlots.get(k);
                    if (exp != null && exp > 0 && System.currentTimeMillis() >= exp) {
                        openPlots.remove(k);
                    }
                }
            }.runTaskLater(org.bukkit.Bukkit.getPluginManager().getPlugin("GuildShelter"), durationMs / 50);
        }
        String timeStr = durationMs > 0 ? (durationMs / 60_000) + "分钟" : "永久";
        sender.sendMessage(Messages.get("success.plot_opened", timeStr));
    }

    /** /gs close：手动关闭庄园访客模式。 */
    private void closePlot(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) { sender.sendMessage(Messages.get("error.no_manor")); return; }
        String key = manor.guild().value() + ":" + manor.slot();
        if (openPlots.remove(key) != null) {
            sender.sendMessage(Messages.get("success.plot_closed"));
        } else {
            sender.sendMessage(Messages.get("error.plot_not_open"));
        }
    }

    /** 检查某庄园是否处于临时开放状态（供 ManorAccessListener 用）。 */
    public boolean isPlotOpen(GuildId guild, int slot) {
        String key = guild.value() + ":" + slot;
        Long expireAt = openPlots.get(key);
        if (expireAt == null) return false;
        if (expireAt == 0) return true; // 永久开放
        if (System.currentTimeMillis() >= expireAt) {
            openPlots.remove(key);
            return false;
        }
        return true;
    }

    /** /gs admin reload：热重载 config.yml。 */
    private void adminReload(CommandSender sender) {
        org.windy.guildshelter.GuildShelterPlugin plugin = org.windy.guildshelter.GuildShelterPlugin.get();
        if (plugin != null) {
            plugin.reloadConfig();
            sender.sendMessage(Messages.get("success.reload"));
        } else {
            sender.sendMessage(Messages.get("error.plugin_unavailable"));
        }
    }

    /** /gs admin fund <公会> <add|check|set> [金额]：管理公会资金。 */
    private void fund(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.get("usage.admin_fund"));
            return;
        }
        GuildId guild = new GuildId(args[2]);
        GuildWorld gw = guilds.find(guild).orElse(null);
        if (gw == null) {
            sender.sendMessage(Messages.get("error.guild_not_exist", guild.value()));
            return;
        }
        String action = args.length >= 4 ? args[3].toLowerCase() : "check";
        switch (action) {
            case "check" -> {
                sender.sendMessage(Messages.get("info.fund_check", guild.value(), String.format("%.0f", gw.funds())));
            }
            case "add" -> {
                if (args.length < 5) {
                    sender.sendMessage(Messages.get("usage.admin_fund"));
                    return;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Messages.get("error.number_must_be_int"));
                    return;
                }
                if (amount <= 0) {
                    sender.sendMessage(Messages.get("error.positive_required"));
                    return;
                }
                guilds.save(gw.withFunds(gw.funds() + amount));
                sender.sendMessage(Messages.get("success.fund_added", guild.value(), String.format("%.0f", amount),
                        String.format("%.0f", gw.funds() + amount)));
            }
            case "set" -> {
                if (args.length < 5) {
                    sender.sendMessage(Messages.get("usage.admin_fund"));
                    return;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Messages.get("error.number_must_be_int"));
                    return;
                }
                guilds.save(gw.withFunds(amount));
                sender.sendMessage(Messages.get("success.fund_set", guild.value(), String.format("%.0f", amount)));
            }
            default -> sender.sendMessage(Messages.get("usage.admin_fund"));
        }
    }

    /** /gs admin setowner <公会> <玩家>：转移庄园所有权。 */
    private void setOwner(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Messages.get("usage.admin_setowner"));
            return;
        }
        GuildId guild = new GuildId(args[2]);
        org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[3]);
        PlayerRef newOwner = PlayerRef.of(target.getUniqueId());
        // 找该玩家在该公会的庄园
        Manor existing = manors.findByOwner(guild, newOwner).orElse(null);
        if (existing != null) {
            sender.sendMessage(Messages.get("error.target_has_manor", args[3], existing.slot()));
            return;
        }
        // 找该公会里最老的庄园（slot 最小的），转移给目标
        List<Manor> all = manors.findAll(guild);
        if (all.isEmpty()) {
            sender.sendMessage(Messages.get("error.give_no_plots"));
            return;
        }
        Manor target2 = all.get(0);
        Manor updated = new Manor(target2.slot(), guild, newOwner, target2.level(),
                target2.coBuilders(), target2.members(), target2.denied(), target2.flags());
        manors.save(updated);
        sender.sendMessage(Messages.get("success.setowner", target2.slot(), args[3]));
    }

    /** /gs admin purge <天数> [公会id]：清除 N 天未登录玩家的庄园。 */
    private void purge(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.get("usage.admin_purge"));
            return;
        }
        int days;
        try {
            days = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Messages.get("error.days_must_be_int"));
            return;
        }
        if (days < 1) {
            sender.sendMessage(Messages.get("error.days_must_be_positive"));
            return;
        }
        long threshold = System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000;
        int purged = 0;
        // 遍历指定公会或所有公会
        java.util.List<GuildWorld> worlds = args.length >= 4
                ? java.util.List.of(guilds.find(new GuildId(args[3])).orElse(null))
                : guilds.findAll();
        if (worlds.contains(null)) {
            sender.sendMessage(Messages.get("error.guild_not_exist_short"));
            return;
        }
        for (GuildWorld gw : worlds) {
            for (Manor m : manors.findAll(gw.guild())) {
                org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(m.owner().uuid());
                if (op.hasPlayedBefore() && op.getLastPlayed() < threshold
                        && !op.isOnline()) {
                    manors.delete(gw.guild(), m.slot());
                    purged++;
                }
            }
        }
        sender.sendMessage(Messages.get("success.purge", purged, days));
    }

    /** /gs admin regen [公会id]：重置当前所在庄园的地形（清植被+整地）。 */
    private void regen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.only_player_regen"));
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        Manor manor = manors.findByOwnerAnywhere(ref).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        service.clearManor(manor.guild(), ref);
        sender.sendMessage(Messages.get("success.regen", manor.slot()));
    }

    /**
     * /gs citytrust &lt;玩家&gt; | /gs cityuntrust &lt;玩家&gt;：会长（含副会长）信任/撤销会内成员建造主城。
     * 仅会长可用；被信任者必须在本公会内（拥有本会庄园）。
     */
    private void cityTrust(CommandSender sender, String[] args, boolean add) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§e用法: /gs " + (add ? "citytrust" : "cityuntrust") + " <玩家>");
            return;
        }
        PlayerRef self = PlayerRef.of(player.getUniqueId());
        Manor myManor = manors.findByOwnerAnywhere(self).orElse(null);
        if (myManor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        GuildId guild = myManor.guild();
        // 仅会长/副会长可授主城信任
        if (!service.isGuildAdmin(self, guild) && !player.isOp()) {
            sender.sendMessage("§c只有会长（或副会长）能管理主城信任。");
            return;
        }
        var cache = org.windy.guildshelter.GuildShelterPlugin.cityTrustCache();
        if (cache == null) {
            sender.sendMessage("§c主城信任功能未就绪。");
            return;
        }
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        java.util.UUID targetId = target.getUniqueId();
        if (add) {
            // 被信任者必须是会内成员（在本公会拥有庄园）
            if (manors.findByOwner(guild, PlayerRef.of(targetId)).isEmpty()) {
                sender.sendMessage("§c只能信任本公会的成员。");
                return;
            }
            cache.add(guild, targetId);
            sender.sendMessage("§a已信任 §f" + args[1] + " §a建造公会主城。");
        } else {
            cache.remove(guild, targetId);
            sender.sendMessage("§e已撤销 §f" + args[1] + " §e的主城建造信任。");
        }
    }

    /** /gs admin citywall &lt;公会&gt;：给已建好的公会补铺主城环路（+ 围墙若启用），修旧世界"路被吞"。 */
    private void citywall(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法: /gs admin citywall <公会>");
            return;
        }
        GuildId guild = new GuildId(args[2]);
        GuildWorld gw = guilds.find(guild).orElse(null);
        if (gw == null) {
            sender.sendMessage(Messages.get("error.guild_not_exist", guild.value()));
            return;
        }
        gw = worlds.ensureWorld(gw); // 确保世界已加载
        guilds.save(gw);
        service.prepareMainCityRoads(gw); // 补铺主城四周环路（修旧世界"路被吞"）
        service.buildCityWall(gw);        // 围墙（默认关时无操作）
        sender.sendMessage("§a已开始为公会 " + guild.value() + " 补铺主城环路（进度见控制台）。");
    }

    /** /gs admin export：导出所有公会数据到 CSV 文件。 */
    private void exportData(CommandSender sender) {
        try {
            java.io.File dir = new java.io.File("plugins/GuildShelter/export");
            dir.mkdirs();
            String filename = "guildshelter_export_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
                    .format(new java.util.Date()) + ".csv";
            java.io.File file = new java.io.File(dir, filename);
            try (java.io.PrintWriter pw = new java.io.PrintWriter(file, "UTF-8")) {
                pw.println("guild,slot,owner_uuid,owner_name,level,coBuilders,members,denied,flags");
                for (GuildWorld gw : guilds.findAll()) {
                    for (Manor m : manors.findAll(gw.guild())) {
                        String ownerName = Bukkit.getOfflinePlayer(m.owner().uuid()).getName();
                        pw.println(String.join(",",
                                esc(gw.guild().value()),
                                String.valueOf(m.slot()),
                                m.owner().uuid().toString(),
                                esc(ownerName != null ? ownerName : "?"),
                                String.valueOf(m.level()),
                                String.valueOf(m.coBuilders().size()),
                                String.valueOf(m.members().size()),
                                String.valueOf(m.denied().size()),
                                esc(org.windy.guildshelter.persistence.FlagsCsv.toCsv(m.flags()))));
                    }
                }
            }
            sender.sendMessage(Messages.get("success.export", filename));
        } catch (Exception e) {
            sender.sendMessage(Messages.get("error.export_failed", e.getMessage()));
        }
    }

    private static String esc(String v) {
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    // ===== near / rate / top / middle =====

    /** /gs near：列出附近庄园（按距离排序，显示庄主+距离）。 */
    private void near(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        GuildWorld gw = registry.get(player.getWorld().getName());
        if (gw == null) {
            sender.sendMessage(Messages.get("error.not_in_guild_world"));
            return;
        }
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        int px = (player.getLocation().getBlockX() >> 4) - gw.originChunkX();
        int pz = (player.getLocation().getBlockZ() >> 4) - gw.originChunkZ();
        List<Manor> all = manors.findAll(gw.guild());
        if (all.isEmpty()) {
            sender.sendMessage(Messages.get("error.no_plots_in_guild"));
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
        sender.sendMessage(Messages.get("info.near_header"));
        int show = Math.min(sorted.size(), 10);
        for (int i = 0; i < show; i++) {
            SlotDist sd = sorted.get(i);
            String alias = Flag.ALIAS.resolveString(sd.manor().flags());
            String name = alias.isBlank() ? "#" + sd.manor().slot() : alias + " (#" + sd.manor().slot() + ")";
            String ownerName = Bukkit.getOfflinePlayer(sd.manor().owner().uuid()).getName();
            sender.sendMessage(Messages.get("info.near_entry", name, ownerName != null ? ownerName : "?", String.format("%.1f", sd.dist() * 16)));
        }
    }

    /** /gs rate <分数> [公会名 slot]：给庄园打分（站在庄园上直接打，或指定公会+slot）。 */
    private void rate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.rate"));
            return;
        }
        int score;
        try {
            score = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Messages.get("error.score_must_be_int"));
            return;
        }
        if (score < 1 || score > 10) {
            sender.sendMessage(Messages.get("error.score_range"));
            return;
        }

        Manor targetManor = null;
        // 模式1：指定公会+slot
        if (args.length >= 4) {
            GuildId guild = new GuildId(args[2]);
            try {
                int slot = Integer.parseInt(args[3]);
                targetManor = manors.findBySlot(guild, slot).orElse(null);
            } catch (NumberFormatException ignored) {}
            if (targetManor == null) {
                sender.sendMessage(Messages.get("error.plot_not_found", args[2], args[3]));
                return;
            }
        } else {
            // 模式2：站在庄园上自动检测
            GuildWorld gw = registry.get(player.getWorld().getName());
            if (gw == null) {
                sender.sendMessage(Messages.get("error.rate_need_world"));
                return;
            }
            LayoutCalculator layout = new LayoutCalculator(gw.layout());
            int lx = (player.getLocation().getBlockX() >> 4) - gw.originChunkX();
            int lz = (player.getLocation().getBlockZ() >> 4) - gw.originChunkZ();
            Classification c = mergeClassify(layout, gw.guild(), lx, lz);
            if (!c.isPlot()) {
                sender.sendMessage(Messages.get("error.rate_need_plot"));
                return;
            }
            targetManor = manors.findBySlot(gw.guild(), c.slot()).orElse(null);
            if (targetManor == null) {
                sender.sendMessage(Messages.get("error.slot_empty", args[3]));
                return;
            }
        }

        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        if (targetManor.owner().equals(ref)) {
            sender.sendMessage(Messages.get("error.cannot_rate_self"));
            return;
        }
        manors.rate(targetManor.guild(), targetManor.slot(), ref, score);
        double avg = manors.getAverageRating(targetManor.guild(), targetManor.slot());
        int count = manors.getRatingCount(targetManor.guild(), targetManor.slot());
        sender.sendMessage(Messages.get("success.rated", targetManor.slot(), score, String.format("%.1f", avg), count));
    }

    /** /gs top [公会名]：按评分排行（不需庄园；不填公会名则自动检测自己所在的公会）。 */
    /** /gs top [公会] [rating|level|members|entities]：排行榜。 */
    private void top(CommandSender sender, String[] args) {
        GuildId guild = null;
        String sortBy = "rating";
        // 解析参数：第一个非公会名参数是排序方式
        for (int i = 1; i < args.length; i++) {
            String a = args[i].toLowerCase();
            if (a.equals("rating") || a.equals("level") || a.equals("members") || a.equals("entities") || a.equals("visits")) {
                sortBy = a;
            } else if (guild == null) {
                guild = new GuildId(args[i]);
            }
        }
        // 自动检测公会
        if (guild == null && sender instanceof Player player) {
            GuildWorld gw = registry.get(player.getWorld().getName());
            if (gw != null) guild = gw.guild();
        }
        if (guild == null && sender instanceof Player player) {
            Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
            if (manor != null) guild = manor.guild();
        }
        if (guild == null) {
            sender.sendMessage(Messages.get("usage.top"));
            return;
        }
        final GuildId finalGuild = guild;
        List<Manor> all = manors.findAll(finalGuild);
        if (all.isEmpty()) {
            sender.sendMessage(Messages.get("error.no_plots_in_guild"));
            return;
        }
        // 排序
        switch (sortBy) {
            case "level" -> all.sort((a, b) -> Integer.compare(b.level(), a.level()));
            case "members" -> all.sort((a, b) -> Integer.compare(b.coBuilders().size() + b.members().size(),
                    a.coBuilders().size() + a.members().size()));
            case "visits" -> all.sort((a, b) -> Integer.compare(
                    manors.getVisitCount(finalGuild, b.slot()),
                    manors.getVisitCount(finalGuild, a.slot())));
            case "entities" -> {
                World world = Bukkit.getWorld(guilds.find(finalGuild).map(GuildWorld::worldName).orElse(""));
                if (world != null && census != null) {
                    all.sort((a, b) -> Integer.compare(
                            census.countAt(world, b).livingTotal(),
                            census.countAt(world, a).livingTotal()));
                }
            }
            default -> { // rating
                all.sort((a, b) -> Double.compare(
                        manors.getAverageRating(finalGuild, b.slot()),
                        manors.getAverageRating(finalGuild, a.slot())));
            }
        }
        String title = switch (sortBy) {
            case "level" -> "等级";
            case "members" -> "成员数";
            case "entities" -> "实体数";
            case "visits" -> "访问量";
            default -> "评分";
        };
        sender.sendMessage(Messages.get("info.top_header", finalGuild.value(), title));
        int show = Math.min(all.size(), 10);
        for (int i = 0; i < show; i++) {
            Manor m = all.get(i);
            String alias = Flag.ALIAS.resolveString(m.flags());
            String name = alias.isBlank() ? "#" + m.slot() : alias + " (#" + m.slot() + ")";
            String ownerName = Bukkit.getOfflinePlayer(m.owner().uuid()).getName();
            String value = switch (sortBy) {
                case "level" -> "Lv" + m.level();
                case "members" -> (m.coBuilders().size() + m.members().size()) + "人";
                case "visits" -> manors.getVisitCount(finalGuild, m.slot()) + "次";
                case "entities" -> {
                    World w = Bukkit.getWorld(guilds.find(finalGuild).map(GuildWorld::worldName).orElse(""));
                    yield w != null && census != null ? census.countAt(w, m).livingTotal() + "只" : "?";
                }
                default -> {
                    double avg = manors.getAverageRating(finalGuild, m.slot());
                    int count = manors.getRatingCount(finalGuild, m.slot());
                    yield String.format("%.1f", avg) + "分(" + count + "人)";
                }
            };
            sender.sendMessage(Messages.get("info.top_entry", i + 1, name, ownerName != null ? ownerName : "?", title, value));
        }
    }

    /** /gs middle：传送到庄园正中心（无视 sethome）。 */
    private void middle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        Manor manor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (manor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
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
        sender.sendMessage(Messages.get("success.middle_teleported", manor.slot()));
    }

    // ===== comment / inbox =====

    /** /gs comment <留言>：给当前所在庄园留言。 */
    private void comment(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.comment"));
            return;
        }
        GuildWorld gw = registry.get(player.getWorld().getName());
        if (gw == null) {
            sender.sendMessage(Messages.get("error.not_in_guild_world"));
            return;
        }
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        int lx = (player.getLocation().getBlockX() >> 4) - gw.originChunkX();
        int lz = (player.getLocation().getBlockZ() >> 4) - gw.originChunkZ();
        Classification c = mergeClassify(layout, gw.guild(), lx, lz);
        if (!c.isPlot()) {
            sender.sendMessage(Messages.get("error.not_on_plot"));
            return;
        }
        String msg = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        manors.addComment(gw.guild(), c.slot(), PlayerRef.of(player.getUniqueId()), msg);
        sender.sendMessage(Messages.get("success.comment_added", c.slot()));
    }

    /** /gs inbox：查看自己庄园收到的留言。 */
    private void inbox(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        List<ManorRepository.CommentEntry> entries = manors.getInbox(ref, 20);
        if (entries.isEmpty()) {
            sender.sendMessage(Messages.get("error.no_comments"));
            return;
        }
        sender.sendMessage(Messages.get("info.inbox_header"));
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm");
        for (ManorRepository.CommentEntry e : entries) {
            String authorName = Bukkit.getOfflinePlayer(e.author().uuid()).getName();
            String time = sdf.format(new java.util.Date(e.timestamp()));
            sender.sendMessage(Messages.get("info.inbox_entry", time, authorName != null ? authorName : "?", e.slot(), e.message()));
        }
    }

    // ===== swap / grant / merge =====

    /** /gs swap <玩家>：与对方互换庄园 slot。 */
    private void swap(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.swap"));
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        Manor myManor = manors.findByOwnerAnywhere(ref).orElse(null);
        if (myManor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Messages.get("error.player_offline", args[1]));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage(Messages.get("error.cannot_self_swap"));
            return;
        }
        PlayerRef targetRef = PlayerRef.of(target.getUniqueId());
        Manor targetManor = manors.findByOwnerAnywhere(targetRef).orElse(null);
        if (targetManor == null) {
            sender.sendMessage(Messages.get("error.target_no_manor", target.getName()));
            return;
        }
        if (!myManor.guild().equals(targetManor.guild())) {
            sender.sendMessage(Messages.get("error.not_same_guild"));
            return;
        }
        // 交换 slot：保存对方 slot 的数据给你，你的给对方
        // 注意：两个 save 不是原子的，极端情况（崩溃）可能部分写入。
        // 但都是 upsert，不会丢数据，最多 slot 对不上需要手动修。
        int mySlot = myManor.slot();
        int theirSlot = targetManor.slot();
        Manor newMine = new Manor(theirSlot, myManor.guild(), ref, myManor.level(),
                myManor.coBuilders(), myManor.members(), myManor.denied(), myManor.flags());
        Manor newTheirs = new Manor(mySlot, targetManor.guild(), targetRef, targetManor.level(),
                targetManor.coBuilders(), targetManor.members(), targetManor.denied(), targetManor.flags());
        try {
            manors.save(newMine);
            manors.save(newTheirs);
        } catch (Exception e) {
            sender.sendMessage(Messages.get("error.export_failed", e.getMessage()));
            return;
        }
        sender.sendMessage(Messages.get("success.swap", target.getName(), mySlot, theirSlot));
        target.sendMessage(Messages.get("success.swap_notify", player.getName(), theirSlot, mySlot));
    }

    /** /gs grant <玩家>：给玩家分配额外庄园（需 admin）。 */
    private void grant(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (!Permissions.hasAdminPerm(player, Permissions.ADMIN_TRUST_OTHER)) {
            sender.sendMessage(Messages.get("error.need_admin_perm"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.grant"));
            return;
        }
        Manor myManor = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
        if (myManor == null) {
            sender.sendMessage(Messages.get("error.not_in_any_world"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Messages.get("error.player_offline", args[1]));
            return;
        }
        PlayerRef targetRef = PlayerRef.of(target.getUniqueId());
        GuildId guild = myManor.guild();
        // 检查目标是否已有该公会的庄园
        if (manors.findByOwner(guild, targetRef).isPresent()) {
            sender.sendMessage(Messages.get("error.target_has_manor", target.getName(), ""));
            return;
        }
        try {
            Manor assigned = service.assignManor(guild, targetRef);
            sender.sendMessage(Messages.get("success.grant", target.getName()));
            org.windy.guildshelter.GuildShelterPlugin.sendWelcome(target, guild.value(), assigned.slot());
        } catch (GuildFullException e) {
            sender.sendMessage(Messages.get("error.guild_full", e.capacity()));
        }
    }

    /** /gs merge <slot>：把相邻庄园合并到自己的庄园（砍掉中间的路）。 */
    private void mergeCmd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.merge"));
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        Manor myManor = manors.findByOwnerAnywhere(ref).orElse(null);
        if (myManor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        int absorbedSlot;
        try {
            absorbedSlot = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Messages.get("error.number_must_be_int"));
            return;
        }
        if (absorbedSlot == myManor.slot()) {
            sender.sendMessage(Messages.get("error.cannot_self_merge"));
            return;
        }
        // 检查目标 slot 是否已被其他庄园吸收
        int existingTarget = merges.getMergedTarget(myManor.guild(), absorbedSlot);
        if (existingTarget != absorbedSlot && existingTarget != myManor.slot()) {
            sender.sendMessage(Messages.get("error.already_merged_to_other", absorbedSlot, existingTarget));
            return;
        }
        if (existingTarget == myManor.slot()) {
            sender.sendMessage(Messages.get("error.already_merged", absorbedSlot));
            return;
        }
        Manor absorbed = manors.findBySlot(myManor.guild(), absorbedSlot).orElse(null);
        if (absorbed == null) {
            sender.sendMessage(Messages.get("error.slot_empty", absorbedSlot));
            return;
        }
        // 安全检查：只能合并自己的庄园（或有 admin 权限）
        if (!absorbed.owner().equals(ref) && !Permissions.hasAdminPerm(player, Permissions.ADMIN_TRUST_OTHER)) {
            sender.sendMessage(Messages.get("error.only_owner"));
            return;
        }
        // 检查是否相邻（slot 在螺旋上相邻 = 距离 1）
        GuildWorld gw = guilds.find(myManor.guild()).orElse(null);
        if (gw == null) {
            sender.sendMessage(Messages.get("error.world_not_exist"));
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
            sender.sendMessage(Messages.get("error.not_adjacent"));
            return;
        }
        merges.merge(myManor.guild(), myManor.slot(), absorbedSlot);
        sender.sendMessage(Messages.get("success.merged", absorbedSlot, myManor.slot()));
    }

    /** /gs unmerge [slot]：取消合并（不填=取消所有，填 slot=取消特定一个）。 */
    private void unmerge(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        Manor myManor = manors.findByOwnerAnywhere(ref).orElse(null);
        if (myManor == null) {
            sender.sendMessage(Messages.get("error.no_manor"));
            return;
        }
        GuildId guild = myManor.guild();
        int primarySlot = myManor.slot();

        // 检查是否是主合并 slot（有吸收其他 slot）
        java.util.Set<Integer> absorbed = merges.getMergedSlots(guild, primarySlot);
        if (absorbed.isEmpty()) {
            sender.sendMessage(Messages.get("error.no_merges"));
            return;
        }

        if (args.length >= 2) {
            // 取消特定 slot
            int targetSlot;
            try {
                targetSlot = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Messages.get("error.number_must_be_int"));
                return;
            }
            if (!absorbed.contains(targetSlot)) {
                sender.sendMessage(Messages.get("error.not_merged", targetSlot));
                return;
            }
            // 从 DB + 缓存中移除这条合并记录
            manors.unmergeOne(guild, primarySlot, targetSlot);
            merges.removeOne(guild, primarySlot, targetSlot);
            sender.sendMessage(Messages.get("success.unmerged_one", targetSlot));
        } else {
            // 取消所有
            merges.unmerge(guild, primarySlot);
            sender.sendMessage(Messages.get("success.unmerged_all", primarySlot, absorbed.size()));
        }
    }

    /** /gs move <公会名>：搬家到另一个公会（保留建筑+数据）。 */
    private void move(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (!service.isMoveEnabled()) {
            sender.sendMessage(Messages.get("error.move_not_enabled"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.move"));
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        Manor currentManor = manors.findByOwnerAnywhere(ref).orElse(null);
        if (currentManor == null) {
            sender.sendMessage(Messages.get("error.move_no_manor"));
            return;
        }
        GuildId targetGuild = new GuildId(args[1]);

        // 预检：先检查大部分错误条件（不扣费不复制）
        if (currentManor.guild().equals(targetGuild)) {
            sender.sendMessage(Messages.get("error.move_same_guild"));
            return;
        }
        long lastMove = manors.getLastMoveTime(ref.uuid());
        if (lastMove > 0 && service.moveCooldownDays() > 0) {
            long cooldownMs = (long) service.moveCooldownDays() * 24 * 60 * 60 * 1000;
            long remaining = cooldownMs - (System.currentTimeMillis() - lastMove);
            if (remaining > 0) {
                long days = remaining / (24 * 60 * 60 * 1000) + 1;
                sender.sendMessage(Messages.get("error.move_cooldown", days));
                return;
            }
        }
        GuildWorld targetGw = guilds.find(targetGuild).orElse(null);
        if (targetGw == null) {
            sender.sendMessage(Messages.get("error.move_target_not_exist"));
            return;
        }
        int capacity = levels.maxMembers(targetGw.guildLevel());
        int targetSlot = manors.nextFreeSlot(targetGuild);
        if (targetSlot >= capacity) {
            sender.sendMessage(Messages.get("error.move_target_full"));
            return;
        }
        if (service.moveCost() > 0) {
            org.windy.guildshelter.domain.port.EconomyPort eco = null;
            // 通过 VaultEconomy 静态获取（如果有）
            // 这里直接检查 service 的经济能力
            // 实际扣费在 service.moveManor 里
        }

        // 显示预览信息
        double cost = service.moveCost();
        String costStr = cost > 0 ? String.format("%.0f", cost) : "免费";
        sender.sendMessage("§6==== 搬家确认 ====");
        sender.sendMessage("§7当前公会: §f" + currentManor.guild().value() + " §7庄园 #" + currentManor.slot());
        sender.sendMessage("§7目标公会: §f" + targetGuild.value());
        sender.sendMessage("§7费用: §e" + costStr);
        sender.sendMessage("§7冷却: §f" + service.moveCooldownDays() + " §7天");

        // mod 数据风险检测
        GuildWorld srcGw = guilds.find(currentManor.guild()).orElse(null);
        if (srcGw != null) {
            org.windy.guildshelter.domain.port.ManorMover mover = service.getManorMover();
            if (mover != null) {
                LayoutCalculator layout = new LayoutCalculator(srcGw.layout());
                ChunkRegion src = layout.plotRegion(currentManor.slot())
                        .shift(srcGw.originChunkX(), srcGw.originChunkZ());
                java.util.List<String> risks = mover.detectRisks(srcGw.worldName(),
                        src.minChunkX(), src.minChunkZ(), src.maxChunkX(), src.maxChunkZ());
                for (String risk : risks) {
                    sender.sendMessage(risk);
                }
            }
        }

        sender.sendMessage("§e⚠ 建筑将被复制到新公会营地，旧位置将被清空。");
        sender.sendMessage("§e30秒内输入 §6/gs confirm §e确认搬家。");
    }

    /** 执行子命令（跳过确认检查，供 confirm 调用）。 */
    private void executeSub(CommandSender sender, String sub, String[] args) {
        switch (sub) {
            case "deny" -> deny(sender, args);
            case "clear" -> clear(sender);
            case "merge" -> mergeCmd(sender, args);
            case "unmerge" -> unmerge(sender, args);
            case "swap" -> swap(sender, args);
            case "grant" -> grant(sender, args);
            case "move" -> executeMove(sender, args);
            default -> sender.sendMessage(Messages.get("error.unknown_command"));
        }
    }

    /** 实际执行搬家（confirm 后调用）。 */
    private void executeMove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("usage.move"));
            return;
        }
        PlayerRef ref = PlayerRef.of(player.getUniqueId());
        GuildId targetGuild = new GuildId(args[1]);

        sender.sendMessage("§e正在搬家，请稍候...");
        GuildService.MoveResult result = service.moveManor(ref, targetGuild);

        switch (result) {
            case SUCCESS -> {
                Manor newManor = manors.findByOwnerAnywhere(ref).orElse(null);
                int newSlot = newManor != null ? newManor.slot() : -1;
                double cost = service.moveCost();
                if (cost > 0) {
                    sender.sendMessage(Messages.get("success.move_cost_deducted",
                            String.format("%.0f", cost)));
                }
                sender.sendMessage(Messages.get("success.manor_moved",
                        ref.uuid().toString().substring(0, 8), targetGuild.value(), newSlot));
                // 展示 mod 数据搬运结果
                for (String modResult : service.getLastMoveModResults()) {
                    sender.sendMessage(modResult);
                }
                // 传送到新庄园
                GuildWorld gw = ensureLoadedWorld(sender, targetGuild);
                if (gw != null && newManor != null) {
                    World world = Bukkit.getWorld(gw.worldName());
                    if (world != null) {
                        ChunkRegion active = new LayoutCalculator(gw.layout())
                                .activeRegion(newManor.slot(), newManor.level())
                                .shift(gw.originChunkX(), gw.originChunkZ());
                        int cx = (active.minBlockX() + active.maxBlockX()) / 2;
                        int cz = (active.minBlockZ() + active.maxBlockZ()) / 2;
                        world.loadChunk(cx >> 4, cz >> 4, true);
                        int cy = world.getHighestBlockYAt(cx, cz) + 1;
                        player.teleport(new Location(world, cx + 0.5, cy, cz + 0.5));
                    }
                }
            }
            case NOT_ENABLED -> sender.sendMessage(Messages.get("error.move_not_enabled"));
            case NO_MANOR -> sender.sendMessage(Messages.get("error.move_no_manor"));
            case SAME_GUILD -> sender.sendMessage(Messages.get("error.move_same_guild"));
            case ON_COOLDOWN -> sender.sendMessage(Messages.get("error.move_cooldown",
                    service.moveCooldownDays()));
            case TARGET_NOT_EXIST -> sender.sendMessage(Messages.get("error.move_target_not_exist"));
            case TARGET_FULL -> sender.sendMessage(Messages.get("error.move_target_full"));
            case NOT_ENOUGH_MONEY -> sender.sendMessage(Messages.get("error.move_not_enough_money",
                    String.format("%.0f", service.moveCost())));
            case COPY_FAILED -> sender.sendMessage(Messages.get("error.move_copy_failed"));
        }
    }

    /** 合并感知的 classify：ROAD chunk 若在合并路带上，返回主庄园的 PLOT。O(1) 内存查找。 */
    private Classification mergeClassify(LayoutCalculator layout, GuildId guild, int chunkX, int chunkZ) {
        Classification raw = layout.classify(chunkX, chunkZ);
        if (raw.type() != org.windy.guildshelter.domain.layout.RegionType.ROAD) {
            return raw;
        }
        if (!merges.hasMerges(guild)) {
            return raw;
        }
        MergeAwareClassifier merger = new MergeAwareClassifier(layout, merges, guild);
        return merger.classify(chunkX, chunkZ);
    }

    /** 确保该公会营地已加载并登记；失败返回 null 并已提示。 */
    private GuildWorld ensureLoadedWorld(CommandSender sender, GuildId guild) {
        GuildWorld gw = guilds.find(guild).orElse(null);
        if (gw == null) {
            sender.sendMessage(Messages.get("error.world_not_exist"));
            return null;
        }
        gw = worlds.ensureWorld(gw);
        guilds.save(gw);
        registry.register(gw);
        if (org.bukkit.Bukkit.getWorld(gw.worldName()) == null) {
            sender.sendMessage(Messages.get("error.world_load_failed", gw.worldName()));
            return null;
        }
        return gw;
    }

    private void create(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.get("usage.admin_create"));
            return;
        }
        GuildId guild = new GuildId(args[2]);
        if (guilds.exists(guild)) {
            sender.sendMessage(Messages.get("error.guild_already_exist", guild.value()));
            return;
        }
        // 可选地形参数: /gs admin create <guild> [NONE|CLEAR_VEGETATION|FLATTEN|VOID|FLAT]
        TerrainPrepMode terrainMode = null; // null = 用 config 默认值
        if (args.length >= 4) {
            try {
                terrainMode = TerrainPrepMode.valueOf(args[3].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(Messages.get("error.unknown_terrain", args[3]));
                return;
            }
        }
        long seed = ThreadLocalRandom.current().nextLong();
        TerrainPrepMode mode = terrainMode != null ? terrainMode : org.windy.guildshelter.domain.model.TerrainPrepMode.CLEAR_VEGETATION;

        // 注意：首次建会需 Bukkit.createWorld 新建世界，其内部 setInitialSpawn 会同步生成出生区块
        // (managedBlock 等待 chunk future)。若直接在此处执行，命令本身正运行在 1.26 命令执行上下文
        // (ExecutionContext) 的嵌套事件循环里，嵌套的 managedBlock 等不到 chunk 完成 → 主线程死锁 →
        // Spigot 看门狗超时杀服。改为调度到下一 tick 的干净主线程任务执行，脱离命令上下文即可正常生成。
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                GuildWorld gw = service.createGuild(guild, seed, mode, serverName);
                registry.register(gw);
                sender.sendMessage(Messages.get("success.create_world", gw.worldName(), gw.seed(), gw.originChunkX(), gw.originChunkZ()));
                logMap(guild);
            } catch (RuntimeException e) {
                sender.sendMessage(Messages.get("error.world_load_failed", worlds.worldName(guild)));
                logger.warning("[GuildShelter] 建会失败 " + guild.value() + ": " + e);
            }
        });
    }

    private void tp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.only_player_tp"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Messages.get("usage.admin_tp"));
            return;
        }
        GuildWorld gw = guilds.find(new GuildId(args[2])).orElse(null);
        if (gw == null) {
            sender.sendMessage(Messages.get("error.guild_not_exist", args[2]));
            return;
        }
        gw = worlds.ensureWorld(gw);
        guilds.save(gw);
        registry.register(gw);
        World world = Bukkit.getWorld(gw.worldName());
        if (world == null) {
            sender.sendMessage(Messages.get("error.world_load_failed", gw.worldName()));
            return;
        }
        player.teleport(worlds.safeSpawn(world, gw));
        sender.sendMessage(Messages.get("success.tp_teleported", gw.worldName()));
    }

    /** 给自己在该公会分配下一块庄园（自动整地），并传送过去观察。 */
    private void claim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.only_player_claim"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Messages.get("usage.admin_claim"));
            return;
        }
        GuildId guild = new GuildId(args[2]);
        GuildWorld gw = guilds.find(guild).orElse(null);
        if (gw == null) {
            sender.sendMessage(Messages.get("error.guild_not_exist", guild.value()));
            return;
        }
        gw = worlds.ensureWorld(gw);
        registry.register(gw);

        Manor manor;
        try {
            manor = service.assignManor(guild, PlayerRef.of(player.getUniqueId()));
        } catch (GuildFullException e) {
            sender.sendMessage(Messages.get("error.guild_full_with_level", e.capacity(), e.guildLevel()));
            return;
        }
        // 分配后 allocatedSlots 可能变化，重新读
        gw = guilds.find(guild).orElse(gw);
        registry.register(gw);

        World world = Bukkit.getWorld(gw.worldName());
        if (world == null) {
            sender.sendMessage(Messages.get("error.world_load_failed", gw.worldName()));
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
        // 异步整地（assignManor 已触发），玩家到达时可能还在进行中。
        // 延迟后给玩家重发区块数据包，修复幽灵方块。
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) return;
                // 卸载再加载周围区块 → 强制客户端重新拉取数据
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        int cxx = (cx >> 4) + dx;
                        int czz = (cz >> 4) + dz;
                        if (world.isChunkLoaded(cxx, czz)) {
                            world.refreshChunk(cxx, czz);
                        }
                    }
                }
            }
        }.runTaskLater(org.bukkit.Bukkit.getPluginManager().getPlugin("GuildShelter"), 60L); // 3秒后
        sender.sendMessage(Messages.get("success.claim", manor.slot(), manor.level()));
        sender.sendMessage(Messages.get("success.claim_hint", guild.value()));
        logMap(guild);
    }

    /** 升级发令玩家在该公会的庄园一级（成员自己的事，只受物理满级限制），升级后对新扩范围整地。 */
    private void upgradeManor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.only_player_upgrade"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Messages.get("usage.admin_upgrade_manor"));
            return;
        }
        GuildId guild = new GuildId(args[2]);
        if (guilds.find(guild).isEmpty()) {
            sender.sendMessage(Messages.get("error.guild_not_exist", guild.value()));
            return;
        }
        int cap = levels.manorMaxLevel();
        try {
            boolean ok = service.upgradeManor(guild, PlayerRef.of(player.getUniqueId()));
            if (ok) {
                Manor m = service.assignManor(guild, PlayerRef.of(player.getUniqueId())); // 幂等读回当前庄园
                sender.sendMessage(Messages.get("success.upgraded", m.level(), cap));
            } else {
                sender.sendMessage(Messages.get("error.already_max_level", cap));
            }
        } catch (NoSuchElementException e) {
            sender.sendMessage(Messages.get("error.no_manor_in_guild", guild.value()));
        }
    }

    /** 测试用：给随机 UUID 批量分配 n 块庄园，把网格填出来便于观察分布（满了即停）。 */
    private void fill(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Messages.get("usage.admin_fill"));
            return;
        }
        GuildId guild = new GuildId(args[2]);
        int n;
        try {
            n = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Messages.get("error.number_must_be_int"));
            return;
        }
        GuildWorld gw = guilds.find(guild).orElse(null);
        if (gw == null) {
            sender.sendMessage(Messages.get("error.guild_not_exist", guild.value()));
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
                sender.sendMessage(Messages.get("success.fill_full", e.capacity(), done, guild.value()));
                break;
            }
        }
        sender.sendMessage(Messages.get("success.fill", guild.value(), done));
        logMap(guild);
    }

    /** 仅把网格分布图打到控制台（不改任何数据）。 */
    private void map(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.get("usage.admin_map"));
            return;
        }
        GuildId guild = new GuildId(args[2]);
        if (guilds.find(guild).isEmpty()) {
            sender.sendMessage(Messages.get("error.guild_not_exist", guild.value()));
            return;
        }
        logMap(guild);
        sender.sendMessage(Messages.get("success.map"));
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
        int capacity = service.effectiveCapacity(gw); // 与发地口径一致(宿主有上限跟宿主)
        LayoutCalculator layout = new LayoutCalculator(gw.layout()); // 用该世界冻结的布局
        int cityChunks = gw.layout().cityChunksAtLevel(gw.guildLevel(), levels.maxGuildLevel());
        for (String line : GridAsciiMap.render(layout, gw, occupied, capacity, cityChunks)) {
            logger.info(line);
        }
    }

    /** 升级公会一级（放开更多成员名额），并按新等级容量扩世界边界。 */
    private void upgradeGuild(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.get("usage.admin_upgrade_guild"));
            return;
        }
        GuildId guild = new GuildId(args[2]);
        GuildWorld before = guilds.find(guild).orElse(null);
        if (before == null) {
            sender.sendMessage(Messages.get("error.guild_not_exist", guild.value()));
            return;
        }
        LayoutCalculator layout = new LayoutCalculator(before.layout()); // 用该世界冻结的布局
        double oldBorder = layout.borderSizeBlocks(
                Math.max(before.allocatedSlots(), levels.maxMembers(before.guildLevel())));
        boolean ok = service.upgradeGuild(guild);
        if (!ok) {
            sender.sendMessage(Messages.get("error.already_max_level", levels.maxGuildLevel()));
            return;
        }
        GuildWorld after = guilds.find(guild).orElse(before);
        double newBorder = layout.borderSizeBlocks(
                Math.max(after.allocatedSlots(), levels.maxMembers(after.guildLevel())));
        sender.sendMessage(Messages.get("success.upgrade_guild", after.guildLevel(), levels.maxGuildLevel(),
                levels.maxMembers(before.guildLevel()), levels.maxMembers(after.guildLevel()),
                (int) oldBorder, (int) newBorder));
        // 升级特效：公会营地里放烟花 + 全服广播
        World world = Bukkit.getWorld(after.worldName());
        if (world != null) {
            LayoutCalculator al = new LayoutCalculator(after.layout());
            int sx = al.spawnBlockX() + (after.originChunkX() << 4);
            int sz = al.spawnBlockZ() + (after.originChunkZ() << 4);
            int sy = world.getHighestBlockYAt(sx, sz) + 2;
            var loc = new org.bukkit.Location(world, sx + 0.5, sy, sz + 0.5);
            world.spawn(loc, org.bukkit.entity.Firework.class, fw -> {
                var meta = fw.getFireworkMeta();
                meta.addEffect(org.bukkit.FireworkEffect.builder()
                        .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                        .withColor(org.bukkit.Color.YELLOW, org.bukkit.Color.ORANGE)
                        .withFade(org.bukkit.Color.RED)
                        .trail(true).flicker(true).build());
                meta.setPower(1);
                fw.setFireworkMeta(meta);
            });
            // 广播给世界内所有玩家
            for (Player p : world.getPlayers()) {
                p.sendTitle("§6§l⬆ 公会升级！", "§e" + guild.value() + " §7→ Lv" + after.guildLevel(), 10, 60, 20);
            }
        }
        logMap(guild);
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.get("usage.admin_delete"));
            return;
        }
        GuildId guild = new GuildId(args[2]);
        worlds.unloadGuild(guild);
        guilds.delete(guild);
        registry.unregister(worlds.worldName(guild));
        sender.sendMessage(Messages.get("success.delete", worlds.worldName(guild)));
    }

    private void listWorlds(CommandSender sender) {
        List<World> all = Bukkit.getWorlds();
        sender.sendMessage(Messages.get("info.world_list", all.size()));
        for (World w : all) {
            Location s = w.getSpawnLocation();
            sender.sendMessage(String.format("§7- §f%s §7env=%s spawn=(%d,%d,%d)",
                    w.getName(), w.getEnvironment(), s.getBlockX(), s.getBlockY(), s.getBlockZ()));
        }
    }

    private void whereami(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("error.player_only"));
            return;
        }
        Location l = player.getLocation();
        sender.sendMessage(Messages.get("info.whereami", l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[]{"home", "spawn", "upgrade", "info", "trust", "untrust",
                    "member", "deny", "undeny", "list", "visit", "clear", "flag", "card",
                    "alias", "sethome", "done", "kick", "near", "rate", "top", "middle",
                    "comment", "inbox", "swap", "grant", "merge", "unmerge", "confirm",
                    "help", "desc", "toggle", "template", "sub", "bulletin", "open", "close",
                    "flower", "gift", "board", "move", "admin"}) {
                out.add(s);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "flag" -> { out.add("set"); out.add("unset"); }
                case "member" -> { out.add("add"); out.add("remove"); }
                case "toggle" -> { out.add("titles"); }
                case "template" -> { out.addAll(java.util.List.of("create", "delete", "apply", "setflag", "list", "save", "paste", "list-schematics")); }
                case "sub" -> { out.addAll(java.util.List.of("create", "delete", "setflag", "list")); }
                case "bulletin" -> { out.addAll(java.util.List.of("set", "show", "clear")); }
                case "top" -> {
                    for (GuildWorld gw : guilds.findAll()) out.add(gw.guild().value());
                    out.addAll(java.util.List.of("rating", "level", "members", "entities", "visits"));
                }
                case "list" -> { out.add("mine"); }
                case "open" -> { out.addAll(java.util.List.of("0", "30", "60", "120")); }
                case "help" -> { out.addAll(COMMAND_HELP.keySet()); }
                case "admin" -> {
                    if (sender.isOp() || Permissions.hasAdminPerm(sender, Permissions.ADMIN)) {
                        out.addAll(java.util.List.of("create", "tp", "claim", "fill", "map", "upgrade-manor",
                                "upgrade-guild", "delete", "worlds", "whereami", "reload", "setowner",
                                "purge", "regen", "export", "fund", "citywall"));
                    }
                }
                default -> {
                    // 在线玩家补全
                    if (java.util.Set.of("card", "kick", "swap", "grant", "gift", "trust", "untrust", "deny", "undeny").contains(sub)) {
                        if (sub.equals("trust") || sub.equals("deny")) out.add("*");
                        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
                    }
                    // 公会名补全
                    if (java.util.Set.of("visit", "move").contains(sub)) {
                        for (GuildWorld gw : guilds.findAll()) out.add(gw.guild().value());
                    }
                    // 评分 1-10
                    if ("rate".equals(sub)) {
                        for (int i = 1; i <= 10; i++) out.add(String.valueOf(i));
                    }
                }
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "flag" -> { for (Flag f : Flag.values()) out.add(f.id()); }
                case "member" -> { for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName()); }
                case "top" -> { out.addAll(java.util.List.of("rating", "level", "members", "entities", "visits")); }
                case "template" -> {
                    String action = args[1].toLowerCase();
                    if (action.equals("apply") || action.equals("setflag") || action.equals("delete") || action.equals("paste")) {
                        if (sender instanceof Player player) {
                            Manor m = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
                            if (m != null) out.addAll(manors.listTemplates(m.guild()));
                        }
                        if (action.equals("paste") && schematicStore != null) {
                            out.addAll(schematicStore.list());
                        }
                    }
                }
                case "sub" -> {
                    if (args[1].equalsIgnoreCase("delete") || args[1].equalsIgnoreCase("setflag")) {
                        if (sender instanceof Player player) {
                            Manor m = manors.findByOwnerAnywhere(PlayerRef.of(player.getUniqueId())).orElse(null);
                            if (m != null) {
                                out.addAll(manors.getSubs(m.guild(), m.slot()).stream()
                                        .map(ManorRepository.SubEntry::name).toList());
                            }
                        }
                    }
                }
                case "admin" -> {
                    String action = args[1].toLowerCase();
                    if (java.util.Set.of("create", "tp", "claim", "fill", "map", "upgrade-manor",
                            "upgrade-guild", "delete", "setowner", "fund", "citywall").contains(action)) {
                        for (GuildWorld gw : guilds.findAll()) out.add(gw.guild().value());
                    }
                }
                case "rate" -> {
                    // 第三个参数是公会名
                    for (GuildWorld gw : guilds.findAll()) out.add(gw.guild().value());
                }
            }
        } else if (args.length == 4) {
            String sub = args[0].toLowerCase();
            if (sub.equals("flag") && args[1].equalsIgnoreCase("set")) {
                Flag f = Flag.byId(args[2]).orElse(null);
                if (f != null) {
                    switch (f.type()) {
                        case BOOLEAN -> { out.add("true"); out.add("false"); }
                        case INTEGER -> { out.addAll(java.util.List.of("-1", "0", "5", "10", "50", "100")); }
                        case DOUBLE -> { out.addAll(java.util.List.of("0", "100", "500", "1000")); }
                        default -> {}
                    }
                }
            } else if (sub.equals("template") && args[1].equalsIgnoreCase("setflag")) {
                for (Flag f : Flag.values()) out.add(f.id());
            } else if (sub.equals("sub") && args[1].equalsIgnoreCase("setflag")) {
                for (Flag f : Flag.values()) out.add(f.id());
            } else if (sub.equals("admin")) {
                String action = args[1].toLowerCase();
                if (action.equals("create")) {
                    for (TerrainPrepMode m : TerrainPrepMode.values()) out.add(m.name());
                } else if (action.equals("setowner")) {
                    for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
                } else if (action.equals("fund")) {
                    out.addAll(java.util.List.of("check", "add", "set"));
                }
            }
        } else if (args.length == 5) {
            String sub = args[0].toLowerCase();
            if (sub.equals("template") && args[1].equalsIgnoreCase("setflag")) {
                Flag f = Flag.byId(args[3]).orElse(null);
                if (f != null) {
                    switch (f.type()) {
                        case BOOLEAN -> { out.add("true"); out.add("false"); }
                        case INTEGER -> { out.addAll(java.util.List.of("-1", "0", "5", "10", "50", "100")); }
                        case DOUBLE -> { out.addAll(java.util.List.of("0", "100", "500", "1000")); }
                        default -> {}
                    }
                }
            } else if (sub.equals("sub") && args[1].equalsIgnoreCase("setflag")) {
                Flag f = Flag.byId(args[3]).orElse(null);
                if (f != null) {
                    switch (f.type()) {
                        case BOOLEAN -> { out.add("true"); out.add("false"); }
                        case INTEGER -> { out.addAll(java.util.List.of("-1", "0", "5", "10", "50", "100")); }
                        case DOUBLE -> { out.addAll(java.util.List.of("0", "100", "500", "1000")); }
                        default -> {}
                    }
                }
            }
        }
        return out;
    }
}
