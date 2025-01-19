package org.windy.guildshelter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.command.GuildShelterCommand;
import org.windy.guildshelter.listener.GuildCreateListener;
import org.windy.guildshelter.listener.neoforge.BlockInteractListener;
import org.windy.guildshelter.util.SqLiteDatabase;
import java.io.File;
import static net.neoforged.neoforge.common.NeoForge.EVENT_BUS;

public class plugin extends JavaPlugin {
    public static final Logger LOGGER = LogManager.getLogger();
    private SqLiteDatabase sqLiteDatabase;

    @Override
    public void onEnable() {
        // 保存默认的 config.yml 配置文件
        saveDefaultConfig();

        // 保存 lang.yml 文件（如果不存在）
        saveDefaultLangFile();

        // 获取 lang.yml 文件的配置
        FileConfiguration langConfig = getLangConfig();
        // 初始化数据库
        sqLiteDatabase = new SqLiteDatabase();
        sqLiteDatabase.connect();
        // 创建 plot 表格
        sqLiteDatabase.connect();
        sqLiteDatabase.createPlotTable();

        //依赖注册
        try {
            Class.forName("com.sk89q.worldedit.WorldEdit");
            LOGGER.info(langConfig.getString("worldedit"));
        } catch (ClassNotFoundException e) {
            LOGGER.warn("未检测到 WorldEdit 模组！");
        }

        // 注册事件监听器
        EVENT_BUS.register(new BlockInteractListener());  // 只注册监听器类
        LOGGER.info("注册neoforge监听器！");
        // 注册 GuildCreateListener
        getServer().getPluginManager().registerEvents(new GuildCreateListener(this), this);
        LOGGER.info("注册公会创建监听器！");

        // 注册 /gs 命令
        this.getCommand("gs").setExecutor(new GuildShelterCommand(this));
        this.getCommand("gs").setTabCompleter(new GuildShelterCommand(this));
    }

    @Override
    public void onDisable() {
        // 注销事件监听器
        EVENT_BUS.unregister(new BlockInteractListener());
        if (sqLiteDatabase != null) {
            sqLiteDatabase.disconnect();
        }
    }
    // 保存 lang.yml 文件的方法
    private void saveDefaultLangFile() {
        File langFile = new File(getDataFolder(), "lang.yml");

        // 如果 lang.yml 不存在，则将其从资源文件夹复制到数据文件夹
        if (!langFile.exists()) {
            saveResource("lang.yml", false);  // 复制 lang.yml
            getLogger().info("lang.yml 文件已从资源文件夹复制到插件数据文件夹.");
        } else {
            getLogger().info("lang.yml 文件已存在.");
        }
    }

    // 获取 lang.yml 的配置
    private FileConfiguration getLangConfig() {
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "lang.yml"));
    }
}
