package org.windy.guildshelter.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.bukkit.Bukkit;
import org.windy.guildshelter.GuildShelterPlugin;
import org.windy.guildshelter.adapter.bukkit.ClaimGuard;

import java.util.UUID;

/**
 * 领地保护的 <b>NeoForge 后端</b>（混合端启用）：监听 NeoForge 游戏事件，复用与 Bukkit 端
 * 完全相同的 {@link ClaimGuard} 判定。相比 Bukkit 监听器，这里能覆盖到模组方块/交互——
 * 这正是"NeoForge 增强版全盘走 NeoForge"的意义。
 *
 * <p>桥接方式：从 NeoForge 实体取 UUID → {@link Bukkit#getPlayer(UUID)} 拿 Bukkit 玩家，
 * 不碰 CraftBukkit 内部类。判定服务在事件时从 {@link GuildShelterPlugin#protectionGuard()}
 * 惰性获取（mod 比插件先加载，装配未完成时放行）。
 *
 * <p><b>注意</b>：NeoForge 26 的事件类/方法名需以本地编译为准（本项目 NeoForge API 历来需编译验证）。
 * 若类名或 getter 不符，按编译报错调整 import 与方法名即可，逻辑不变。
 */
public final class NeoForgeProtection {

    @SubscribeEvent
    public void onBreak(BreakBlockEvent event) { // NeoForge 26: 破坏事件移到 event.level.block 子包
        handle(event.getPlayer(), event.getPos(), () -> event.setCanceled(true));
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        handle(event.getEntity(), event.getPos(), () -> event.setCanceled(true));
    }

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        handle(event.getEntity(), event.getPos(), () -> event.setCanceled(true));
    }

    /** 公共桥接：非玩家忽略；解析共享判定与 Bukkit 玩家后套用同一套权限规则。 */
    private void handle(Entity entity, BlockPos pos, Runnable cancel) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player) || pos == null) {
            return;
        }
        ClaimGuard guard = GuildShelterPlugin.protectionGuard();
        if (guard == null) {
            return; // 装配未完成（mod 早于插件加载），放行
        }
        UUID uuid = entity.getUUID();
        org.bukkit.entity.Player bukkit = Bukkit.getPlayer(uuid);
        if (bukkit == null) {
            return; // 非真实在线玩家（假玩家/未映射），不拦
        }
        if (!guard.allowed(bukkit, pos.getX(), pos.getZ())) {
            cancel.run();
            guard.notifyDenied(bukkit);
        }
    }
}
