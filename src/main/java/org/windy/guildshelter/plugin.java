package org.windy.guildshelter;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.Listener.PlayerInteractListener;

import static net.neoforged.neoforge.common.NeoForge.EVENT_BUS;

public class plugin extends JavaPlugin implements Listener {
    public static final Logger LOGGER = LogManager.getLogger();
    @Override
    public void onEnable() {
        // 使用默认IEventBus注册事件监听器 (当前类)
        EVENT_BUS.register(this);
        EVENT_BUS.register(new PlayerInteractListener());
        LOGGER.info("注册neoforge监听器！");
    }
    @Override
    public void onDisable() {
        EVENT_BUS.unregister(this);
        EVENT_BUS.unregister(new PlayerInteractListener());
    }

}
