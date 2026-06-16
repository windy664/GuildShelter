package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.windy.guildshelter.adapter.bukkit.GuildWorldRegistry;
import org.windy.guildshelter.domain.layout.Classification;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.port.ManorRepository;
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
    private final ManorRepository manors;
    private final LevelRules levels;

    /** 每个玩家上一次所在 chunk 与归类签名，用于去重。 */
    private final Map<UUID, long[]> lastChunk = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastKey = new ConcurrentHashMap<>();

    public RegionTitleListener(GuildWorldRegistry registry, ManorRepository manors, LevelRules levels) {
        this.registry = registry;
        this.manors = manors;
        this.levels = levels;
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

        // 世界 chunk → layout chunk（减去 origin 偏移）；用该世界冻结的布局
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        int lx = cx - gw.originChunkX();
        int lz = cz - gw.originChunkZ();
        Classification c = layout.classify(lx, lz);

        String key = c.type() + ":" + c.slot();
        if (key.equals(lastKey.get(id))) {
            return; // 归类没变，不重复弹 title
        }
        lastKey.put(id, key);

        switch (c.type()) {
            case MAIN_CITY -> player.sendTitle("§6§l主城",
                    "§e公会中心 · 公会 Lv" + gw.guildLevel(), 5, 30, 10);
            case ROAD -> player.sendTitle("§7§l道路", "§8公共土径", 5, 20, 10);
            case PLOT -> showPlotTitle(player, gw, layout, lx, lz, c.slot());
        }
    }

    private void showPlotTitle(Player player, GuildWorld gw, LayoutCalculator layout,
                               int lx, int lz, int slot) {
        Manor manor = manors.findBySlot(gw.guild(), slot).orElse(null);
        if (manor != null) {
            boolean active = layout.activeRegion(slot, manor.level()).containsChunk(lx, lz);
            String tail = active ? "" : " §8[预留区·升级解锁]";
            player.sendTitle("§a§l地皮 #" + slot,
                    "§7主人:§f " + ownerName(manor.owner().uuid())
                            + " §7· 庄园 Lv" + manor.level() + tail,
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
