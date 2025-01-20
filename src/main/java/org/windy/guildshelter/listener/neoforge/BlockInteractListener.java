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


    // 左键点击方块事件
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.LeftClickBlock event) {
        String player = event.getEntity().getName().getString();
        String world = event.getLevel().dimension().toString();
        int x = event.getPos().getX();
        int z = event.getPos().getZ();

        // 检查实体的名字是否包含特定的子字符串
        if (player.contains("AS-FAKEPLAYER") ||
                player.contains("[MINECRAFT]") ||
                player.contains("[MEKANISM]") ||
                player.contains("[IF]") ||
                player.contains("[IntegratedTunnels]") ||
                player.contains("KILLER JOE") ||
                player.contains("[DEPOLYER]") ||
                player.contains("[XU2FAKEPLAYER]") ||
                player.contains("[MODULAR ROUTERS]")) {
            // 如果玩家名字匹配条件，返回，不处理事件
            LOGGER.info("假玩家" + player + " 触发了 PlayerInteractEvent");
            return;
        }
        if(PermissionCheck.hasPermission(player,world,x,z)){
            return;
        }

        // 如果权限不足，取消事件
        event.setCanceled(true);
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
