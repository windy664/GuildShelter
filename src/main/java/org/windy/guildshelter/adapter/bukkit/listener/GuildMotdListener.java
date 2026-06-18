package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.windy.guildshelter.adapter.bukkit.GuildWorldRegistry;
import org.windy.guildshelter.adapter.bukkit.Messages;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.port.GuildRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公会 MOTD（Message of the Day）：玩家进入公会世界时显示公会公告。
 * 公告内容取 GuildWorld.bulletin 字段。
 * 每个玩家每个世界每 5 分钟只显示一次（防刷屏）。
 */
public final class GuildMotdListener implements Listener {

    private final GuildWorldRegistry registry;
    private final GuildRepository guilds;
    /** 玩家→上次看到 MOTD 的时间戳（毫秒），防刷屏。 */
    private final Map<UUID, Long> lastMotd = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 5 * 60 * 1000; // 5 分钟

    public GuildMotdListener(GuildWorldRegistry registry, GuildRepository guilds) {
        this.registry = registry;
        this.guilds = guilds;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!org.windy.guildshelter.adapter.bukkit.FakePlayerFilter.isRealPlayer(player)) return;
        GuildWorld gw = registry.get(player.getWorld().getName());
        if (gw == null) return; // 不是公会世界

        // 冷却检查
        long now = System.currentTimeMillis();
        Long last = lastMotd.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) return;

        // 从 DB 读最新公告（registry 缓存可能过期）
        GuildWorld latest = guilds.find(gw.guild()).orElse(gw);
        String bulletin = latest.bulletin();
        if (bulletin != null && !bulletin.isBlank()) {
            player.sendMessage(Messages.get("info.bulletin_show", gw.guild().value(), bulletin));
            lastMotd.put(player.getUniqueId(), now);
        }
    }
}
