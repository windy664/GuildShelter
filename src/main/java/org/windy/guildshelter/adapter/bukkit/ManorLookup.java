package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.World;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
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

    /** 返回 (blockX,blockZ) 处的成员庄园；不在公会世界/不在地皮/未分配则 empty。 */
    public Optional<Manor> at(World world, int blockX, int blockZ) {
        GuildWorld gw = registry.get(world.getName());
        if (gw == null) {
            return Optional.empty();
        }
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        int lx = (blockX >> 4) - gw.originChunkX();
        int lz = (blockZ >> 4) - gw.originChunkZ();
        OptionalInt slot = layout.slotAt(lx, lz);
        if (slot.isEmpty()) {
            return Optional.empty();
        }
        return manors.findBySlot(gw.guild(), slot.getAsInt());
    }
}
