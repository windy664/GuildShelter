package org.windy.guildshelter.adapter.provider;

import com.guild.GuildPlugin;
import com.guild.guild.Guild;
import com.guild.guild.GuildManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.windy.guildshelter.adapter.bukkit.GuildWorldRegistry;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;
import org.windy.guildshelter.service.GuildService;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Guild(com.guild)和 Shetuan 一样不发自定义事件，所以同样用定时 diff 把生命周期补出来。
 * 结构与 {@link ShetuanSyncTask} 一致，仅 GuildId 取会名（该插件以会名为主键）。
 *
 * <p>每轮(主线程)：社团世界不存在→建；成员无地皮→分；地皮主人已不在成员名单→释放；
 * 库里有、Guild 里已无的世界→解散清理。
 *
 * <p><b>解散清扫的守卫</b>：本插件的 GuildId 是会名，无法靠形状区分来源，所以只扫
 * “GuildId <b>不</b>形如 UUID” 的世界——这样能与 {@link ShetuanSyncTask}(只扫形如 UUID 的)互补，
 * 二者同开互不误删。注意：若同时再装 PlayerGuild/LegendaryGuild 等<b>同样以会名为 key</b>的插件，
 * 本清扫可能误删其世界（属配置冲突，正常一个服只装一个公会插件）。可用 config 关掉清扫。
 */
public final class GuildPluginSyncTask extends BukkitRunnable {

    private final GuildService service;
    private final GuildRepository guilds;
    private final ManorRepository manors;
    private final GuildWorldRegistry registry;
    private final Logger logger;
    private final boolean disbandSweep;

    public GuildPluginSyncTask(GuildService service, GuildRepository guilds, ManorRepository manors,
                               GuildWorldRegistry registry, Logger logger, boolean disbandSweep) {
        this.service = service;
        this.guilds = guilds;
        this.manors = manors;
        this.registry = registry;
        this.logger = logger;
        this.disbandSweep = disbandSweep;
    }

    @Override
    public void run() {
        GuildManager gm = manager();
        if (gm == null) {
            return;
        }
        Set<String> liveGuilds = new HashSet<>();
        for (Guild guild : gm.getGuilds().values()) {
            try {
                liveGuilds.add(syncGuild(guild).value());
            } catch (RuntimeException e) {
                logger.warning("[GuildShelter] 同步公会 " + guild.getName() + " 出错：" + e.getMessage());
            }
        }
        if (disbandSweep) {
            sweepDissolved(liveGuilds);
        }
    }

    /** 对齐单个公会的世界与成员地皮，返回其 GuildId。 */
    private GuildId syncGuild(Guild guild) {
        GuildId id = new GuildId(guild.getName());

        if (!guilds.exists(id)) {
            GuildWorld gw = service.createGuild(id, ThreadLocalRandom.current().nextLong());
            registry.register(gw);
            logger.info("[GuildShelter] 公会 " + guild.getName() + " → 已建世界 " + gw.worldName());
        }

        Set<UUID> memberUuids = new HashSet<>(guild.getMembers().keySet());
        for (UUID uuid : memberUuids) {
            PlayerRef ref = PlayerRef.of(uuid);
            if (manors.findByOwner(id, ref).isEmpty()) {
                Manor manor = service.assignManor(id, ref);
                notifyIfOnline(uuid, manor);
                logger.info("[GuildShelter] " + guild.getName() + " 新成员 " + uuid + " → 地皮 #" + manor.slot());
            }
        }

        for (Manor manor : manors.findAll(id)) {
            if (!memberUuids.contains(manor.owner().uuid())) {
                service.releaseManor(id, manor.owner());
                logger.info("[GuildShelter] " + guild.getName() + " 成员 " + manor.owner().uuid()
                        + " 已退出 → 释放地皮 #" + manor.slot());
            }
        }
        return id;
    }

    /** 库里有、Guild 里已无、且 GuildId 不形如 UUID(避开 Shetuan 的世界) = 已解散，卸载并清数据。 */
    private void sweepDissolved(Set<String> liveGuilds) {
        for (GuildWorld gw : guilds.findAll()) {
            String value = gw.guild().value();
            if (!isUuid(value) && !liveGuilds.contains(value)) {
                registry.unregister(gw.worldName());
                service.dissolveGuild(gw.guild());
                logger.info("[GuildShelter] 公会已解散(" + value + ") → 已卸载世界并清理数据。");
            }
        }
    }

    private void notifyIfOnline(UUID uuid, Manor manor) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.sendMessage("§a[公会营地] 已为你分配地皮 #" + manor.slot() + "，使用 /gs 前往。");
        }
    }

    private static GuildManager manager() {
        GuildPlugin plugin = GuildPlugin.getInstance();
        return plugin == null ? null : plugin.getGuildManager();
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
