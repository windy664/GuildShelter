package org.windy.guildshelter.adapter.provider;

import com.handy.guild.api.PlayerGuildApi;
import com.handy.guild.event.GuildCreateEvent;
import com.handy.guild.event.GuildDissolutionEvent;
import com.handy.guild.event.PlayerJoinGuildEvent;
import com.handy.guild.event.PlayerLeaveGuildEvent;
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
 * 把 PlayerGuild 的生命周期事件翻译成对 {@link GuildService} 的调用，实现"入会自动有地皮"。
 *
 * <p>GuildId = PlayerGuild 公会名。加入事件只给数字 ID，借在线玩家经 API 反查公会名；
 * 退出按 owner 全局查庄园释放（不需名字）；解散借 owner 玩家反查公会名。
 */
public final class PlayerGuildListener implements Listener {

    private final GuildService service;
    private final GuildRepository guilds;
    private final GuildWorldRegistry registry;
    private final Logger logger;
    private final org.bukkit.plugin.Plugin plugin;

    public PlayerGuildListener(GuildService service, GuildRepository guilds,
                               GuildWorldRegistry registry, Logger logger,
                               org.bukkit.plugin.Plugin plugin) {
        this.service = service;
        this.guilds = guilds;
        this.registry = registry;
        this.logger = logger;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGuildCreate(GuildCreateEvent event) {
        String name = event.getGuildName();
        if (name == null || name.isBlank()) {
            return;
        }
        // 首次建会的 Bukkit.createWorld 内部 setInitialSpawn 会同步 managedBlock 等出生区块；本事件
        // 多半在宿主 /guild create 的命令执行上下文里同步触发，嵌套 managedBlock 会死锁触发看门狗。
        // 推迟到干净 tick 执行即可正常生成。
        Bukkit.getScheduler().runTask(plugin, () -> {
            GuildWorld gw = service.createGuild(new GuildId(name), ThreadLocalRandom.current().nextLong());
            registry.register(gw);
            logger.info("[GuildShelter] 公会创建 → 已建世界: " + gw.worldName());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinGuildEvent event) {
        UUID uuid = event.getPlayerUuid();
        if (uuid == null) {
            return;
        }
        String guildName = resolveGuildName(uuid);
        if (guildName == null) {
            logger.warning("[GuildShelter] 加入事件无法解析公会名(玩家 " + uuid + ")，跳过分配地皮。");
            return;
        }
        GuildId guild = new GuildId(guildName);
        // 公会世界已存在：直接分配（无 createWorld，安全同步执行）。
        if (guilds.exists(guild)) {
            assignAndWelcome(guild, guildName, uuid);
            return;
        }
        // 惰性补建（如插件安装前已建的公会）：createWorld 不能在事件上下文同步跑（嵌套 managedBlock
        // 死锁），整段「建世界+分地皮+欢迎」推迟到干净 tick。createGuild 幂等，重复调用返回已有。
        Bukkit.getScheduler().runTask(plugin, () -> {
            GuildWorld gw = service.createGuild(guild, ThreadLocalRandom.current().nextLong());
            registry.register(gw);
            assignAndWelcome(guild, guildName, uuid);
        });
    }

    /** 分配地皮并发欢迎语（要求公会世界已存在；不触发 createWorld）。 */
    private void assignAndWelcome(GuildId guild, String guildName, UUID uuid) {
        Manor manor;
        try {
            manor = service.assignManor(guild, PlayerRef.of(uuid));
        } catch (GuildFullException e) {
            Player full = Bukkit.getPlayer(uuid);
            if (full != null) {
                full.sendMessage(Messages.get("error.guild_full", e.capacity()));
            }
            logger.info("[GuildShelter] " + guildName + " 名额已满(" + e.capacity() + ")，" + uuid + " 暂未分配地皮。");
            return;
        }
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            org.windy.guildshelter.GuildShelterPlugin.sendWelcome(p, guildName, manor.slot());
        }
        logger.info("[GuildShelter] " + guildName + " 新成员 " + uuid + " → 地皮 #" + manor.slot());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerLeave(PlayerLeaveGuildEvent event) {
        UUID uuid = event.getPlayer() != null
                ? event.getPlayer().getUniqueId()
                : (event.getOfflinePlayer() != null ? event.getOfflinePlayer().getUniqueId() : null);
        if (uuid == null) {
            return;
        }
        service.releaseManorAnywhere(PlayerRef.of(uuid));
        logger.info("[GuildShelter] 成员退出 " + uuid + " → 已释放其地皮。");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGuildDissolution(GuildDissolutionEvent event) {
        Player owner = event.getPlayer();
        String guildName = owner != null ? PlayerGuildApi.getInstance().getPlayerGuildName(owner) : null;
        if (guildName == null || guildName.isBlank()) {
            logger.warning("[GuildShelter] 解散事件无法解析公会名，世界/数据需手动清理。");
            return;
        }
        GuildId guild = new GuildId(guildName);
        guilds.find(guild).ifPresent(gw -> registry.unregister(gw.worldName()));
        service.dissolveGuild(guild);
        var mr = org.windy.guildshelter.GuildShelterPlugin.mergeRegistry();
        if (mr != null) mr.removeGuild(guild);
        logger.info("[GuildShelter] 公会解散 " + guildName + " → 已卸载世界并清理数据。");
    }

    private String resolveGuildName(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) {
            return null;
        }
        String name = PlayerGuildApi.getInstance().getPlayerGuildName(p);
        return (name == null || name.isBlank()) ? null : name;
    }
}
