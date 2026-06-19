package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 庄园级区块卸载：当庄园庄主和所有 trusted 都不在线超过 N 分钟，
 * 卸载该庄园实占范围内的 chunk（保留主城和道路 chunk）。
 * 庄主/trusted 登录时立即重载。
 */
public final class ManorChunkManager extends BukkitRunnable {

    private final GuildWorldRegistry registry;
    private final GuildRepository guilds;
    private final ManorRepository manors;
    private final int inactiveMinutes;
    private final boolean keepRoadLoaded;
    private final Logger logger;

    /** "guildId:slot" → 最后一次庄主/trusted 在线的时间戳。 */
    private final Map<String, Long> lastOnlineTime = new ConcurrentHashMap<>();
    /** 已卸载的庄园集合（避免重复卸载）。 */
    private final Set<String> unloadedManors = ConcurrentHashMap.newKeySet();

    public ManorChunkManager(GuildWorldRegistry registry, GuildRepository guilds, ManorRepository manors,
                             int inactiveMinutes, boolean keepRoadLoaded, Logger logger) {
        this.registry = registry;
        this.guilds = guilds;
        this.manors = manors;
        this.inactiveMinutes = inactiveMinutes;
        this.keepRoadLoaded = keepRoadLoaded;
        this.logger = logger;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        long thresholdMs = (long) inactiveMinutes * 60 * 1000;

        for (GuildWorld gw : guilds.findAll()) {
            World world = Bukkit.getWorld(gw.worldName());
            if (world == null) continue;
            LayoutCalculator layout = new LayoutCalculator(gw.layout());

            for (Manor manor : manors.findAll(gw.guild())) {
                String key = manor.guild().value() + ":" + manor.slot();
                boolean supervisorOnline = isSupervisorOnline(manor);

                if (supervisorOnline) {
                    lastOnlineTime.put(key, now);
                    // 如果之前卸载了，重新加载
                    if (unloadedManors.remove(key)) {
                        reloadManorChunks(world, gw, manor, layout);
                        logger.info("[GuildShelter] 重载庄园 chunk: " + manor.guild().value() + " #" + manor.slot());
                    }
                    continue;
                }

                // 无上级在线，检查是否超时
                Long lastTime = lastOnlineTime.get(key);
                if (lastTime == null) {
                    lastOnlineTime.put(key, now);
                    continue;
                }
                if (now - lastTime < thresholdMs) continue;
                if (unloadedManors.contains(key)) continue; // 已卸载

                // 超时，卸载该庄园 chunk
                unloadManorChunks(world, gw, manor, layout);
                unloadedManors.add(key);
                logger.info("[GuildShelter] 卸载庄园 chunk: " + manor.guild().value() + " #" + manor.slot()
                        + "（无上级在线 " + inactiveMinutes + " 分钟）");
            }
        }
    }

    /** 庄主或任一 trusted 是否在线。 */
    private boolean isSupervisorOnline(Manor manor) {
        Player owner = Bukkit.getPlayer(manor.owner().uuid());
        if (owner != null && owner.isOnline()) return true;
        for (PlayerRef ref : manor.trusted()) {
            Player p = Bukkit.getPlayer(ref.uuid());
            if (p != null && p.isOnline()) return true;
        }
        return false;
    }

    /** 卸载庄园实占范围内的 chunk。 */
    private void unloadManorChunks(World world, GuildWorld gw, Manor manor, LayoutCalculator layout) {
        ChunkRegion region = layout.activeRegion(manor.slot(), manor.level())
                .shift(gw.originChunkX(), gw.originChunkZ());
        int unloaded = 0;
        for (int cx = region.minChunkX(); cx <= region.maxChunkX(); cx++) {
            for (int cz = region.minChunkZ(); cz <= region.maxChunkZ(); cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                // 跳过有玩家的 chunk
                boolean hasPlayers = false;
                for (org.bukkit.entity.Entity e : world.getChunkAt(cx, cz).getEntities()) {
                    if (e instanceof Player) { hasPlayers = true; break; }
                }
                if (hasPlayers) continue;
                world.unloadChunkRequest(cx, cz);
                unloaded++;
            }
        }
    }

    /** 重新加载庄园实占范围内的 chunk。 */
    private void reloadManorChunks(World world, GuildWorld gw, Manor manor, LayoutCalculator layout) {
        ChunkRegion region = layout.activeRegion(manor.slot(), manor.level())
                .shift(gw.originChunkX(), gw.originChunkZ());
        for (int cx = region.minChunkX(); cx <= region.maxChunkX(); cx++) {
            for (int cz = region.minChunkZ(); cz <= region.maxChunkZ(); cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    world.loadChunk(cx, cz, true);
                }
            }
        }
    }

    /** 玩家登录时调用：立即重载其庄园 chunk。 */
    public void onPlayerJoin(UUID playerId) {
        manors.findByOwnerAnywhere(PlayerRef.of(playerId)).ifPresent(manor -> {
            String key = manor.guild().value() + ":" + manor.slot();
            lastOnlineTime.put(key, System.currentTimeMillis());
            if (unloadedManors.remove(key)) {
                GuildWorld gw = guilds.find(manor.guild()).orElse(null);
                if (gw == null) return;
                World world = Bukkit.getWorld(gw.worldName());
                if (world == null) return;
                reloadManorChunks(world, gw, manor, new LayoutCalculator(gw.layout()));
                logger.info("[GuildShelter] 玩家登录重载庄园 chunk: " + manor.guild().value() + " #" + manor.slot());
            }
        });
    }
}
