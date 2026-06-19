package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.windy.guildshelter.adapter.bukkit.GuildWorldRegistry;
import org.windy.guildshelter.adapter.bukkit.WorldCache;
import org.windy.guildshelter.domain.layout.Classification;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.rule.LevelRules;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家在公会世界里移动时，按 {@link LayoutCalculator} 判断脚下属于主城 / 地皮#N / 道路，
 * 并在区域变化时弹 title。地皮会进一步标注：有没有被认领、主人、庄园等级、是否在已解锁的实占范围内。
 *
 * <p>仅在跨 chunk 时计算，且只在归类变化时发 title，避免刷屏。
 */
public final class RegionTitleListener implements Listener {

    private final GuildWorldRegistry registry;
    private final WorldCache cache;
    private final LevelRules levels;

    /** 每个玩家上一次所在 chunk 与归类签名，用于去重。 */
    private final Map<UUID, long[]> lastChunk = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastKey = new ConcurrentHashMap<>();

    public RegionTitleListener(GuildWorldRegistry registry, WorldCache cache, LevelRules levels) {
        this.registry = registry;
        this.cache = cache;
        this.levels = levels;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (!org.windy.guildshelter.adapter.bukkit.FakePlayerFilter.isRealPlayer(event.getPlayer())) return;
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

        // 世界 chunk → layout chunk（减去 origin 偏移）；用该世界冻结的布局
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        int lx = cx - gw.originChunkX();
        int lz = cz - gw.originChunkZ();
        Classification c = layout.classify(lx, lz);

        // 地皮额外算"已解锁/预留"状态并并入去重 key：让玩家在【自己地皮内】跨越解锁边界也能收到提示。
        Manor manor = c.isPlot() ? cache.manorAt(gw, c.slot()) : null; // 2 秒 TTL 缓存，不查库
        boolean plotActive = manor != null
                && layout.activeRegion(c.slot(), manor.level()).containsChunk(lx, lz);

        String key = c.type() + ":" + c.slot() + (c.isPlot() ? ":" + plotActive : "");
        if (key.equals(lastKey.get(id))) {
            return; // 区域+解锁状态都没变，不重复弹 title
        }
        lastKey.put(id, key);

        switch (c.type()) {
            case MAIN_CITY -> player.sendTitle("§6§l主城",
                    "§e公会中心 · 公会 Lv" + gw.guildLevel(), 5, 30, 10);
            case ROAD -> player.sendTitle("§7§l道路", "§8公共土径", 5, 20, 10);
            case PLOT -> showPlotTitle(player, gw, manor, plotActive, c.slot());
        }
    }

    private void showPlotTitle(Player player, GuildWorld gw, Manor manor, boolean active, int slot) {
        if (manor != null) {
            if (!active) {
                // 走进了某块地皮的预留区——明确提示需升级庄园解锁（自己的地皮尤其有用）。
                boolean own = manor.owner().uuid().equals(player.getUniqueId());
                player.sendTitle("§e§l⚠ 预留区",
                        own ? "§7尚未解锁 · §a/gs upgrade §7升级庄园解锁此处"
                                : "§7地皮 #" + slot + " 的预留区 · 主人升级后启用",
                        5, 40, 10);
                return;
            }
            player.sendTitle("§a§l地皮 #" + slot,
                    "§7主人:§f " + ownerName(manor.owner().uuid())
                            + " §7· 庄园 Lv" + manor.level(),
                    5, 40, 10);
            return;
        }
        int capacity = levels.maxMembers(gw.guildLevel());
        if (slot < capacity) {
            player.sendTitle("§e§l空闲地皮 #" + slot, "§7未认领 · 可分配", 5, 40, 10);
        } else {
            player.sendTitle("§8§l预留地块 #" + slot, "§8超出当前容量 · 需升级公会", 5, 40, 10);
        }
    }

    private String ownerName(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String name = op.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }
}
