package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.World;
import org.windy.guildshelter.domain.layout.Classification;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.layout.RegionType;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.port.ManorRepository;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * 坐标 → 该处所属成员庄园(若有)。复用世界注册表 + 该世界冻结布局做归类。
 * flag 执行监听器用它判断"这块方块/这个位置属于哪块地皮、其 flag 如何"。
 */
public final class ManorLookup {

    private final GuildWorldRegistry registry;
    private final ManorRepository manors;

    public ManorLookup(GuildWorldRegistry registry, ManorRepository manors) {
        this.registry = registry;
        this.manors = manors;
    }

    public boolean isGuildWorld(World world) {
        return registry.isGuildWorld(world.getName());
    }

    /** 返回 (blockX,blockZ) 处的成员庄园；不在公会世界/不在地皮/未分配则 empty。合并路 chunk 归主地皮。 */
    public Optional<Manor> at(World world, int blockX, int blockZ) {
        GuildWorld gw = registry.get(world.getName());
        if (gw == null) {
            return Optional.empty();
        }
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        int lx = (blockX >> 4) - gw.originChunkX();
        int lz = (blockZ >> 4) - gw.originChunkZ();
        OptionalInt slot = layout.slotAt(lx, lz);
        if (slot.isPresent()) {
            return manors.findBySlot(gw.guild(), slot.getAsInt());
        }
        // 原始 classify 不是 PLOT → 检查是否为合并路 chunk
        Classification raw = layout.classify(lx, lz);
        if (raw.type() == RegionType.ROAD) {
            MergeAwareClassifier merger = new MergeAwareClassifier(layout, manors, gw.guild());
            Classification merged = merger.classify(lx, lz);
            if (merged.isPlot()) {
                return manors.findBySlot(gw.guild(), merged.slot());
            }
        }
        return Optional.empty();
    }
}
