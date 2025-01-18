package org.windy.guildshelter.Listener;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerInteractListener extends JavaPlugin implements Listener {
    private static final Logger LOGGER = LogManager.getLogger();
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {

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
    }
}
