package org.windy.guildshelter.adapter.provider;

import com.github.shetuan.model.Club;
import com.github.shetuan.model.ClubMember;
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
import org.windy.guildshelter.service.GuildFullException;
import org.windy.guildshelter.service.GuildService;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Shetuan 不发任何自定义事件，所以"入会自动分地皮 / 退会释放 / 解散清理"这套生命周期没有事件可挂。
 * 这里自造一个轮子：在主线程上<b>定时 diff</b>——以 Shetuan 的社团成员名单为准，对齐本插件已分配的地皮。
 *
 * <p>每轮：
 * <ul>
 *   <li>社团世界不存在 → 建（含已装本插件前就存在的老社团，惰性补建）；</li>
 *   <li>成员无地皮 → 分配（在线则提示）；</li>
 *   <li>已有地皮的主人已不在成员名单 → 释放（退会/被踢）；</li>
 *   <li>库里有、Shetuan 里已无的社团世界 → 卸载并清数据（解散）。</li>
 * </ul>
 *
 * <p>解散清扫只动 GuildId 形如 UUID 的世界（Shetuan 的签名），避免误删其它公会插件建的世界。
 * 每个社团单独 try/catch，单个社团出错不影响同批其它社团。
 */
public final class ShetuanSyncTask extends BukkitRunnable {

    private final ShetuanAccess access;
    private final GuildService service;
    private final GuildRepository guilds;
    private final ManorRepository manors;
    private final GuildWorldRegistry registry;
    private final Logger logger;

    public ShetuanSyncTask(ShetuanAccess access, GuildService service, GuildRepository guilds,
                           ManorRepository manors, GuildWorldRegistry registry, Logger logger) {
        this.access = access;
        this.service = service;
        this.guilds = guilds;
        this.manors = manors;
        this.registry = registry;
        this.logger = logger;
    }

    @Override
    public void run() {
        Set<String> liveGuilds = new HashSet<>();
        for (Club club : access.allClubs()) {
            try {
                liveGuilds.add(syncClub(club).value());
            } catch (RuntimeException e) {
                logger.warning("[GuildShelter] 同步社团 " + club.id() + " 出错：" + e.getMessage());
            }
        }
        sweepDissolved(liveGuilds);
    }

    /** 对齐单个社团的世界与成员地皮，返回其 GuildId。 */
    private GuildId syncClub(Club club) {
        GuildId guild = new GuildId(club.id().toString());

        if (!guilds.exists(guild)) {
            GuildWorld gw = service.createGuild(guild, ThreadLocalRandom.current().nextLong());
            registry.register(gw);
            logger.info("[GuildShelter] 社团 " + display(club) + " → 已建世界 " + gw.worldName());
        }

        Set<UUID> memberUuids = new HashSet<>();
        boolean full = false;
        for (ClubMember member : access.membersOf(club.id())) {
            UUID uuid = member.uuid();
            memberUuids.add(uuid);
            PlayerRef ref = PlayerRef.of(uuid);
            if (!full && manors.findByOwner(guild, ref).isEmpty()) {
                try {
                    Manor manor = service.assignManor(guild, ref);
                    notifyIfOnline(uuid, manor);
                    logger.info("[GuildShelter] " + display(club) + " 新成员 " + uuid + " → 地皮 #" + manor.slot());
                } catch (GuildFullException e) {
                    logger.info("[GuildShelter] " + display(club) + " 名额已满(" + e.capacity()
                            + ")，余下成员需公会升级后再分配。");
                    full = true; // 满了就别再试，但仍要把名单收全以便下面正确释放退会者
                }
            }
        }

        for (Manor manor : manors.findAll(guild)) {
            if (!memberUuids.contains(manor.owner().uuid())) {
                service.releaseManor(guild, manor.owner());
                logger.info("[GuildShelter] " + display(club) + " 成员 " + manor.owner().uuid()
                        + " 已退出 → 释放地皮 #" + manor.slot());
            }
        }
        return guild;
    }

    /** 库里有、Shetuan 里已无的 UUID 形态世界 = 已解散，卸载并清数据。 */
    private void sweepDissolved(Set<String> liveGuilds) {
        for (GuildWorld gw : guilds.findAll()) {
            String value = gw.guild().value();
            if (isUuid(value) && !liveGuilds.contains(value)) {
                registry.unregister(gw.worldName());
                service.dissolveGuild(gw.guild());
                var mr = org.windy.guildshelter.GuildShelterPlugin.mergeRegistry();
                if (mr != null) mr.removeGuild(gw.guild());
                logger.info("[GuildShelter] 社团已解散(" + value + ") → 已卸载世界并清理数据。");
            }
        }
    }

    private void notifyIfOnline(UUID uuid, Manor manor) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            org.windy.guildshelter.GuildShelterPlugin.sendWelcome(p, manor.guild().value(), manor.slot());
        }
    }

    private String display(Club club) {
        String name = access.displayName(club);
        return (name == null || name.isBlank()) ? club.id().toString() : name;
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
