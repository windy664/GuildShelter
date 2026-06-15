package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.windy.guildshelter.adapter.bukkit.GuildWorldRegistry;
import org.windy.guildshelter.domain.layout.Classification;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.GuildWorld;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试/可视化用：玩家在公会世界里移动时，按 {@link LayoutCalculator} 判断脚下属于
 * 主城 / 地皮#N / 道路，并在区域变化时弹 title。用于验证网格是否正确叠在自然地形上、
 * 陆地锚定（origin 偏移）是否正确。
 *
 * <p>仅在跨 chunk 时计算（按 chunk 判归属），且只在归类变化时发 title，避免刷屏。
 */
public final class RegionTitleListener implements Listener {

    private final LayoutCalculator layout;
    private final GuildWorldRegistry registry;

    /** 每个玩家上一次所在 chunk 与归类签名，用于去重。 */
    private final Map<UUID, long[]> lastChunk = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastKey = new ConcurrentHashMap<>();

    public RegionTitleListener(LayoutCalculator layout, GuildWorldRegistry registry) {
        this.layout = layout;
        this.registry = registry;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        int cx = event.getTo().getBlockX() >> 4;
        int cz = event.getTo().getBlockZ() >> 4;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        long[] prev = lastChunk.get(id);
        if (prev != null && prev[0] == cx && prev[1] == cz) {
            return; // 同一 chunk，不重复计算
        }
        lastChunk.put(id, new long[]{cx, cz});

        GuildWorld gw = registry.get(player.getWorld().getName());
        if (gw == null) {
            lastKey.remove(id);
            return;
        }

        // 世界 chunk → layout chunk（减去 origin 偏移）
        int lx = cx - gw.originChunkX();
        int lz = cz - gw.originChunkZ();
        Classification c = layout.classify(lx, lz);

        String key = c.type() + ":" + c.slot();
        if (key.equals(lastKey.get(id))) {
            return; // 归类没变，不重复弹 title
        }
        lastKey.put(id, key);

        switch (c.type()) {
            case MAIN_CITY -> player.sendTitle("§6§l主城", "§e公会中心", 5, 30, 10);
            case PLOT -> player.sendTitle("§a§l地皮 #" + c.slot(),
                    c.border() ? "§7成员住所 · 边界" : "§7成员住所", 5, 30, 10);
            case ROAD -> player.sendTitle("§7§l道路", "§8公共区域", 5, 20, 10);
        }
    }
}
