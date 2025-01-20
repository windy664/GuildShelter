package org.windy.guildshelter.listener.neoforge;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.windy.guildshelter.util.PermissionCheck;

public class BlockInteractListener {

    private static final Logger LOGGER = LogManager.getLogger();

    // 提取通用的权限检查逻辑
    private boolean isPlayerInPlot(String player, String worldName, double playerX, double playerZ) {
        // 调用权限检查方法，传递玩家名称、世界名称、玩家坐标
        boolean hasPermission = PermissionCheck.hasPermission(player, worldName, (int) playerX, (int) playerZ);

        // 记录是否有权限
        LOGGER.info("Checking permission for player: {} at world: {} with coordinates ({}, {}) - Permission: {}",
                player, worldName, (int) playerX, (int) playerZ, hasPermission);

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

    // 获取世界名称，处理世界维度和主世界
// 获取世界名称，处理世界维度和主世界
    private String getWorldName(Level world) {
        String worldName = world.dimension().location().getPath();  // 获取维度路径（如 "overworld"）

        // 记录世界名称映射
        LOGGER.info("Detected world dimension: {}. Mapping to world name: {}", worldName, worldName.equals("overworld") ? "world" : worldName);

        // 如果是主世界，映射为 "world"
        if (worldName.equals("overworld")) {
            return "world";  // 映射为数据库中的世界名称
        }

        // 对于其他维度，直接返回其名称
        return worldName;
    }

    // 左键点击方块事件
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.LeftClickBlock event) {
        String player = event.getEntity().getName().getString();
        String worldName = getWorldName(event.getLevel());  // 获取正确的世界名称

        // 先检查玩家是否在公会地块范围内
        if (!isPlayerInPlot(player, worldName, event.getEntity().getX(), event.getEntity().getZ())) {
            LOGGER.info("Player {} is not in plot range, skipping permission check.", player);
            return; // 不在范围内，不阻断操作
        }

        // 如果玩家在公会地块范围内，则进行权限检查
        if (isFakePlayer(player)) {
            LOGGER.info("Fake player {} triggered PlayerInteractEvent.", player);
            return; // 假玩家不处理
        }

        // 如果没有权限，阻止事件
        boolean hasPermission = PermissionCheck.hasPermission(player, worldName, (int) event.getEntity().getX(), (int) event.getEntity().getZ());
        if (!hasPermission) {
            event.setCanceled(true);
            LOGGER.info("Player {} does not have permission to interact with the block.", player);
        }
    }

    // 右键点击方块事件
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        String player = event.getEntity().getName().getString();
        String worldName = getWorldName(event.getLevel());  // 获取正确的世界名称

        // 先检查玩家是否在公会地块范围内
        if (!isPlayerInPlot(player, worldName, event.getEntity().getX(), event.getEntity().getZ())) {
            LOGGER.info("Player {} is not in plot range, skipping permission check.", player);
            return; // 不在范围内，不阻断操作
        }

        // 如果玩家在公会地块范围内，则进行权限检查
        if (isFakePlayer(player)) {
            LOGGER.info("Fake player {} triggered PlayerInteractEvent.", player);
            return; // 假玩家不处理
        }

        // 如果没有权限，阻止事件
        boolean hasPermission = PermissionCheck.hasPermission(player, worldName, (int) event.getEntity().getX(), (int) event.getEntity().getZ());
        if (!hasPermission) {
            event.setCanceled(true);
            LOGGER.info("Player {} does not have permission to right-click the block.", player);
        }
    }

    // 实体与方块交互事件
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        String player = event.getEntity().getName().getString();
        String worldName = getWorldName(event.getLevel());  // 获取正确的世界名称

        // 先检查玩家是否在公会地块范围内
        if (!isPlayerInPlot(player, worldName, event.getEntity().getX(), event.getEntity().getZ())) {
            LOGGER.info("Player {} is not in plot range, skipping permission check.", player);
            return; // 不在范围内，不阻断操作
        }

        // 如果玩家在公会地块范围内，则进行权限检查
        if (isFakePlayer(player)) {
            LOGGER.info("Fake player {} triggered PlayerInteractEvent.", player);
            return; // 假玩家不处理
        }

        // 如果没有权限，阻止事件
        boolean hasPermission = PermissionCheck.hasPermission(player, worldName, (int) event.getEntity().getX(), (int) event.getEntity().getZ());
        if (!hasPermission) {
            event.setCanceled(true);
            LOGGER.info("Player {} does not have permission to interact with the entity.", player);
        }
    }
}
