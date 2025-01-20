package org.windy.guildshelter.api;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.plugin;

public class ConfigAPI {

    private static JavaPlugin plugin = JavaPlugin.getPlugin(plugin.class);  // 获取插件实例
    private static FileConfiguration config = plugin.getConfig();  // 获取配置文件

    // 加载配置文件（如果插件没有自定义配置文件，可以通过这个方法加载默认的 config.yml）
    public static void loadConfig() {
        plugin.saveDefaultConfig();  // 如果没有配置文件则使用默认配置
        plugin.reloadConfig();  // 重新加载配置文件
    }

    // 获取配置项
    public static int getPlotLength() {
        return config.getInt("Plot-length", 100);  // 默认值为 100
    }

    public static int getPlotWidth() {
        return config.getInt("Plot-width", 100);  // 默认值为 100
    }

    public static int getTotalLength() {
        return config.getInt("Total-length", 500);  // 默认值为 500
    }

    public static int getTotalWidth() {
        return config.getInt("Total-width", 500);  // 默认值为 500
    }

    public static int getRoadWidth() {
        return config.getInt("Road-width", 5);  // 默认值为 5
    }
    public static int getRadius(){
        return config.getInt("Radius", 40);
    }
}
