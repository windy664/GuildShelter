package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitRunnable;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;

import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * 庄园掉落物限制定时任务：扫描所有公会营地，清理超限庄园的多余掉落物。
 * clean 模式：超限时先清理最旧的掉落物降到阈值以下。
 * block 模式：只拦截新掉落物（在 ManorEntityListener 里做），不主动清理。
 */
public final class ManorLimitTask extends BukkitRunnable {

    private final GuildWorldRegistry registry;
    private final GuildRepository guilds;
    private final ManorRepository manors;
    private final ManorEntityCensus census;
    private final boolean cleanMode;
    private final Logger logger;

    public ManorLimitTask(GuildWorldRegistry registry, GuildRepository guilds, ManorRepository manors,
                          ManorEntityCensus census, boolean cleanMode, Logger logger) {
        this.registry = registry;
        this.guilds = guilds;
        this.manors = manors;
        this.census = census;
        this.cleanMode = cleanMode;
        this.logger = logger;
    }

    @Override
    public void run() {
        int totalCleaned = 0;
        for (GuildWorld gw : guilds.findAll()) {
            World world = org.bukkit.Bukkit.getWorld(gw.worldName());
            if (world == null) continue;
            List<Manor> allManors = manors.findAll(gw.guild());
            for (Manor manor : allManors) {
                totalCleaned += cleanManorDrops(world, gw, manor);
            }
            totalCleaned += cleanCityDrops(world, gw); // 主城固定限额（独立于庄园等级）
        }
        if (totalCleaned > 0) {
            logger.info("[GuildShelter] 清理超限掉落物: " + totalCleaned + " 个");
        }
    }

    /** 清理单块庄园的超限掉落物。返回清理数量。 */
    private int cleanManorDrops(World world, GuildWorld gw, Manor manor) {
        // 掉落物上限随该庄园等级 + 管理员增量；-1 = 不限，跳过。
        int cap = census.dropCap(manor);
        if (cap < 0) return 0;
        org.windy.guildshelter.domain.layout.LayoutCalculator layout =
                new org.windy.guildshelter.domain.layout.LayoutCalculator(gw.layout());
        org.windy.guildshelter.domain.model.ChunkRegion region = layout
                .activeRegion(manor.slot(), manor.level())
                .shift(gw.originChunkX(), gw.originChunkZ());

        // 统计掉落物
        List<Item> items = new java.util.ArrayList<>();
        for (int cx = region.minChunkX(); cx <= region.maxChunkX(); cx++) {
            for (int cz = region.minChunkZ(); cz <= region.maxChunkZ(); cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                for (Entity e : world.getChunkAt(cx, cz).getEntities()) {
                    if (e instanceof Item item) items.add(item);
                }
            }
        }

        if (items.size() <= cap) return 0;
        if (!cleanMode) return 0; // block 模式不主动清理

        // 清理最旧的
        int toRemove = items.size() - cap;
        items.sort(Comparator.comparingInt(Entity::getTicksLived).reversed());
        int removed = 0;
        for (Item item : items) {
            if (removed >= toRemove) break;
            item.remove();
            removed++;
        }
        return removed;
    }

    /** 清理主城超限掉落物（固定 config 上限，范围=整个主城 footprint）。返回清理数量。 */
    private int cleanCityDrops(World world, GuildWorld gw) {
        int cap = census.cityDropCap();
        if (cap < 0) return 0; // 未启用 / 不限
        org.windy.guildshelter.domain.layout.LayoutCalculator layout =
                new org.windy.guildshelter.domain.layout.LayoutCalculator(gw.layout());
        org.windy.guildshelter.domain.model.ChunkRegion region = layout
                .mainCityRegion().shift(gw.originChunkX(), gw.originChunkZ());

        List<Item> items = new java.util.ArrayList<>();
        for (int cx = region.minChunkX(); cx <= region.maxChunkX(); cx++) {
            for (int cz = region.minChunkZ(); cz <= region.maxChunkZ(); cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                for (Entity e : world.getChunkAt(cx, cz).getEntities()) {
                    if (e instanceof Item item) items.add(item);
                }
            }
        }

        if (items.size() <= cap) return 0;
        if (!cleanMode) return 0; // block 模式不主动清理

        int toRemove = items.size() - cap;
        items.sort(Comparator.comparingInt(Entity::getTicksLived).reversed());
        int removed = 0;
        for (Item item : items) {
            if (removed >= toRemove) break;
            item.remove();
            removed++;
        }
        return removed;
    }
}
