package org.windy.guildshelter.listener.neoforge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.windy.guildshelter.util.PermissionCheck;

public class BlockInteractListener {

    private static final Logger LOGGER = LogManager.getLogger();

    // 提取通用的权限检查逻辑
    private boolean checkPermission(String player, String worldName, double playerX, double playerZ) {
        // 调用权限检查方法，传递玩家名称、世界名称、玩家坐标
        boolean hasPermission = PermissionCheck.hasPermission(player, worldName, (int) playerX, (int) playerZ);

        // 检查是否为假玩家
        if (isFakePlayer(player)) {
            LOGGER.info("Fake player " + player + " triggered PlayerInteractEvent in world: " + worldName);
            return false;  // 这是假的玩家，返回false，不执行后续逻辑
        }

        return hasPermission;
    }

    // 判断是否是假的玩家
    private boolean isFakePlayer(String playerName) {
        return playerName.contains("AS-FAKEPLAYER") ||
                playerName.contains("[MINECRAFT]") ||
                playerName.contains("[MEKANISM]") ||
                playerName.contains("[IF]") ||
                playerName.contains("[IntegratedTunnels]") ||
                playerName.contains("KILLER JOE") ||
                playerName.contains("[DEPOLYER]") ||
                playerName.contains("[XU2FAKEPLAYER]") ||
                playerName.contains("[MODULAR ROUTERS]");
    }

    // 左键点击方块事件
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.LeftClickBlock event) {
        String player = event.getEntity().getName().getString();
        String world = event.getLevel().dimension().location().getNamespace();
        String worldName = world.equals("minecraft") ? event.getLevel().dimension().location().getPath() : world;

        boolean hasPermission = checkPermission(player, worldName, event.getEntity().getX(), event.getEntity().getZ());

        if (hasPermission) {
            // 执行允许的逻辑
            LOGGER.info("Player " + player + " has permission to interact with the block.");
        } else {
            // 如果权限不足，取消事件
            event.setCanceled(true);
            LOGGER.info("Player " + player + " does not have permission to interact with the block.");
        }
    }

    // 右键点击方块事件
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        String player = event.getEntity().getName().getString();
        String world = event.getLevel().dimension().location().getNamespace();
        String worldName = world.equals("minecraft") ? event.getLevel().dimension().location().getPath() : world;

        boolean hasPermission = checkPermission(player, worldName, event.getEntity().getX(), event.getEntity().getZ());

        if (hasPermission) {
            // 这里添加你自己的右键逻辑
            LOGGER.info("Player " + player + " has permission to right-click the block.");
        } else {
            // 如果权限不足，取消事件
            event.setCanceled(true);
            LOGGER.info("Player " + player + " does not have permission to right-click the block.");
        }
    }

    // 实体与方块交互事件
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        String player = event.getEntity().getName().getString();
        String world = event.getLevel().dimension().location().getNamespace();
        String worldName = world.equals("minecraft") ? event.getLevel().dimension().location().getPath() : world;

        boolean hasPermission = checkPermission(player, worldName, event.getEntity().getX(), event.getEntity().getZ());

        if (hasPermission) {
            // 执行允许的逻辑
            LOGGER.info("Player " + player + " has permission to interact with the entity.");
        } else {
            // 如果权限不足，取消事件
            event.setCanceled(true);
            LOGGER.info("Player " + player + " does not have permission to interact with the entity.");
        }
    }
}
