package org.windy.guildshelter.adapter.provider;

import com.gyzer.API.Events.CreateGuildEvent;
import com.gyzer.API.Events.GuildDeleteEvent;
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
 * 把 LegendaryGuild 的生命周期事件翻译成对 {@link GuildService} 的调用，实现"入会自动有地皮"。
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

    public LegendaryGuildListener(GuildService service, GuildRepository guilds,
                                  GuildWorldRegistry registry, Logger logger) {
        this.service = service;
        this.guilds = guilds;
        this.registry = registry;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGuildCreate(CreateGuildEvent event) {
        String name = guildName(event.getGuild());
        if (name == null) {
            return;
        }
        GuildWorld gw = service.createGuild(new GuildId(name), ThreadLocalRandom.current().nextLong());
        registry.register(gw);
        logger.info("[GuildShelter] 公会创建 → 已建世界: " + gw.worldName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinGuildEvent event) {
        String guildName = guildName(event.getGuild());
        String playerName = event.getUser() != null ? event.getUser().getPlayer() : null;
        if (guildName == null || playerName == null || playerName.isBlank()) {
            return;
        }
        GuildId guild = new GuildId(guildName);
        // 公会世界若尚不存在（如插件安装前已建的公会），惰性补建。
        if (!guilds.exists(guild)) {
            GuildWorld gw = service.createGuild(guild, ThreadLocalRandom.current().nextLong());
            registry.register(gw);
        }
        UUID uuid = resolveUuid(playerName);
        Manor manor;
        try {
            manor = service.assignManor(guild, PlayerRef.of(uuid));
        } catch (GuildFullException e) {
            Player full = Bukkit.getPlayerExact(playerName);
            if (full != null) {
                full.sendMessage("§e[公会营地] 公会名额已满（" + e.capacity() + " 人），需公会升级后才能分配地皮。");
            }
            logger.info("[GuildShelter] " + guildName + " 名额已满(" + e.capacity() + ")，" + playerName + " 暂未分配地皮。");
            return;
        }
        Player p = Bukkit.getPlayerExact(playerName);
        if (p != null) {
            org.windy.guildshelter.GuildShelterPlugin.sendWelcome(p, guildName, manor.slot());
        }
        logger.info("[GuildShelter] " + guildName + " 新成员 " + playerName + " → 地皮 #" + manor.slot());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitGuildEvent event) {
        Player p = event.getPlayer();
        if (p == null) {
            return;
        }
        service.releaseManorAnywhere(PlayerRef.of(p.getUniqueId()));
        logger.info("[GuildShelter] 成员退出 " + p.getName() + " → 已释放其地皮。");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerBeKickFromGuildEvent event) {
        String playerName = event.getUser();
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        service.releaseManorAnywhere(PlayerRef.of(resolveUuid(playerName)));
        logger.info("[GuildShelter] 成员被踢 " + playerName + " → 已释放其地皮。");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGuildDelete(GuildDeleteEvent event) {
        String name = guildName(event.getGuild());
        if (name == null) {
            return;
        }
        GuildId guild = new GuildId(name);
        guilds.find(guild).ifPresent(gw -> registry.unregister(gw.worldName()));
        service.dissolveGuild(guild);
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
