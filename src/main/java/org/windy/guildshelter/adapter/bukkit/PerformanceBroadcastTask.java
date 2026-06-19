package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * 性能统计排行广播：定时计算每块庄园的 tick 开销，全服广播 Top N。
 * tick 开销 = tileEntities × w1 + entities × w2 + droppedItems × w3 + chunks × w4
 */
public final class PerformanceBroadcastTask extends BukkitRunnable {

    private final GuildWorldRegistry registry;
    private final GuildRepository guilds;
    private final ManorRepository manors;
    private final ManorEntityCensus census;
    private final int topCount;
    private final double wTile, wEntity, wDrop, wChunk;
    private final Logger logger;

    public PerformanceBroadcastTask(GuildWorldRegistry registry, GuildRepository guilds,
                                     ManorRepository manors, ManorEntityCensus census,
                                     int topCount, double wTile, double wEntity, double wDrop, double wChunk,
                                     Logger logger) {
        this.registry = registry;
        this.guilds = guilds;
        this.manors = manors;
        this.census = census;
        this.topCount = topCount;
        this.wTile = wTile;
        this.wEntity = wEntity;
        this.wDrop = wDrop;
        this.wChunk = wChunk;
        this.logger = logger;
    }

    @Override
    public void run() {
        List<ManorCost> allCosts = new ArrayList<>();

        for (GuildWorld gw : guilds.findAll()) {
            World world = Bukkit.getWorld(gw.worldName());
            if (world == null) continue;
            LayoutCalculator layout = new LayoutCalculator(gw.layout());
            for (Manor manor : manors.findAll(gw.guild())) {
                ManorEntityCensus.Census c = census.countAt(world, manor);
                ChunkRegion region = layout.activeRegion(manor.slot(), manor.level())
                        .shift(gw.originChunkX(), gw.originChunkZ());
                int chunks = region.widthChunks() * region.depthChunks();
                double cost = c.tileEntities() * wTile + c.livingTotal() * wEntity
                        + c.droppedItems() * wDrop + chunks * wChunk;
                if (cost > 0) {
                    allCosts.add(new ManorCost(gw.guild().value(), manor.slot(), cost, c));
                }
            }
        }

        if (allCosts.isEmpty()) return;

        allCosts.sort(Comparator.comparingDouble(ManorCost::cost).reversed());
        int show = Math.min(topCount, allCosts.size());

        // 全服广播
        Bukkit.broadcastMessage("§6==== §e庄园性能排行 §6(开销 Top " + show + ") §6====");
        for (int i = 0; i < show; i++) {
            ManorCost mc = allCosts.get(i);
            String label = mc.guild + " #" + mc.slot;
            Bukkit.broadcastMessage(String.format("§e%s. §f%s §7(实体:%d 方块实体:%d 掉落物:%d) §c%.3f ms/t",
                    i + 1, label, mc.census.livingTotal(), mc.census.tileEntities(),
                    mc.census.droppedItems(), mc.cost));
        }

        logger.info("[GuildShelter] 性能排行已广播（" + allCosts.size() + " 块庄园）");
    }

    /** 计算单块庄园的 tick 开销（供外部查询用）。 */
    public double calculateCost(ManorEntityCensus.Census c, int chunks) {
        return c.tileEntities() * wTile + c.livingTotal() * wEntity
                + c.droppedItems() * wDrop + chunks * wChunk;
    }

    private record ManorCost(String guild, int slot, double cost, ManorEntityCensus.Census census) {}
}
