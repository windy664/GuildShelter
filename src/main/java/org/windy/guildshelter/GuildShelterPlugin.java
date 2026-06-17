package org.windy.guildshelter;

import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.adapter.bukkit.ClaimGuard;
import org.windy.guildshelter.adapter.bukkit.GuildShelterConfig;
import org.windy.guildshelter.adapter.bukkit.GuildWorldRegistry;
import org.windy.guildshelter.adapter.bukkit.InteractionPolicy;
import org.windy.guildshelter.adapter.bukkit.ManorBuffTask;
import org.windy.guildshelter.adapter.bukkit.ManorEntityCensus;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.adapter.bukkit.command.GsCommand;
import org.windy.guildshelter.adapter.bukkit.listener.ManorAccessListener;
import org.windy.guildshelter.adapter.bukkit.listener.ManorCapListener;
import org.windy.guildshelter.adapter.bukkit.listener.ManorEntityListener;
import org.windy.guildshelter.adapter.bukkit.listener.ManorEnvListener;
import org.windy.guildshelter.adapter.bukkit.listener.ManorFireListener;
import org.windy.guildshelter.adapter.bukkit.listener.ManorFlagListener;
import org.windy.guildshelter.adapter.bukkit.listener.ManorPlayerListener;
import org.windy.guildshelter.adapter.bukkit.listener.ManorProtectionListener;
import org.windy.guildshelter.adapter.bukkit.listener.RegionTitleListener;
import org.windy.guildshelter.adapter.bukkit.world.BukkitTerrainPreparer;
import org.windy.guildshelter.adapter.bukkit.world.WorldManager;
import org.windy.guildshelter.adapter.provider.GuildCommandListener;
import org.windy.guildshelter.adapter.provider.GuildPluginSyncTask;
import org.windy.guildshelter.adapter.provider.LegendaryGuildListener;
import org.windy.guildshelter.adapter.provider.PlayerGuildListener;
import org.windy.guildshelter.adapter.provider.ShetuanAccess;
import org.windy.guildshelter.adapter.provider.ShetuanSyncTask;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;
import org.windy.guildshelter.domain.port.TerrainPreparer;
import org.windy.guildshelter.domain.rule.PermissionRules;
import org.windy.guildshelter.neoforge.NeoForgeHooks;
import org.windy.guildshelter.service.GuildService;
import org.windy.guildshelter.persistence.Storage;
import org.windy.guildshelter.persistence.StorageFactory;

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

    private Storage storage;
    private WorldManager worldManager;
    private ClaimGuard claimGuard;
    private ManorLookup manorLookup;
    private InteractionPolicy interactionPolicy;
    private ManorEntityCensus entityCensus;

    public static GuildShelterPlugin get() {
        return instance;
    }

    /** 供 NeoForge 端(混合端)的保护监听器在事件时取用共享判定;onEnable 前为 null。 */
    public static ClaimGuard protectionGuard() {
        return instance == null ? null : instance.claimGuard;
    }

    /** 供 NeoForge 端的 flag 后端取用地皮解析;onEnable 前/未启用为 null。 */
    public static ManorLookup manorLookup() {
        return instance == null ? null : instance.manorLookup;
    }

    /** 供 NeoForge 端(混合端)的保护监听器取用"访客交互放宽"判定;onEnable 前/未启用为 null。 */
    public static InteractionPolicy interactionPolicy() {
        return instance == null ? null : instance.interactionPolicy;
    }

    /**
     * 地皮实体计数服务。caps 拦截用,亦作可复用 API 供未来"家园卡/评分"等按实体数量判断的功能取数。
     * onEnable 前/保护未启用为 null。
     */
    public static ManorEntityCensus entityCensus() {
        return instance == null ? null : instance.entityCensus;
    }

    /** 探测是否混合端(NeoForge 在)。用字符串反射，纯 Bukkit 端不会因此加载到 NeoForge 类。 */
    private static boolean isNeoForgePresent() {
        try {
            Class.forName("net.neoforged.fml.loading.FMLLoader");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        getServer().getConsoleSender().sendMessage(Texts.logo);

        getDataFolder().mkdirs();
        saveDefaultConfig();

        GuildShelterConfig config = GuildShelterConfig.from(getConfig());
        // 注意：不再用全局 LayoutCalculator。每个世界用自己冻结的 gw.layout()；
        // config.layout() 仅用于①给新世界盖章②老库无快照时回退。

        // 存储后端按 config 选(sqlite/mysql/flatfile);领域只认端口仓库。
        this.storage = StorageFactory.create(config.storage(), getDataFolder().toPath(), config.layout());
        GuildRepository guilds = storage.guilds();
        ManorRepository manors = storage.manors();
        getLogger().info("存储后端: " + config.storage().type());

        this.worldManager = new WorldManager(config.levels(), getLogger());
        // 整地按载体分流：混合端用 NeoForge 原生端（高度图准、覆盖模组方块、不被 Bukkit 兼容层坑），
        // 纯 Bukkit 端用 Bukkit 实现。三元只执行一支，NeoForge 类仅在混合端被惰性加载（纯 Bukkit 永不触及）。
        TerrainPreparer terrain;
        if (isNeoForgePresent()) {
            terrain = new org.windy.guildshelter.neoforge.NeoForgeTerrainPreparer(this);
            getLogger().info("整地：NeoForge 原生端（混合端）。");
        } else {
            terrain = new BukkitTerrainPreparer(this);
        }

        GuildService service = new GuildService(guilds, manors, worldManager, terrain,
                config.layout(), config.levels(), config.terrainPrep());

        GuildWorldRegistry registry = new GuildWorldRegistry();

        GsCommand command = new GsCommand(worldManager, guilds, manors, service, registry,
                config.levels(), getLogger());
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
        getServer().getPluginManager().registerEvents(
                new RegionTitleListener(registry, manors, config.levels()), this);

        // 领地保护：判定逻辑抽到平台中立的 ClaimGuard，Bukkit/NeoForge 两侧共用。
        // 只在开启时构造 guard——关闭时 protectionGuard() 为 null，NeoForge 端也随之放行（开关两端通吃）。
        if (getConfig().getBoolean("protection", true)) {
            this.claimGuard = new ClaimGuard(registry, manors, new PermissionRules());
            // ManorLookup/InteractionPolicy 先建好：保护监听器(交互放宽)与 NeoForge 侧都要用。
            ManorLookup lookup = new ManorLookup(registry, manors);
            this.manorLookup = lookup; // 供 NeoForge flag 后端(混合端)惰性取用
            this.interactionPolicy = new InteractionPolicy(claimGuard, lookup); // 访客交互按类放宽,两载体共用
            this.entityCensus = new ManorEntityCensus(registry); // 实体计数:caps 拦截 + 未来家园卡等复用
            if (isNeoForgePresent()) {
                // 混合端：从 Bukkit 侧直接挂 NeoForge EVENT_BUS（@Mod 入口不会被 FML 加载）。
                // 注册保护(含交互放宽+实体保护) + flag 氛围类两个 NeoForge 处理器;Bukkit 对应监听器随之跳过避免双重。
                NeoForgeHooks.register();
                getLogger().info("领地保护 + Flag 氛围类：已从 Bukkit 侧挂到 NeoForge EVENT_BUS。");
            } else {
                getServer().getPluginManager().registerEvents(new ManorProtectionListener(claimGuard, interactionPolicy), this);
                getServer().getPluginManager().registerEvents(new ManorEntityListener(interactionPolicy), this);
                getLogger().info("领地保护已启用（纯 Bukkit 端，含交互放宽+实体保护）。");
            }
            // 地皮 flag 执行(A 氛围:pvp/怪物/爆炸/火/怪物破坏;B 访问:deny-entry/greeting/farewell)。
            // Bukkit 侧,Youer 上即生效;NeoForge 侧 flag 后端作为后续补(与保护同思路)。
            // 氛围类(A:pvp/怪物/爆炸/怪物破坏)按载体分流:NeoForge 在则交其 EVENT_BUS(覆盖模组),否则 Bukkit。
            if (isNeoForgePresent()) {
                // pvp/怪物/爆炸/怪物破坏 已在上面 NeoForgeHooks.register() 挂到 NeoForge 总线，跳过 Bukkit 免双重。
                getLogger().info("地皮 Flag 氛围类(除火)走 NeoForge 端，Bukkit 监听跳过。");
            } else {
                getServer().getPluginManager().registerEvents(new ManorFlagListener(lookup), this);
            }
            // fire-spread 与 方块环境组(生长/结冰/红石/液流/落叶)：NeoForge 无对应事件,两载体都靠 Bukkit。
            getServer().getPluginManager().registerEvents(new ManorFireListener(lookup), this);
            getServer().getPluginManager().registerEvents(new ManorEnvListener(lookup), this);
            // 伤害/实体组里的玩家行为类(keep-inventory/item-drop/instabreak/mob-place)始终 Bukkit。
            getServer().getPluginManager().registerEvents(new ManorPlayerListener(lookup), this);
            // 实体数量上限 caps:生物生成 + 载具放置。两载体都注册(原版生物/载具 Bukkit 覆盖);
            // 混合端模组生物另由 NeoForge 侧 FinalizeSpawn 补足(对原版幂等,未设 cap 时零开销)。
            getServer().getPluginManager().registerEvents(new ManorCapListener(lookup, entityCensus), this);
            // 访问类(B)与个人增益(C)是玩家行为,Youer 上 Bukkit 全覆盖,始终走 Bukkit。
            getServer().getPluginManager().registerEvents(new ManorAccessListener(lookup), this);
            new ManorBuffTask(lookup).runTaskTimer(this, 20L, 20L);
            getLogger().info("地皮 Flag 执行已启用（访问/增益走 Bukkit，氛围按载体分流）。");
        } else {
            getLogger().info("领地保护已禁用。");
        }

        // 接公会插件：入会自动分地皮、退会/被踢释放、解散清理。
        // 可插拔——按服务器实际装的公会插件挂对应监听器。未来支持新的公会插件，
        // 只需新写一个 Listener 适配并在下面 List 里加一行（软依赖：装了哪个就挂哪个，都没装则跳过）。
        // 监听器工厂用 lambda 延迟构造：插件不在时不实例化，对应的公会插件 API 类也不会被加载。
        record GuildHook(String pluginName, Supplier<Listener> factory) {}
        List<GuildHook> hooks = List.of(
                new GuildHook("PlayerGuild",
                        () -> new PlayerGuildListener(service, guilds, registry, getLogger())),
                new GuildHook("LegendaryGuildRemapped",
                        () -> new LegendaryGuildListener(service, guilds, registry, getLogger())));

        boolean hooked = false;
        for (GuildHook hook : hooks) {
            if (getServer().getPluginManager().getPlugin(hook.pluginName()) != null) {
                getServer().getPluginManager().registerEvents(hook.factory().get(), this);
                getLogger().info("已挂接 " + hook.pluginName() + " 事件。");
                hooked = true;
            }
        }
        // Shetuan(社团)不发任何自定义事件 → 自造同步引擎:定时 diff 成员名单与已分配地皮,
        // 把"入会分地皮/退会释放/解散清理"用轮询补出来。
        if (getServer().getPluginManager().getPlugin("Shetuan") != null) {
            ShetuanAccess access = ShetuanAccess.tryCreate(getServer().getPluginManager(), getLogger());
            if (access != null) {
                long periodTicks = Math.max(1L, getConfig().getLong("shetuan.sync-interval-seconds", 30)) * 20L;
                new ShetuanSyncTask(access, service, guilds, manors, registry, getLogger())
                        .runTaskTimer(this, 20L * 5, periodTicks);
                getLogger().info("已挂接 Shetuan(轮询同步,每 " + (periodTicks / 20L) + "s)。");
                hooked = true;
            } else {
                getLogger().warning("检测到 Shetuan 但无法接入其管理器,社团同步未启用。");
            }
        }

        // Guild(com.guild,作者 ya_xzer21145)同样不发事件 → 复用同步引擎(GuildId=会名)。
        if (getServer().getPluginManager().getPlugin("Guild") != null) {
            long periodTicks = Math.max(1L, getConfig().getLong("guild-plugin.sync-interval-seconds", 30)) * 20L;
            boolean disbandSweep = getConfig().getBoolean("guild-plugin.disband-sweep", true);
            GuildPluginSyncTask guildTask =
                    new GuildPluginSyncTask(service, guilds, manors, registry, getLogger(), disbandSweep);
            guildTask.runTaskTimer(this, 20L * 5, periodTicks);
            // 即时触发：玩家敲 /guild 后主动踢一次同步，建会/入会近即时出世界(轮询仍是安全网)。
            if (getConfig().getBoolean("guild-plugin.instant-trigger", true)) {
                long delayTicks = Math.max(1L, getConfig().getLong("guild-plugin.instant-trigger-delay-ticks", 2));
                getServer().getPluginManager()
                        .registerEvents(new GuildCommandListener(guildTask, this, delayTicks), this);
            }
            getLogger().info("已挂接 Guild(轮询同步,每 " + (periodTicks / 20L) + "s)。");
            hooked = true;
        }

        if (!hooked) {
            getLogger().info("未检测到任何受支持的公会插件，仅 /gs admin 手动管理可用。");
        }

        getLogger().info("GuildShelter 已启用。");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.close();
        }
        getLogger().info("GuildShelter 已禁用。");
        instance = null;
    }
}
