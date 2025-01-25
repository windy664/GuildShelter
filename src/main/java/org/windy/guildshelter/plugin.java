package org.windy.guildshelter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.database.mysql.DatabaseManager;
import org.windy.guildshelter.command.GuildShelterCommand;
import org.windy.guildshelter.listener.GuildCreateListener;
import org.windy.guildshelter.listener.GuildMoveListener;
import org.windy.guildshelter.listener.GuildShelterEnterListener;
import org.windy.guildshelter.listener.neoforge.BlockInteractListener;

import java.io.File;

import static net.neoforged.neoforge.common.NeoForge.EVENT_BUS;

public class plugin extends JavaPlugin {

    public static final Logger LOGGER = LogManager.getLogger();
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        // 保存默认的配置文件
        saveDefaultConfig();
        saveDefaultLangFile();

        // 加载语言文件
        FileConfiguration langConfig = getLangConfig();

        // 依赖注册
        registerWorldEdit(langConfig);

        // 注册事件监听器
        registerEventListeners();

        // 注册命令
        registerCommands();

        // 初始化并连接数据库
        databaseManager = new DatabaseManager(this);
        if (databaseManager.isConnected()) {
            // 在数据库连接成功后创建表格
            databaseManager.registerTables();
        }

        LOGGER.info(ChatColor.YELLOW + "插件已启用！");
    }

    @Override
    public void onDisable() {
        // 注销事件监听器
        EVENT_BUS.unregister(new BlockInteractListener());
        LOGGER.info(ChatColor.YELLOW + "所有Neoforge事件已注销！");
        // 关闭数据库连接
        if (databaseManager != null) {
            databaseManager.closeConnection();
            LOGGER.info(ChatColor.YELLOW + "数据库已断开连接！");
        }

        LOGGER.info(ChatColor.YELLOW + "插件已禁用！");
    }

    private void registerWorldEdit(FileConfiguration langConfig) {
        try {
            Class.forName("com.sk89q.worldedit.WorldEdit");
            LOGGER.info(ChatColor.GREEN + langConfig.getString("worldedit"));
        } catch (ClassNotFoundException e) {
            LOGGER.warn(ChatColor.RED + "未检测到 WorldEdit 模组！");
        }
    }

    private void registerEventListeners() {
        // 注册事件监听器
        EVENT_BUS.register(new BlockInteractListener());
        LOGGER.info(ChatColor.YELLOW + "Neoforge监听器注册完毕");

        // 注册 GuildCreateListener
        getServer().getPluginManager().registerEvents(new GuildCreateListener(this), this);
        LOGGER.info(ChatColor.YELLOW + "公会创建监听器注册完毕");

        getServer().getPluginManager().registerEvents(new GuildMoveListener(databaseManager), this);
        LOGGER.info(ChatColor.YELLOW + "位移事件监听器注册完毕");
        // 注册 GuildShelterEnterListener 监听器
        getServer().getPluginManager().registerEvents(new GuildShelterEnterListener(), this);
        LOGGER.info(ChatColor.YELLOW + "进入公会检测监听器注册完毕");
    }

    private void registerCommands() {
        // 注册 /gs 命令
        this.getCommand("gs").setExecutor(new GuildShelterCommand(this));
        this.getCommand("gs").setTabCompleter(new GuildShelterCommand(this));
        LOGGER.info(ChatColor.YELLOW + "/gs指令注册完毕");
    }

    private void saveDefaultLangFile() {
        File langFile = new File(getDataFolder(), "lang.yml");

        if (!langFile.exists()) {
            saveResource("lang.yml", false); // 复制 lang.yml
            getLogger().info("lang.yml 文件已从资源文件夹复制到插件数据文件夹.");
        } else {
            getLogger().info("lang.yml 文件已存在.");
        }
    }

    private FileConfiguration getLangConfig() {
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "lang.yml"));
    }
}
