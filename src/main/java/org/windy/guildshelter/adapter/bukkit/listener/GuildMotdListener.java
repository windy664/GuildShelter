package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.windy.guildshelter.adapter.bukkit.GuildWorldRegistry;
import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.ManorRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公会 MOTD（Message of the Day）：玩家进入公会世界时显示公会公告。
 * 公告内容取庄主地皮的 {@code description} flag（同一公会共享一条 MOTD）。
 * 每个玩家每个世界每 5 分钟只显示一次（防刷屏）。
 */
public final class GuildMotdListener implements Listener {

    private final GuildWorldRegistry registry;
    private final ManorRepository manors;
    /** 玩家→上次看到 MOTD 的时间戳（毫秒），防刷屏。 */
    private final Map<UUID, Long> lastMotd = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 5 * 60 * 1000; // 5 分钟

    public GuildMotdListener(GuildWorldRegistry registry, ManorRepository manors) {
        this.registry = registry;
        this.manors = manors;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        GuildWorld gw = registry.get(player.getWorld().getName());
        if (gw == null) return; // 不是公会世界

        // 冷却检查
        long now = System.currentTimeMillis();
        Long last = lastMotd.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) return;

        // 找庄主（slot 0）的 description 作为 MOTD
        manors.findBySlot(gw.guild(), 0).ifPresent(ownerManor -> {
            String motd = Flag.DESCRIPTION.resolveString(ownerManor.flags());
            if (!motd.isBlank()) {
                player.sendMessage("§6[§e" + gw.guild().value() + "§6] §7" + motd);
                lastMotd.put(player.getUniqueId(), now);
            }
        });
    }
}
