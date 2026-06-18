package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.domain.flag.Flag;

/**
 * 方块环境组 flag 执行（<b>Bukkit 侧，两载体都注册</b>）：redstone / liquid-flow / 各类生长/结冰积雪/落叶。
 * NeoForge 26 对这些 vanilla 方块机制无细粒度事件（同 fire），故只 Bukkit；也因此不会与 NeoForge 重复。
 */
public final class ManorEnvListener implements Listener {

    private final ManorLookup lookup;

    public ManorEnvListener(ManorLookup lookup) {
        this.lookup = lookup;
    }

    /** 子领地优先 → 庄园 → 默认。无地皮不拦。 */
    private boolean denied(Location loc, Flag flag) {
        return !lookup.resolveFlag(loc.getWorld(), loc.getBlockX(), loc.getBlockZ(), flag);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onRedstone(BlockRedstoneEvent event) {
        if (denied(event.getBlock().getLocation(), Flag.REDSTONE)) {
            event.setNewCurrent(0); // 该事件不可取消，置 0 即抑制信号
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFromTo(BlockFromToEvent event) {
        if (!event.getBlock().isLiquid()) {
            return; // 只管液体流动
        }
        if (denied(event.getToBlock().getLocation(), Flag.LIQUID_FLOW)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (denied(event.getBlock().getLocation(), Flag.CROP_GROW)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        if (denied(event.getLocation(), Flag.CROP_GROW)) { // 树苗长树也算 crop-grow
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        Flag f = spreadFlag(event.getNewState().getType());
        if (f != null && denied(event.getBlock().getLocation(), f)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        Flag f = formFlag(event.getNewState().getType());
        if (f != null && denied(event.getBlock().getLocation(), f)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        Flag f = fadeFlag(event.getBlock().getType());
        if (f != null && denied(event.getBlock().getLocation(), f)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeafDecay(LeavesDecayEvent event) {
        if (denied(event.getBlock().getLocation(), Flag.LEAF_DECAY)) {
            event.setCancelled(true);
        }
    }

    private static Flag spreadFlag(Material m) {
        if (m == Material.GRASS_BLOCK) {
            return Flag.GRASS_GROW;
        }
        if (m == Material.MYCELIUM) {
            return Flag.MYCELIUM_GROW;
        }
        String n = m.name();
        if (n.contains("VINE") || n.contains("LICHEN")) {
            return Flag.VINE_GROW;
        }
        return null; // 蘑菇等其它 spread 不在本组
    }

    private static Flag formFlag(Material m) {
        if (m == Material.ICE || m == Material.FROSTED_ICE) {
            return Flag.ICE_FORM;
        }
        if (m == Material.SNOW || m == Material.SNOW_BLOCK || m == Material.POWDER_SNOW) {
            return Flag.SNOW_FORM;
        }
        return null;
    }

    private static Flag fadeFlag(Material m) {
        if (m == Material.ICE || m == Material.FROSTED_ICE) {
            return Flag.ICE_MELT;
        }
        if (m == Material.SNOW || m == Material.SNOW_BLOCK || m == Material.POWDER_SNOW) {
            return Flag.SNOW_MELT;
        }
        return null;
    }
}
