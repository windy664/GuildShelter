package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.windy.guildshelter.adapter.bukkit.GuildWorldRegistry;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.port.GuildRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地皮边界粒子可视化：玩家手持木棍站在地皮上时，每 0.5 秒在地皮实占范围边缘显示一圈粒子。
 * 放下木棍或离开地皮后自动停止。进入新地皮时粒子颜色随机变化。
 */
public final class ManorParticleTask extends BukkitRunnable {

    private static final DustOptions DUST_GREEN = new DustOptions(Color.GREEN, 1.0f);
    private static final DustOptions DUST_YELLOW = new DustOptions(Color.YELLOW, 1.0f);
    private static final DustOptions DUST_AQUA = new DustOptions(Color.AQUA, 1.0f);
    private static final DustOptions[] COLORS = {DUST_GREEN, DUST_YELLOW, DUST_AQUA};

    private final ManorLookup lookup;
    private final GuildWorldRegistry registry;
    private final GuildRepository guilds;
    private final Map<UUID, Integer> lastSlot = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> colorIdx = new ConcurrentHashMap<>();

    public ManorParticleTask(ManorLookup lookup, GuildWorldRegistry registry, GuildRepository guilds) {
        this.lookup = lookup;
        this.registry = registry;
        this.guilds = guilds;
    }

    @Override
    public void run() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            try {
                showFor(player);
            } catch (Exception ignored) {
                // 防止单个玩家异常影响其他人
            }
        }
    }

    private void showFor(Player player) {
        // 只有手持木棍才显示边界
        if (player.getInventory().getItemInMainHand().getType() != Material.STICK) {
            lastSlot.remove(player.getUniqueId());
            return;
        }
        World world = player.getWorld();
        GuildWorld gw = registry.get(world.getName());
        if (gw == null) {
            lastSlot.remove(player.getUniqueId());
            return;
        }
        int bx = player.getLocation().getBlockX();
        int bz = player.getLocation().getBlockZ();
        Manor manor = lookup.at(world, bx, bz).orElse(null);
        if (manor == null) {
            lastSlot.remove(player.getUniqueId());
            return;
        }
        // 切换地皮时换颜色
        Integer prev = lastSlot.get(player.getUniqueId());
        if (prev == null || prev != manor.slot()) {
            lastSlot.put(player.getUniqueId(), manor.slot());
            colorIdx.put(player.getUniqueId(),
                    (colorIdx.getOrDefault(player.getUniqueId(), 0) + 1) % COLORS.length);
        }
        DustOptions color = COLORS[colorIdx.getOrDefault(player.getUniqueId(), 0)];

        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        ChunkRegion active = layout.activeRegion(manor.slot(), manor.level())
                .shift(gw.originChunkX(), gw.originChunkZ());
        int y = player.getLocation().getBlockY();

        // 四条边各放几个粒子
        int minX = active.minBlockX();
        int maxX = active.maxBlockX() + 15;
        int minZ = active.minBlockZ();
        int maxZ = active.maxBlockZ() + 15;
        int step = 4; // 每 4 格一个粒子

        for (int x = minX; x <= maxX; x += step) {
            spawnDust(world, x + 0.5, y, minZ + 0.5, color);
            spawnDust(world, x + 0.5, y, maxZ + 0.5, color);
        }
        for (int z = minZ; z <= maxZ; z += step) {
            spawnDust(world, minX + 0.5, y, z + 0.5, color);
            spawnDust(world, maxX + 0.5, y, z + 0.5, color);
        }
    }

    private void spawnDust(World world, double x, double y, double z, DustOptions color) {
        world.spawnParticle(Particle.DUST, x, y + 1, z, 1, 0, 0, 0, 0, color);
    }
}
