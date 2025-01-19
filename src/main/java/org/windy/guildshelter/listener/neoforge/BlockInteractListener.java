package org.windy.guildshelter.listener.neoforge;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.windy.guildshelter.util.PermissionCheck;

public class BlockInteractListener {

    private static final Logger LOGGER = LogManager.getLogger();


    // 左键点击方块事件
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.LeftClickBlock event) {
        LOGGER.info("PlayerInteractEvent 触发");

        Player player = event.getEntity();  // 获取玩家对象
        int playerX = player.getBlockX();  // 获取玩家的 x 坐标
        int playerZ = player.getBlockZ();  // 获取玩家的 z 坐标

        // 假设 plot 的半径是 50
        int radius = 50;

        // 使用 PermissionCheck 判断玩家是否有权限
        boolean hasPermission = PermissionCheck.checkPlayerInPlotArea(player, playerX, playerZ, 50);

        if (!hasPermission) {
            // 如果没有权限，取消事件
          //  event.setCanceled(true);
            LOGGER.info(player.getName().getString() + " 尝试在没有权限的区域进行操作，事件已取消。");
        }
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        LOGGER.info("PlayerInteractEvent 触发");
    }
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        LOGGER.info("PlayerInteractEvent 触发");
    }
}
