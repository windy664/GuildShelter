package org.windy.guildshelter;

import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.adapter.bukkit.GuildShelterConfig;
import org.windy.guildshelter.adapter.bukkit.GuildWorldRegistry;
import org.windy.guildshelter.adapter.bukkit.command.GsCommand;
import org.windy.guildshelter.adapter.bukkit.listener.RegionTitleListener;
import org.windy.guildshelter.adapter.bukkit.world.BukkitTerrainPreparer;
import org.windy.guildshelter.adapter.bukkit.world.WorldManager;
import org.windy.guildshelter.adapter.provider.LegendaryGuildListener;
import org.windy.guildshelter.adapter.provider.PlayerGuildListener;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;
import org.windy.guildshelter.domain.port.TerrainPreparer;
import org.windy.guildshelter.service.GuildService;
import org.windy.guildshelter.persistence.SqliteDatabase;
import org.windy.guildshelter.persistence.SqliteGuildRepository;
import org.windy.guildshelter.persistence.SqliteManorRepository;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

/**
 * Bukkit 端入口（装配根）。
 *
 * <p>已接入：配置 → 持久层（SQLite）→ LayoutCalculator → WorldManager + 自定义生成器 → /gs 命令。
 * 待接入：GuildProvider（Phase 4）、保护监听器（Phase 5）、惰性 chunk 加载、经济。
 */
public final class GuildShelterPlugin extends JavaPlugin {

    private static GuildShelterPlugin instance;

    private SqliteDatabase database;
    private WorldManager worldManager;

    public static GuildShelterPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        getServer().getConsoleSender().sendMessage(Texts.logo);

        getDataFolder().mkdirs();
        saveDefaultConfig();

        GuildShelterConfig config = GuildShelterConfig.from(getConfig());
        LayoutCalculator layout = new LayoutCalculator(config.layout());

        this.database = SqliteDatabase.forFile(new File(getDataFolder(), "database.db").toPath());
        GuildRepository guilds = new SqliteGuildRepository(database);
        ManorRepository manors = new SqliteManorRepository(database);

        this.worldManager = new WorldManager(layout, getLogger());
        TerrainPreparer terrain = new BukkitTerrainPreparer(this);

        GuildService service = new GuildService(guilds, manors, worldManager, terrain,
                layout, config.levels(), config.terrainPrep());

        GuildWorldRegistry registry = new GuildWorldRegistry();

        GsCommand command = new GsCommand(worldManager, guilds, service, registry, layout);
        PluginCommand gs = getCommand("gs");
        if (gs != null) {
            gs.setExecutor(command);
            gs.setTabCompleter(command);
        }

        // 启动时把已有公会世界载入注册表（修复重启后 title 不弹 / 监听器查不到 origin）。
        for (GuildWorld gw : guilds.findAll()) {
            registry.register(gw);
        }

        // 进入公会世界时按位置弹 title（主城/地皮#N/道路），用于可视化验证网格。
        getServer().getPluginManager().registerEvents(new RegionTitleListener(layout, registry), this);

        // 接公会插件：入会自动分地皮、退会/被踢释放、解散清理。
        // 可插拔——按服务器实际装的公会插件挂对应监听器。未来支持新的公会插件，
        // 只需新写一个 Listener 适配并在下面 List 里加一行（软依赖：装了哪个就挂哪个，都没装则跳过）。
        // 监听器工厂用 lambda 延迟构造：插件不在时不实例化，对应的公会插件 API 类也不会被加载。
        record GuildHook(String pluginName, Supplier<Listener> factory) {}
        List<GuildHook> hooks = List.of(
                new GuildHook("PlayerGuild",
                        () -> new PlayerGuildListener(service, guilds, registry, getLogger())),
                new GuildHook("LegendaryGuild",
                        () -> new LegendaryGuildListener(service, guilds, registry, getLogger())));

        boolean hooked = false;
        for (GuildHook hook : hooks) {
            if (getServer().getPluginManager().getPlugin(hook.pluginName()) != null) {
                getServer().getPluginManager().registerEvents(hook.factory().get(), this);
                getLogger().info("已挂接 " + hook.pluginName() + " 事件。");
                hooked = true;
            }
        }
        if (!hooked) {
            getLogger().info("未检测到任何受支持的公会插件，仅 /gs admin 手动管理可用。");
        }

        getLogger().info("GuildShelter 已启用。");
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
        getLogger().info("GuildShelter 已禁用。");
        instance = null;
    }
}
