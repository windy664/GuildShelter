package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Xaero's Minimap 集成：在小地图上显示公会营地 waypoint。
 *
 * <p>实现方式：
 * <ul>
 *   <li>检测到 Xaero's Fair-Play Server → 用其 API 同步 waypoint</li>
 *   <li>否则 → 写 waypoint 文件到世界目录（客户端自动读取）</li>
 * </ul>
 *
 * <p>waypoint 格式（Xaero's .txt）：
 * <pre>
 * name;x;y;z;color;disabled;type
 * </pre>
 * 颜色值：0=青, 1=橙, 2=品红, 3=白, 4=粉, 5=灰, 6=淡蓝, 7=棕, 8=黄, 9=淡绿
 */
public final class XaeroIntegration implements Listener {

    private static final int COLOR_GUILD = 8;   // 黄色 = 公会主城
    private static final int COLOR_PLOT = 10;    // 淡绿 = 成员庄园
    private static final int COLOR_EMPTY = 5;    // 灰色 = 空闲庄园

    private final GuildWorldRegistry registry;
    private final GuildRepository guilds;
    private final ManorRepository manors;
    private final Plugin plugin;
    private final Logger logger;
    private final boolean fairPlayDetected;

    /** 已同步 waypoint 的玩家集合（避免重复写入）。 */
    private final Set<UUID> synced = ConcurrentHashMap.newKeySet();

    public XaeroIntegration(GuildWorldRegistry registry, GuildRepository guilds,
                            ManorRepository manors, Plugin plugin, Logger logger) {
        this.registry = registry;
        this.guilds = guilds;
        this.manors = manors;
        this.plugin = plugin;
        this.logger = logger;
        this.fairPlayDetected = plugin.getServer().getPluginManager().getPlugin("XaeroMinimap") != null
                || isModPresent("xaerominimap");
        if (fairPlayDetected) {
            logger.info("[GuildShelter] 检测到 Xaero's Minimap，小地图集成已启用。");
        }
    }

    /** 是否已安装 Xaero's 相关 mod（NeoForge 侧检测）。 */
    private static boolean isModPresent(String modId) {
        try {
            Class.forName("net.neoforged.fml.loading.FMLLoader");
            // NeoForge 环境：尝试加载 Xaero 的核心类
            Class.forName("xaero." + modId + ".XaeroMinimap");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** 玩家进入公会营地时同步 waypoint。 */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        GuildWorld gw = registry.get(player.getWorld().getName());
        if (gw != null) {
            syncWaypoints(player, gw);
        } else {
            synced.remove(player.getUniqueId());
        }
    }

    /** 玩家登录时如果在公会营地则同步。 */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 延迟 1 秒等世界加载完毕
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            GuildWorld gw = registry.get(player.getWorld().getName());
            if (gw != null) {
                syncWaypoints(player, gw);
            }
        }, 20L);
    }

    /**
     * 为玩家同步当前公会营地的所有 waypoint（主城 + 庄园）。
     * 写入到世界目录的 XaeroWaypoints 文件，客户端自动读取。
     */
    public void syncWaypoints(Player player, GuildWorld gw) {
        UUID id = player.getUniqueId();
        if (synced.contains(id)) return;
        synced.add(id);

        if (!fairPlayDetected) {
            // 无 Fair-Play Server → 写文件到世界目录
            writeWaypointFile(player, gw);
            return;
        }
        // Fair-Play Server 在 → 用 API（待实现，当前 fallback 到文件方式）
        writeWaypointFile(player, gw);
    }

    /**
     * 写 Xaero's waypoint 文件到世界目录。
     * 路径: {worldDir}/XaeroWaypoints/{playerUUID}/{dimName}/waypoints.txt
     */
    private void writeWaypointFile(Player player, GuildWorld gw) {
        World world = plugin.getServer().getWorld(gw.worldName());
        if (world == null) return;

        // Xaero's waypoint 目录结构
        File worldDir = world.getWorldFolder();
        File xaeroDir = new File(worldDir, "XaeroWaypoints/" + player.getUniqueId() + "/" + gw.worldName());
        xaeroDir.mkdirs();
        File waypointFile = new File(xaeroDir, "guild_camps.txt");

        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        int originX = gw.originChunkX() << 4;
        int originZ = gw.originChunkZ() << 4;

        StringBuilder sb = new StringBuilder();
        // 主城 waypoint
        int cityX = layout.spawnBlockX() + originX;
        int cityZ = layout.spawnBlockZ() + originZ;
        int cityY = world.getHighestBlockYAt(cityX, cityZ) + 1;
        sb.append(String.format("公会主城 %s;%d;%d;%d;%d;false;0\n",
                gw.guild().value(), cityX, cityY, cityZ, COLOR_GUILD));

        // 庄园 waypoints
        for (Manor manor : manors.findAll(gw.guild())) {
            int plotCenterX = layout.activeRegion(manor.slot(), manor.level()).centerBlockX() + originX;
            int plotCenterZ = layout.activeRegion(manor.slot(), manor.level()).centerBlockZ() + originZ;
            int plotY = world.getHighestBlockYAt(plotCenterX, plotCenterZ) + 1;
            int color = manor.level() > 0 ? COLOR_PLOT : COLOR_EMPTY;
            sb.append(String.format("庄园#%d;%d;%d;%d;%d;false;0\n",
                    manor.slot(), plotCenterX, plotY, plotCenterZ, color));
        }

        try (FileWriter fw = new FileWriter(waypointFile)) {
            fw.write(sb.toString());
        } catch (IOException e) {
            logger.warning("[GuildShelter] 写入 Xaero waypoint 失败: " + e.getMessage());
        }
    }

    /** 清除玩家的 waypoint 同步状态（退出世界时调用）。 */
    public void onPlayerLeave(UUID playerId) {
        synced.remove(playerId);
    }

    /** 公会营地变更时清除所有同步缓存（庄园增减后需重新生成）。 */
    public void invalidateAll() {
        synced.clear();
    }
}
