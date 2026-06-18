package org.windy.guildshelter.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.windy.guildshelter.GuildShelterPlugin;
import org.windy.guildshelter.adapter.bukkit.ManorEntityCensus;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.flag.ManorEntityClass;
import org.windy.guildshelter.domain.model.Manor;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 地皮 flag 的 <b>NeoForge 后端</b>（A 氛围类，混合端覆盖模组内容）：
 * pvp / mob-spawn / explosion / mob-griefing + 环境类（redstone/liquid-flow/crop-grow）。
 * fire-spread 在 NeoForge 26 无独立事件，故仅 Bukkit 侧处理（vanilla 已够）。访问/增益类(B/C)是玩家行为，
 * 由 Bukkit 侧 {@code ManorAccessListener}/{@code ManorBuffTask} 统一处理，本类不重复。
 *
 * <p>桥接：Level→Bukkit 世界用 <b>反射</b> 调 {@code ServerLevel.getWorld()}（CraftBukkit/Youer 运行时注入，
 * moddev 编译期 mojmap 无此方法）。其余均 mojmap 原生。判定服务 {@link ManorLookup} 从插件惰性取。
 *
 * <p><b>NeoForge 26 环境事件说明</b>：红石/液体/作物等 vanilla 方块机制在 NeoForge 26 中
 * 可能没有细粒度事件（注释见 {@code ManorEnvListener}）。以下环境事件 handler 使用
 * NeoForge 的 {@code BlockEvent} 子类——编译时需验证类名是否存在于目标版本。
 * 若不存在则删除对应 handler，vanilla 机制由 Bukkit 侧 {@code ManorEnvListener} 覆盖。
 */
public final class NeoForgeFlags {

    private static volatile Method getWorldMethod;

    // ---- pvp / pve / invincible ----
    @SubscribeEvent
    public void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        BlockPos pos = victim.blockPosition();
        boolean victimIsPlayer = victim instanceof Player;

        if (victimIsPlayer && flagOn(victim.level(), pos, Flag.INVINCIBLE)) {
            event.setCanceled(true);
            return;
        }
        // 解析攻击者：直接实体 或 弹射物→射手
        Entity rawAttacker = event.getSource().getEntity();
        Entity attacker = resolveShooter(rawAttacker);
        if (victimIsPlayer) {
            if (attacker instanceof Player && attacker != victim) {
                if (denied(victim.level(), pos, Flag.PVP)) {
                    event.setCanceled(true);
                }
            } else if (attacker instanceof LivingEntity && !(attacker instanceof Player)) {
                // 怪打玩家：pve 总开关 + pve-monster 细分
                if (denied(victim.level(), pos, Flag.PVE) || denied(victim.level(), pos, Flag.PVE_MONSTER)) {
                    event.setCanceled(true);
                }
            }
        } else if (attacker instanceof Player) {
            // 玩家打怪：pve 总开关 + pve-player 细分
            if (denied(victim.level(), pos, Flag.PVE) || denied(victim.level(), pos, Flag.PVE_PLAYER)) {
                event.setCanceled(true);
            }
        }
    }

    /** 解析弹射物到射手：箭/三叉戟/雪球等 → getOwner()。非弹射物返回原实体。 */
    private static Entity resolveShooter(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.projectile.Projectile proj) {
            Entity owner = proj.getOwner();
            return owner != null ? owner : entity;
        }
        return entity;
    }

    // ---- mob-spawn + 实体上限 caps ----
    @SubscribeEvent
    public void onSpawn(FinalizeSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        if (denied(entity.level(), entity.blockPosition(), Flag.MOB_SPAWN)) {
            event.setSpawnCancelled(true);
            return;
        }
        // caps：混合端的模组生物只在 NeoForge 侧能拦（Bukkit 的 CreatureSpawnEvent 未必对模组怪触发）。
        ManorEntityCensus census = GuildShelterPlugin.entityCensus();
        ManorLookup lookup = GuildShelterPlugin.manorLookup();
        if (census == null || lookup == null) {
            return; // 装配未完成 / 未启用
        }
        org.bukkit.World world = bukkitWorld(entity.level());
        if (world == null) {
            return;
        }
        ManorEntityClass cls = classifyMob(entity);
        BlockPos pos = entity.blockPosition();
        lookup.at(world, pos.getX(), pos.getZ()).ifPresent(manor -> {
            if (census.exceedsCap(world, manor, cls)) {
                event.setSpawnCancelled(true);
            }
        });
    }

    /** NeoForge 生物 → cap 分类，按 {@link MobCategory}（MONSTER→敌对，CREATURE→动物，余→其它生物）。 */
    private static ManorEntityClass classifyMob(LivingEntity entity) {
        MobCategory cat = entity.getType().getCategory();
        return switch (cat) {
            case MONSTER -> ManorEntityClass.HOSTILE;
            case CREATURE -> ManorEntityClass.ANIMAL;
            default -> ManorEntityClass.OTHER_MOB;
        };
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

    // ===== 环境类 flag（编译验证：以下事件类名需以 NeoForge 26 实际版本为准）=====
    // NeoForge 26 可能没有细粒度的 vanilla 方块事件（redstone/liquid/crop）。
    // 若编译报错"找不到符号"，删除对应 handler 即可——vanilla 机制由 Bukkit 侧 ManorEnvListener 覆盖。
    // 这些 handler 的价值在于：模组方块（如 Mekanism 管道、Thermal 红石机器）触发的 NeoForge 事件
    // 能被拦截，而 Bukkit 侧对模组内容不可见。

    /**
     * 红石信号传播：{@code NeighborNotifyEvent} 在方块更新邻居时触发（含模组红石机器）。
     * ⚠️ 编译验证：NeoForge 26 中此类可能不存在或签名不同，按编译报错调整。
     */
    @SubscribeEvent
    public void onNeighborNotify(net.neoforged.neoforge.event.level.BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof Level level && denied(level, event.getPos(), Flag.REDSTONE)) {
            event.setCanceled(true);
        }
    }

    /**
     * 液体流动：{@code FluidPlaceBlockEvent} 在液体放置方块时触发（含模组管道）。
     * ⚠️ 编译验证：NeoForge 26 中此类可能不存在或签名不同，按编译报错调整。
     */
    @SubscribeEvent
    public void onFluidPlace(net.neoforged.neoforge.event.level.BlockEvent.FluidPlaceBlockEvent event) {
        BlockPos target = event.getPos();
        if (event.getLevel() instanceof Level level && denied(level, target, Flag.LIQUID_FLOW)) {
            event.setCanceled(true);
        }
    }

    /** 该位置地皮该 flag 是否为 false(禁止)。无地皮/无法解析则不拦。 */
    private static boolean denied(Level level, BlockPos pos, Flag flag) {
        return manorAt(level, pos.getX(), pos.getZ())
                .map(m -> !flag.resolveBool(m.flags())).orElse(false);
    }

    /** 该位置地皮该 flag 是否为 true(开)。用于 invincible 这种"开=拦"的反向语义。 */
    private static boolean flagOn(Level level, BlockPos pos, Flag flag) {
        return manorAt(level, pos.getX(), pos.getZ())
                .map(m -> flag.resolveBool(m.flags())).orElse(false);
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
