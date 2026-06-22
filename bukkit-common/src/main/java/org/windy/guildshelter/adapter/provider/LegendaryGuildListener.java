package org.windy.guildshelter.adapter.provider;

import com.gyzer.API.Events.CreateGuildEvent;
import com.gyzer.API.Events.GuildDeleteEvent;
import com.gyzer.API.Events.GuildLevelupEvent;
import com.gyzer.API.Events.PlayerBeKickFromGuildEvent;
import com.gyzer.API.Events.PlayerJoinGuildEvent;
import com.gyzer.API.Events.PlayerQuitGuildEvent;
import com.gyzer.Data.Guild.Guild;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.windy.guildshelter.adapter.bukkit.GuildWorldRegistry;
import org.windy.guildshelter.adapter.bukkit.Messages;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.service.GuildFullException;
import org.windy.guildshelter.service.GuildService;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * 把 LegendaryGuild 的生命周期事件翻译成对 {@link GuildService} 的调用，实现"入会自动有庄园"。
 *
 * <p>GuildId = LegendaryGuild 公会名（事件直接携带 {@link Guild} 对象，无需反查）。
 * LegendaryGuild 以玩家名管理成员，本类用 {@link #resolveUuid(String)} 把名字解析成 UUID 后交给
 * domain（domain 一律用 UUID）。在离线模式下该 UUID 由玩家名确定性派生，分配 slot 稳定一致。
 */
public final class LegendaryGuildListener implements Listener {

    private final GuildService service;
    private final GuildRepository guilds;
    private final GuildWorldRegistry registry;
    private final Logger logger;
    private final org.bukkit.plugin.Plugin plugin;

    public LegendaryGuildListener(GuildService service, GuildRepository guilds,
                                  GuildWorldRegistry registry, Logger logger,
                                  org.bukkit.plugin.Plugin plugin) {
        this.service = service;
        this.guilds = guilds;
        this.registry = registry;
        this.logger = logger;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGuildCreate(CreateGuildEvent event) {
        String name = guildName(event.getGuild());
        if (name == null) {
            return;
        }
        // 首次建会的 Bukkit.createWorld 内部 setInitialSpawn 会同步 managedBlock 等出生区块；本事件
        // 多半在宿主建会命令的执行上下文里同步触发，嵌套 managedBlock 会死锁触发看门狗。
        // 推迟到干净 tick 执行即可正常生成。
        Bukkit.getScheduler().runTask(plugin, () ->
            // 异步版：Iris 世界在异步线程建、建好回主线程回调（Iris 禁止主线程 create()）；非 Iris 即同步。
            service.createGuildAsync(new GuildId(name), ThreadLocalRandom.current().nextLong(), gw -> {
                registry.register(gw);
                logger.info("[GuildShelter] 公会创建 → 已建世界: " + gw.worldName());
            }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinGuildEvent event) {
        String guildName = guildName(event.getGuild());
        String playerName = event.getUser() != null ? event.getUser().getPlayer() : null;
        if (guildName == null || playerName == null || playerName.isBlank()) {
            return;
        }
        GuildId guild = new GuildId(guildName);
        // 公会营地已存在：直接分配（无 createWorld，安全同步执行）。
        if (guilds.exists(guild)) {
            assignAndWelcome(guild, guildName, playerName);
            return;
        }
        // 惰性补建（如插件安装前已建的公会）：createWorld 不能在事件上下文同步跑（嵌套 managedBlock
        // 死锁），整段「建世界+分庄园+欢迎」推迟到干净 tick。createGuild 幂等，重复调用返回已有。
        Bukkit.getScheduler().runTask(plugin, () ->
            service.createGuildAsync(guild, ThreadLocalRandom.current().nextLong(), gw -> {
                registry.register(gw);
                assignAndWelcome(guild, guildName, playerName);
            }));
    }

    /** 分配庄园并发欢迎语（要求公会营地已存在；不触发 createWorld）。 */
    private void assignAndWelcome(GuildId guild, String guildName, String playerName) {
        UUID uuid = resolveUuid(playerName);
        Manor manor;
        try {
            manor = service.assignManor(guild, PlayerRef.of(uuid));
        } catch (GuildFullException e) {
            Player full = Bukkit.getPlayerExact(playerName);
            if (full != null) {
                full.sendMessage(Messages.get("error.guild_full", e.capacity()));
            }
            logger.info("[GuildShelter] " + guildName + " 名额已满(" + e.capacity() + ")，" + playerName + " 暂未分配庄园。");
            return;
        }
        Player p = Bukkit.getPlayerExact(playerName);
        if (p != null) {
            org.windy.guildshelter.GuildShelterPlugin.sendWelcome(p, guildName, manor.slot());
        }
        logger.info("[GuildShelter] " + guildName + " 新成员 " + playerName + " → 庄园 #" + manor.slot());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitGuildEvent event) {
        Player p = event.getPlayer();
        if (p == null) {
            return;
        }
        service.releaseManorAnywhere(PlayerRef.of(p.getUniqueId()));
        logger.info("[GuildShelter] 成员退出 " + p.getName() + " → 已释放其庄园。");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerBeKickFromGuildEvent event) {
        String playerName = event.getUser();
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        service.releaseManorAnywhere(PlayerRef.of(resolveUuid(playerName)));
        logger.info("[GuildShelter] 成员被踢 " + playerName + " → 已释放其庄园。");
    }

    /** 宿主公会升级 → 跟随把 GuildShelter 公会等级 +1（仍走我们自己的等级曲线，封顶在 config max-level）。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGuildLevelup(GuildLevelupEvent event) {
        String name = guildName(event.getGuild());
        if (name == null) {
            return;
        }
        GuildId guild = new GuildId(name);
        if (!guilds.exists(guild)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (service.upgradeGuild(guild)) {
                logger.info("[GuildShelter] 宿主公会升级 → " + name + " 跟随升一级。");
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGuildDelete(GuildDeleteEvent event) {
        String name = guildName(event.getGuild());
        if (name == null) {
            return;
        }
        GuildId guild = new GuildId(name);
        guilds.find(guild).ifPresent(gw -> registry.unregister(gw.worldName()));
        service.dissolveGuild(guild); // 主城信任缓存经 MembershipChangeListener.onGuildDissolved 自动清理
        var mr = org.windy.guildshelter.GuildShelterPlugin.mergeRegistry();
        if (mr != null) mr.removeGuild(guild);
        logger.info("[GuildShelter] 公会解散 " + name + " → 已卸载世界并清理数据。");
    }

    private static String guildName(Guild guild) {
        if (guild == null) {
            return null;
        }
        String name = guild.getGuild();
        return (name == null || name.isBlank()) ? null : name;
    }

    /** 玩家名 → UUID：优先在线玩家（拿真 UUID），否则离线确定性 UUID。同一服务器模式下结果稳定。 */
    private static UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online.getUniqueId() : Bukkit.getOfflinePlayer(name).getUniqueId();
    }
}
