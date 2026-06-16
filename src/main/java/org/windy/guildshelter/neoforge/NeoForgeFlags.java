package org.windy.guildshelter.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.windy.guildshelter.GuildShelterPlugin;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.model.Manor;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 地皮 flag 的 <b>NeoForge 后端</b>（A 氛围类，混合端覆盖模组内容）：pvp / mob-spawn / explosion / mob-griefing。
 * fire-spread 在 NeoForge 26 无独立事件，故仅 Bukkit 侧处理（vanilla 已够）。访问/增益类(B/C)是玩家行为，
 * 由 Bukkit 侧 {@code ManorAccessListener}/{@code ManorBuffTask} 统一处理，本类不重复。
 *
 * <p>桥接：Level→Bukkit 世界用 <b>反射</b> 调 {@code ServerLevel.getWorld()}（CraftBukkit/Youer 运行时注入，
 * moddev 编译期 mojmap 无此方法）。其余均 mojmap 原生。判定服务 {@link ManorLookup} 从插件惰性取。
 */
public final class NeoForgeFlags {

    private static volatile Method getWorldMethod;

    // ---- pvp ----
    @SubscribeEvent
    public void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return; // 受害者必须是玩家
        }
        Entity attacker = event.getSource().getCausingEntity(); // 解析弹射物到射手
        if (!(attacker instanceof Player) || attacker == event.getEntity()) {
            return;
        }
        if (denied(event.getEntity().level(), event.getEntity().blockPosition(), Flag.PVP)) {
            event.setCanceled(true);
        }
    }

    // ---- mob-spawn ----
    @SubscribeEvent
    public void onSpawn(FinalizeSpawnEvent event) {
        if (denied(event.getEntity().level(), event.getEntity().blockPosition(), Flag.MOB_SPAWN)) {
            event.setSpawnCancelled(true);
        }
    }

    // ---- explosion（按方块剔除受保护地皮内的方块）----
    @SubscribeEvent
    public void onExplode(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        event.getAffectedBlocks().removeIf(pos -> denied(level, pos, Flag.EXPLOSION));
    }

    // ---- mob-griefing ----
    @SubscribeEvent
    public void onGrief(EntityMobGriefingEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        if (denied(event.getEntity().level(), event.getEntity().blockPosition(), Flag.MOB_GRIEFING)) {
            event.setCanGrief(false);
        }
    }

    /** 该位置地皮该 flag 是否为 false(禁止)。无地皮/无法解析则不拦。 */
    private static boolean denied(Level level, BlockPos pos, Flag flag) {
        return manorAt(level, pos.getX(), pos.getZ())
                .map(m -> !flag.resolveBool(m.flags())).orElse(false);
    }

    private static Optional<Manor> manorAt(Level level, int x, int z) {
        ManorLookup lookup = GuildShelterPlugin.manorLookup();
        if (lookup == null) {
            return Optional.empty(); // 装配未完成 / flag 未启用
        }
        org.bukkit.World world = bukkitWorld(level);
        return world == null ? Optional.empty() : lookup.at(world, x, z);
    }

    /** 反射调 ServerLevel.getWorld()（Youer 运行时注入）。 */
    private static org.bukkit.World bukkitWorld(Level level) {
        try {
            Method m = getWorldMethod;
            if (m == null) {
                m = level.getClass().getMethod("getWorld");
                getWorldMethod = m;
            }
            Object w = m.invoke(level);
            return w instanceof org.bukkit.World bw ? bw : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
