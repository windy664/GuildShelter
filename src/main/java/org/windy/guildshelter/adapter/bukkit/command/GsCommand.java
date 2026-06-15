package org.windy.guildshelter.adapter.bukkit.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.windy.guildshelter.adapter.bukkit.GuildWorldRegistry;
import org.windy.guildshelter.adapter.bukkit.world.WorldManager;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.service.GuildService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /gs 命令分发（验证 + 管理）。
 *
 * <p>admin 子命令：create 建公会世界、tp 进主城、claim 给自己分配一块地皮（自动整地）、
 * delete 卸载、worlds/whereami 诊断。普通玩家命令将在接入 GuildProvider 后补全。
 */
public final class GsCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERM = "guildshelter.admin";

    private final WorldManager worlds;
    private final GuildRepository guilds;
    private final GuildService service;
    private final GuildWorldRegistry registry;
    private final LayoutCalculator layout;

    public GsCommand(WorldManager worlds, GuildRepository guilds, GuildService service,
                     GuildWorldRegistry registry, LayoutCalculator layout) {
        this.worlds = worlds;
        this.guilds = guilds;
        this.service = service;
        this.registry = registry;
        this.layout = layout;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || !args[0].equalsIgnoreCase("admin")) {
            sender.sendMessage("用法: /gs admin <create|tp|claim|delete|worlds|whereami> [公会id]");
            return true;
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
            case "delete" -> delete(sender, args);
            case "worlds" -> listWorlds(sender);
            case "whereami" -> whereami(sender);
            default -> sender.sendMessage("用法: /gs admin <create|tp|claim|delete|worlds|whereami> [公会id]");
        }
        return true;
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

        Manor manor = service.assignManor(guild, PlayerRef.of(player.getUniqueId()));
        // 分配后 allocatedSlots 可能变化，重新读
        gw = guilds.find(guild).orElse(gw);
        registry.register(gw);

        World world = Bukkit.getWorld(gw.worldName());
        if (world == null) {
            sender.sendMessage("§c世界加载失败: " + gw.worldName());
            return;
        }
        ChunkRegion active = layout.activeRegion(manor.slot(), manor.level())
                .shift(gw.originChunkX(), gw.originChunkZ());
        int cx = (active.minBlockX() + active.maxBlockX()) / 2;
        int cz = (active.minBlockZ() + active.maxBlockZ()) / 2;
        world.loadChunk(cx >> 4, cz >> 4, true);
        int cy = world.getHighestBlockYAt(cx, cz) + 1;
        player.teleport(new Location(world, cx + 0.5, cy, cz + 0.5));
        sender.sendMessage("§a已分配地皮 #" + manor.slot() + "（等级 " + manor.level()
                + "），整地进行中。你已被传送过去。");
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
            out.add("admin");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            out.add("create");
            out.add("tp");
            out.add("claim");
            out.add("delete");
            out.add("worlds");
            out.add("whereami");
        }
        return out;
    }
}
