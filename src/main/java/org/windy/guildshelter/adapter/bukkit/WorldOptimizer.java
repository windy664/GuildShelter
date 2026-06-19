package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.port.GuildRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 世界级优化：定时检查公会营地，无玩家超过 N 分钟则卸载（或卸载 chunk）。
 * 玩家进入时由 WorldManager.ensureWorld() 自动重载。
 */
public final class WorldOptimizer extends BukkitRunnable {

    private final GuildWorldRegistry registry;
    private final GuildRepository guilds;
    private final String mode; // "world" 或 "chunk"
    private final int inactiveMinutes;
    private final boolean keepSpawnLoaded;
    private final Logger logger;

    /** 世界名 → 最后一次有玩家的时间戳。 */
    private final Map<String, Long> lastPlayerTime = new ConcurrentHashMap<>();

    public WorldOptimizer(GuildWorldRegistry registry, GuildRepository guilds,
                          String mode, int inactiveMinutes, boolean keepSpawnLoaded, Logger logger) {
        this.registry = registry;
        this.guilds = guilds;
        this.mode = mode;
        this.inactiveMinutes = inactiveMinutes;
        this.keepSpawnLoaded = keepSpawnLoaded;
        this.logger = logger;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        long thresholdMs = (long) inactiveMinutes * 60 * 1000;

        for (GuildWorld gw : guilds.findAll()) {
            World world = Bukkit.getWorld(gw.worldName());
            if (world == null) continue; // 已卸载

            // 记录有玩家的时间
            if (!world.getPlayers().isEmpty()) {
                lastPlayerTime.put(gw.worldName(), now);
                continue;
            }

            // 无玩家，检查是否超时
            Long lastTime = lastPlayerTime.get(gw.worldName());
            if (lastTime == null) {
                lastPlayerTime.put(gw.worldName(), now); // 首次记录
                continue;
            }
            if (now - lastTime < thresholdMs) continue;

            // 超时，执行卸载
            if ("chunk".equals(mode)) {
                unloadChunks(world, gw);
            } else {
                unloadWorld(world, gw);
            }
        }
    }

    /** 卸载整个世界。 */
    private void unloadWorld(World world, GuildWorld gw) {
        logger.info("[GuildShelter] 卸载无活跃公会营地: " + world.getName()
                + "（无玩家 " + inactiveMinutes + " 分钟）");
        Bukkit.unloadWorld(world, true);
        registry.unregister(world.getName());
        lastPlayerTime.remove(world.getName());
    }

    /** 只卸载非主城 chunk（保留出生点附近）。 */
    private void unloadChunks(World world, GuildWorld gw) {
        int unloaded = 0;
        int spawnCX = world.getSpawnLocation().getBlockX() >> 4;
        int spawnCZ = world.getSpawnLocation().getBlockZ() >> 4;
        for (var chunk : world.getLoadedChunks()) {
            int cx = chunk.getX(), cz = chunk.getZ();
            // 保留出生点附近 3x3 chunk
            if (keepSpawnLoaded && Math.abs(cx - spawnCX) <= 1 && Math.abs(cz - spawnCZ) <= 1) {
                continue;
            }
            // 保留有玩家的 chunk
            boolean hasPlayers = false;
            for (org.bukkit.entity.Entity e : chunk.getEntities()) {
                if (e instanceof Player) { hasPlayers = true; break; }
            }
            if (hasPlayers) continue;
            world.unloadChunkRequest(cx, cz);
            unloaded++;
        }
        if (unloaded > 0) {
            logger.info("[GuildShelter] 卸载 " + world.getName() + " 的 " + unloaded + " 个 chunk");
        }
    }

    /** 世界有玩家进入时调用，重置计时器。 */
    public void onPlayerEnter(String worldName) {
        lastPlayerTime.put(worldName, System.currentTimeMillis());
    }
}
