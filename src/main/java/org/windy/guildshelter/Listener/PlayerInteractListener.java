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
    }
}
