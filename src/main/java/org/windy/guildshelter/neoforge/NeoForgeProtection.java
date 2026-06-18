package org.windy.guildshelter.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.bukkit.Bukkit;
import org.windy.guildshelter.GuildShelterPlugin;
import org.windy.guildshelter.adapter.bukkit.ClaimGuard;
import org.windy.guildshelter.adapter.bukkit.InteractionPolicy;
import org.windy.guildshelter.domain.flag.InteractCategory;

import java.util.UUID;

/**
 * 领地保护的 <b>NeoForge 后端</b>（混合端启用）：监听 NeoForge 游戏事件，复用与 Bukkit 端
 * 完全相同的判定。相比 Bukkit 监听器，这里能覆盖到<b>模组方块/容器/实体</b>——这正是
 * "NeoForge 增强版全盘走 NeoForge"的意义。
 *
 * <p>两层：①破坏/放置 = 硬保护，访客一律挡（{@link ClaimGuard}）；②右键方块/实体交互 =
 * 按类放宽（{@link InteractionPolicy}：use/container/item-frame/vehicle-use）。容器靠
 * {@code BlockState.getMenuProvider} 识别（覆盖模组容器），实体靠 ItemFrame/ArmorStand/Boat/Minecart 归类。
 *
 * <p>桥接：从 NeoForge 实体取 UUID → {@link Bukkit#getPlayer(UUID)} 拿 Bukkit 玩家，不碰
 * CraftBukkit 内部类；坐标取自方块/实体的 BlockPos。判定服务在事件时惰性获取（mod 比插件先加载，
 * 装配未完成时放行）。
 *
 * <p><b>注意</b>：NeoForge 26 的事件类/方法名需以本地编译为准（本项目 NeoForge API 历来需编译验证）。
 * 若类名或 getter 不符，按编译报错调整 import 与方法名即可，逻辑不变。
 */
public final class NeoForgeProtection {

    // ===== 硬保护：破坏/放置（访客一律挡）=====

    @SubscribeEvent
    public void onBreak(BreakBlockEvent event) {
        Entity entity = event.getPlayer();
        BlockPos pos = event.getPos();
        org.bukkit.entity.Player bukkit = bukkitPlayer(entity, pos);
        if (bukkit == null) {
            return;
        }
        ClaimGuard guard = GuildShelterPlugin.protectionGuard();
        if (guard == null) {
            return;
        }

        // 只有在没权限（被拒绝）的时候才拦截并回发更新
        if (!guard.allowed(bukkit, pos.getX(), pos.getZ())) {
            event.setCanceled(true);
            guard.notifyDenied(bukkit);
            resyncBlock(entity, pos); // 拦截时的同步逻辑保留
        }

    }



    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        guard(event.getEntity(), event.getPos(), () -> event.setCanceled(true));
    }

    // ===== 按类放宽：右键方块（容器/use）=====

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        // 有菜单(MenuProvider)的方块 = 容器(覆盖模组容器)；否则按 use 处理。
        InteractCategory cat = state.getMenuProvider(level, pos) != null
                ? InteractCategory.CONTAINER : InteractCategory.USE;
        interact(event.getEntity(), pos, cat, () -> event.setCanceled(true));
    }

    // ===== 按类放宽：实体交互（展示框/盔甲架/载具）=====

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        categoryOf(event.getTarget()).ifPresent(cat ->
                interact(event.getEntity(), event.getTarget().blockPosition(), cat,
                        () -> event.setCanceled(true)));
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        categoryOf(event.getTarget()).ifPresent(cat ->
                interact(event.getEntity(), event.getTarget().blockPosition(), cat,
                        () -> event.setCanceled(true)));
    }

    /** 实体 → 交互类；非受管实体返回 empty（不干预）。 */
    private static java.util.Optional<InteractCategory> categoryOf(Entity entity) {
        if (entity instanceof ItemFrame || entity instanceof ArmorStand) {
            return java.util.Optional.of(InteractCategory.ITEM_FRAME);
        }
        if (entity instanceof VehicleEntity) { // 船(AbstractBoat)/矿车(AbstractMinecart) 共同超类
            return java.util.Optional.of(InteractCategory.VEHICLE);
        }
        return java.util.Optional.empty();
    }

    // ===== 公共桥接 =====

    /** 硬保护：解析共享 {@link ClaimGuard} 与 Bukkit 玩家后套用破坏/放置权限。 */
    private void guard(Entity entity, BlockPos pos, Runnable cancel) {
        org.bukkit.entity.Player bukkit = bukkitPlayer(entity, pos);
        if (bukkit == null) {
            return;
        }
        ClaimGuard guard = GuildShelterPlugin.protectionGuard();
        if (guard == null) {
            return; // 装配未完成（mod 早于插件加载），放行
        }
        if (!guard.allowed(bukkit, pos.getX(), pos.getZ())) {
            cancel.run();
            guard.notifyDenied(bukkit);
            // Youer 混合端上取消 BreakBlockEvent/EntityPlaceEvent 不会自动把权威方块状态回发给客户端，
            // 客户端按预测显示"幽灵方块"，需一次交互(右键)才触发区块同步。这里主动回发，立刻纠正。
            resyncBlock(entity, pos);
        }
    }

    /** 把目标(及其上方)方块的真实状态回发给该玩家，纠正破坏/放置取消后的客户端预测偏差。 */
    private static void resyncBlock(Entity entity, BlockPos pos) {
        if (!(entity instanceof ServerPlayer sp)) {
            return;
        }
        Level level = sp.level();
        sp.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        // 破坏常牵连上方方块(高草/雪层/门上半/作物等)，一并回发避免连带幽灵。
        sp.connection.send(new ClientboundBlockUpdatePacket(level, pos.above()));
    }

    /** 按类放宽：解析共享 {@link InteractionPolicy} 与 Bukkit 玩家后按 category 判定。 */
    private void interact(Entity entity, BlockPos pos, InteractCategory cat, Runnable cancel) {
        org.bukkit.entity.Player bukkit = bukkitPlayer(entity, pos);
        if (bukkit == null) {
            return;
        }
        InteractionPolicy policy = GuildShelterPlugin.interactionPolicy();
        if (policy == null) {
            return; // 装配未完成，放行
        }
        if (!policy.allowed(bukkit, pos.getX(), pos.getZ(), cat)) {
            cancel.run();
            policy.notifyDenied(bukkit);
        }
    }

    /** 非玩家/无坐标 → null；真实在线玩家 → 对应 Bukkit Player（不碰 CraftBukkit 内部类）。 */
    private static org.bukkit.entity.Player bukkitPlayer(Entity entity, BlockPos pos) {
        if (!(entity instanceof net.minecraft.server.level.ServerPlayer) || pos == null) {
            return null;
        }
        UUID uuid = entity.getUUID();
        return Bukkit.getPlayer(uuid); // 假玩家/未映射 → null（不拦）
    }
}
