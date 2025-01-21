package org.windy.guildshelter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.command.GuildShelterCommand;
import org.windy.guildshelter.database.GuildRegionTable;
import org.windy.guildshelter.database.PlotTable;
import org.windy.guildshelter.listener.GuildCreateListener;
import org.windy.guildshelter.listener.neoforge.BlockInteractListener;
import org.windy.guildshelter.database.CenterTable;
import org.windy.guildshelter.database.DatabaseManager;

import java.io.File;
import java.sql.Connection;

import static net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
import org.bukkit.ChatColor;

public class plugin extends JavaPlugin {

    public static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void onEnable() {
        // 保存默认的配置文件
        saveDefaultConfig();
        saveDefaultLangFile();

        // 加载语言文件
        FileConfiguration langConfig = getLangConfig();

        // 连接数据库
        DatabaseManager.init();
        LOGGER.info(ChatColor.GREEN+"连接到数据库");

        // 初始化表格
        initializeTables();

        // 依赖注册
        registerWorldEdit(langConfig);

        // 注册事件监听器
        registerEventListeners();

        // 注册命令
        registerCommands();

        LOGGER.info(ChatColor.YELLOW + "插件已启用！");
    }

    @Override
    public void onDisable() {
        // 注销事件监听器
        EVENT_BUS.unregister(new BlockInteractListener());

        // 关闭数据库连接
        DatabaseManager.close();

        LOGGER.info(ChatColor.YELLOW + "插件已禁用！");
    }

    private void initializeTables() {
        // 创建数据库表格
        PlotTable plotTable = new PlotTable();
        plotTable.createPlotTable();

        GuildRegionTable guildRegionTable = new GuildRegionTable();
        guildRegionTable.createGuildRegionTable();

        CenterTable centerTable = new CenterTable();
        centerTable.createCenterTable();

        LOGGER.info(ChatColor.AQUA + "数据库表格已初始化！");
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
        LOGGER.info(ChatColor.YELLOW + "注册 Neoforge 监听器！");

        // 注册 GuildCreateListener
        getServer().getPluginManager().registerEvents(new GuildCreateListener(this), this);
        LOGGER.info(ChatColor.YELLOW + "注册公会创建监听器！");
    }

    private void registerCommands() {
        // 注册 /gs 命令
        this.getCommand("gs").setExecutor(new GuildShelterCommand(this));
        this.getCommand("gs").setTabCompleter(new GuildShelterCommand(this));
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
