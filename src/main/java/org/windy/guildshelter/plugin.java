package org.windy.guildshelter;

import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.Listener.PlayerInteractListener;

import static net.neoforged.neoforge.common.NeoForge.EVENT_BUS;

public class plugin extends JavaPlugin {
    public static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void onEnable() {
        // 注册事件监听器
        EVENT_BUS.register(new PlayerInteractListener());  // 只注册监听器类
        LOGGER.info("注册neoforge监听器！");
    }

    @Override
    public void onDisable() {
        // 注销事件监听器
        EVENT_BUS.unregister(new PlayerInteractListener());
    }
}
