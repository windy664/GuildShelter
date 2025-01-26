package org.windy.guildshelter.listener.neoforge;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.windy.guildshelter.util.PermissionCheck;
import org.windy.guildshelter.database.mysql.DatabaseManager;

public class BlockInteractListener {

    private static final Logger LOGGER = LogManager.getLogger();


    private final PermissionCheck permissionCheck;

    public BlockInteractListener(PermissionCheck permissionCheck) {
        this.permissionCheck = permissionCheck;
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
    private String getWorldName(Level world) {
        String worldName = world.dimension().location().getPath();  // 获取维度路径（如 "overworld"）

        // 记录世界名称映射
        LOGGER.info("检测到的世界维度: {}. 映射为世界名称: {}", worldName, worldName.equals("overworld") ? "world" : worldName);

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

        if (isFakePlayer(player)) {
            LOGGER.info("玩家 {} 触发了左键点击事件 (假玩家)。", player);
            return; // 假玩家不处理
        }

        // 如果没有权限，阻止事件
        boolean hasPermission = permissionCheck.hasPermission(player, worldName, (double) event.getEntity().getX(), (double) event.getEntity().getZ());
        if (!hasPermission) {
            event.setCanceled(true);
            LOGGER.info("玩家 {} 没有权限与方块交互，事件被取消。", player);
        }
    }

    // 右键点击方块事件
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        String player = event.getEntity().getName().getString();
        String worldName = getWorldName(event.getLevel());  // 获取正确的世界名称

        if (isFakePlayer(player)) {
            LOGGER.info("玩家 {} 触发了右键点击事件 (假玩家)。", player);
            return; // 假玩家不处理
        }

        // 如果没有权限，阻止事件
        boolean hasPermission = permissionCheck.hasPermission(player, worldName, (double) event.getEntity().getX(), (double) event.getEntity().getZ());
        if (!hasPermission) {
            event.setCanceled(true);
            LOGGER.info("玩家 {} 没有权限右键点击方块，事件被取消。", player);
        }
    }
}
