package org.windy.guildshelter.listener.neoforge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
public class BlockInteractListener {

    private static final Logger LOGGER = LogManager.getLogger();


    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.LeftClickBlock event) {
        LOGGER.info("PlayerInteractEvent 触发");

        String deplayer = event.getEntity().getName().toString();

        // 检查实体的名字是否包含特定的子字符串
        if (deplayer.contains("AS-FAKEPLAYER") ||
                deplayer.contains("[MINECRAFT]") ||
                deplayer.contains("[MEKANISM]") ||
                deplayer.contains("[IF]") ||
                deplayer.contains("[IntegratedTunnels]") ||
                deplayer.contains("KILLER JOE") ||
                deplayer.contains("[DEPOLYER]") ||
                deplayer.contains("[XU2FAKEPLAYER]") ||
                deplayer.contains("[MODULAR ROUTERS]")) {
            // 如果玩家名字匹配条件，返回，不处理事件
            LOGGER.info("假玩家" + deplayer + " 触发了 PlayerInteractEvent");
            return;
        }
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