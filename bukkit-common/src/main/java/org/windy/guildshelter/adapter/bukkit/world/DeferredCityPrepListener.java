package org.windy.guildshelter.adapter.bukkit.world;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.windy.guildshelter.adapter.bukkit.GuildWorldRegistry;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.port.DeferredPrep;
import org.windy.guildshelter.service.GuildService;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 惰性生成世界（Iris）的<b>延迟主城整地</b>：实现 {@link DeferredPrep} 收集"待补"世界，
 * 监听玩家<b>首次进入</b>该世界（{@link PlayerChangedWorldEvent} / 登录即在该世界 {@link PlayerJoinEvent}），
 * 此时主城区块本就为玩家加载 → 延后一拍补铺主城路 + 围墙（{@link GuildService#runDeferredCityPrep}），
 * 避免建会时强制同步生成 Iris 未生成区块。
 *
 * <p>整地幂等，故"待补"只存内存：重启后世界文件夹仍在但本会话未补，下次玩家进入即补一次（无害重复）。
 */
public final class DeferredCityPrepListener implements DeferredPrep, Listener {

    private final Plugin plugin;
    private final GuildService service;
    private final GuildWorldRegistry registry;
    /** 待补主城整地的世界名集合（线程安全）。 */
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    public DeferredCityPrepListener(Plugin plugin, GuildService service, GuildWorldRegistry registry) {
        this.plugin = plugin;
        this.service = service;
        this.registry = registry;
    }

    @Override
    public void markGuildPending(GuildId guild, String worldName) {
        pending.add(worldName);
        plugin.getLogger().info("[GuildShelter] " + worldName + " 惰性世界(Iris)：主城铺路延迟到玩家首次进入。");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        tryPrep(e.getPlayer().getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        tryPrep(e.getPlayer().getWorld().getName());
    }

    /** 玩家所在世界若在待补集合里 → 延后一拍（让所在区块加载稳定）补铺主城路 + 围墙，然后移出集合。 */
    private void tryPrep(String worldName) {
        if (!pending.contains(worldName)) {
            return;
        }
        GuildWorld gw = registry.get(worldName);
        if (gw == null) {
            return; // registry 还没登记（理论上建会即登记），下次进入再试
        }
        // 先移出，避免同一世界多人同时进触发并发补铺；失败也不重试（幂等，下次有人进会再尝试）。
        if (!pending.remove(worldName)) {
            return;
        }
        // 延后 40 tick（~2s）：给玩家所在主城区块留出加载时间，补铺只整已加载/将加载的区块。
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                service.runDeferredCityPrep(gw);
                plugin.getLogger().info("[GuildShelter] " + worldName + " 玩家进入 → 已补铺主城路/围墙。");
            } catch (RuntimeException ex) {
                pending.add(worldName); // 出错放回，下次进入重试
                plugin.getLogger().warning("[GuildShelter] " + worldName + " 补铺主城路失败，下次进入重试: " + ex);
            }
        }, 40L);
    }
}
