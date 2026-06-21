package org.windy.guildshelter;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.adapter.bukkit.ClaimGuard;
import org.windy.guildshelter.adapter.bukkit.GuildShelterConfig;
import org.windy.guildshelter.adapter.bukkit.GuildShelterPapi;
import org.windy.guildshelter.adapter.bukkit.GuildUpkeepTask;
import org.windy.guildshelter.adapter.bukkit.GuildWorldRegistry;
import org.windy.guildshelter.adapter.bukkit.Messages;
import org.windy.guildshelter.adapter.bukkit.SupervisorCache;
import org.windy.guildshelter.adapter.bukkit.WorldCache;
import org.windy.guildshelter.adapter.bukkit.gui.BukkitInventoryUi;
import org.windy.guildshelter.adapter.bukkit.gui.ModChannelUi;
import org.windy.guildshelter.adapter.bukkit.gui.VanillaGuiListener;
import org.windy.guildshelter.adapter.bukkit.gui.YamlGuiLoader;
import org.windy.guildshelter.domain.port.ui.UiActionRouter;
import org.windy.guildshelter.domain.port.ui.UiBackend;
import org.windy.guildshelter.adapter.bukkit.XaeroIntegration;
import org.windy.guildshelter.adapter.bukkit.ManorLimitTask;
import org.windy.guildshelter.adapter.bukkit.ManorUpgradeCommandHook;
import org.windy.guildshelter.adapter.bukkit.WorldOptimizer;
import org.windy.guildshelter.adapter.bukkit.PerformanceBroadcastTask;
import org.windy.guildshelter.adapter.bukkit.ManorChunkManager;
import org.windy.guildshelter.adapter.bukkit.InteractionPolicy;
import org.windy.guildshelter.adapter.bukkit.GuildMemberCache;
import org.windy.guildshelter.adapter.bukkit.ManorBuffTask;
import org.windy.guildshelter.adapter.bukkit.ManorEntityCensus;
import org.windy.guildshelter.adapter.bukkit.ManorLookup;
import org.windy.guildshelter.adapter.bukkit.VisitCounter;
import org.windy.guildshelter.adapter.bukkit.MergeRegistry;
import org.windy.guildshelter.adapter.bukkit.VaultEconomy;
import org.windy.guildshelter.adapter.bukkit.command.GsCommand;
import org.windy.guildshelter.adapter.bukkit.listener.ManorAccessListener;
import org.windy.guildshelter.adapter.bukkit.listener.GuildMotdListener;
import org.windy.guildshelter.adapter.bukkit.listener.ManorCapListener;
import org.windy.guildshelter.adapter.bukkit.listener.ManorCommandListener;
import org.windy.guildshelter.adapter.bukkit.listener.ManorEntityListener;
import org.windy.guildshelter.adapter.bukkit.listener.ManorEnvListener;
import org.windy.guildshelter.adapter.bukkit.listener.ManorFireListener;
import org.windy.guildshelter.adapter.bukkit.listener.ManorFlagListener;
import org.windy.guildshelter.adapter.bukkit.listener.ManorParticleTask;
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
import org.windy.guildshelter.domain.port.EconomyPort;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;
import org.windy.guildshelter.domain.port.TerrainPreparer;
import org.windy.guildshelter.domain.rule.PermissionRules;
import org.windy.guildshelter.platform.PlatformBindings;
import org.windy.guildshelter.adapter.bukkit.SchematicStores;
import org.windy.guildshelter.domain.port.SchematicStore;
import org.windy.guildshelter.domain.port.ManorMover;
import org.windy.guildshelter.domain.port.ModDataMoverRegistry;
import org.windy.guildshelter.service.GuildService;
import org.windy.guildshelter.adapter.bungee.ProxyChannel;
import org.windy.guildshelter.persistence.Storage;
import org.windy.guildshelter.persistence.StorageFactory;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Bukkit 端入口（装配根）。
 *
 * <p>已接入：配置 → 持久层（SQLite/MySQL/FlatFile）→ WorldManager + 整地 → /gs 命令 → 保护监听器
 * → 公会插件 provider → 性能/社交/搬家/Schematic/PAPI/跨服 → UI 后端（原版 Inventory 兜底 + 模组联动桩）。
 * 待接入：GUI 入口命令（/gs gui，UI 后端与路由已预留，见 {@link #uiBackend()}/{@link #uiRouter()}）。
 */
public abstract class GuildShelterPlugin extends JavaPlugin {

    private static GuildShelterPlugin instance;

    /**
     * 载体接缝（PLAN_MODULES.md §4）：按载体分流的决策（整地/搬家/原生保护/群系采样/mod 搬运/UI auto）
     * 全收口到此。两个薄子类（bukkit / neoforge_26_2）各返回自己的实现。
     */
    protected abstract PlatformBindings createBindings();

    private Storage storage;
    private WorldManager worldManager;
    private ClaimGuard claimGuard;
    private ManorLookup manorLookup;
    private InteractionPolicy interactionPolicy;
    private ManorEntityCensus entityCensus;
    private MergeRegistry mergeRegistry;
    private WorldCache worldCache;
    private SupervisorCache supervisorCache;
    private org.windy.guildshelter.adapter.bukkit.CityTrustCache cityTrustCache;
    private org.windy.guildshelter.adapter.bukkit.RoadPermitCache roadPermitCache;
    private YamlGuiLoader guiLoader;
    private UiActionRouter uiRouter;
    private UiBackend uiBackend;

    public static GuildShelterPlugin get() {
        return instance;
    }

    /** 供 NeoForge 端(混合端)的保护监听器在事件时取用共享判定;onEnable 前为 null。 */
    public static ClaimGuard protectionGuard() {
        return instance == null ? null : instance.claimGuard;
    }

    /** 供 NeoForge 端的 flag 后端取用庄园解析;onEnable 前/未启用为 null。 */
    public static ManorLookup manorLookup() {
        return instance == null ? null : instance.manorLookup;
    }

    /** 供 NeoForge 端(混合端)的保护监听器取用"访客交互放宽"判定;onEnable 前/未启用为 null。 */
    public static InteractionPolicy interactionPolicy() {
        return instance == null ? null : instance.interactionPolicy;
    }

    /**
     * 庄园实体计数服务。caps 拦截用,亦作可复用 API 供未来"家园卡/评分"等按实体数量判断的功能取数。
     * onEnable 前/保护未启用为 null。
     */
    public static ManorEntityCensus entityCensus() {
        return instance == null ? null : instance.entityCensus;
    }

    /** 合并缓存注册表（provider 解散公会时需清理缓存）。 */
    public static MergeRegistry mergeRegistry() {
        return instance == null ? null : instance.mergeRegistry;
    }

    /** 主城信任缓存（命令授权、provider 解散/退会时清理）。onEnable 前为 null。 */
    public static org.windy.guildshelter.adapter.bukkit.CityTrustCache cityTrustCache() {
        return instance == null ? null : instance.cityTrustCache;
    }

    /** 限时路权缓存（命令授予/撤销）。onEnable 前为 null。 */
    public static org.windy.guildshelter.adapter.bukkit.RoadPermitCache roadPermitCache() {
        return instance == null ? null : instance.roadPermitCache;
    }

    /** YAML GUI 加载器（从 gui.yml 读菜单定义）。 */
    public static YamlGuiLoader guiLoader() {
        return instance == null ? null : instance.guiLoader;
    }

    /**
     * UI 动作路由表（预留）。未来 {@code /gs gui} 命令在此注册各菜单的 onBuild/onAction，
     * 无需改动 UI 后端。onEnable 前为 null。
     */
    public static UiActionRouter uiRouter() {
        return instance == null ? null : instance.uiRouter;
    }

    /**
     * 当前 UI 渲染后端（原版 Inventory 兜底 / 模组联动，按 config {@code ui.backend} 选）。
     * 未来打开菜单走 {@code uiBackend().open(viewer, view)}。onEnable 前为 null。
     */
    public static UiBackend uiBackend() {
        return instance == null ? null : instance.uiBackend;
    }

    /** 给新分配庄园的玩家发欢迎消息。 */
    public static void sendWelcome(Player player, String guildName, int slot) {
        if (instance == null) return;
        player.sendMessage(Messages.get("success.welcome", guildName, slot));
    }

    @Override
    public void onEnable() {
        instance = this;
        final PlatformBindings bindings = createBindings(); // 载体接缝（整地/搬家/保护/采样/UI 分流）
        // 启动横幅在 onEnable 末尾打印（彼时存储/保护/宿主等信息齐全），见文件尾。

        getDataFolder().mkdirs();
        saveDefaultConfig();
        // 加载语言文件
        Messages.load(getConfig().getString("language", "zh_CN"), getDataFolder());

        // 等级系统独立配置 levels.yml（首启释放默认文件，并从旧 config.yml 迁移已有等级配置）。
        org.bukkit.configuration.file.FileConfiguration levelsCfg = loadLevelsConfig();
        GuildShelterConfig config = GuildShelterConfig.from(getConfig(), levelsCfg);

        // 存储后端按 config 选(sqlite/mysql/flatfile);领域只认端口仓库。
        this.storage = StorageFactory.create(config.storage(), getDataFolder().toPath(), config.layout());
        GuildRepository guilds = storage.guilds();
        ManorRepository manors = storage.manors();
        getLogger().info("存储后端: " + config.storage().type());

        // 代理跨服通道（BungeeCord / Velocity）
        ProxyChannel proxyChannel = ProxyChannel.create(config.proxyType(), this);
        if (proxyChannel.isAvailable()) {
            getLogger().info("跨服代理: " + config.proxyType() + "（服务器名: " + config.serverName() + "）");
        }

        this.worldManager = new WorldManager(config.levels(), config.oceanReseed(), getLogger());
        this.worldManager.setBiomeSampler(bindings.biomeSampler()); // 混合端注入群系采样，纯 Bukkit 为 null
        // 整地按载体分流（接缝）
        String roadBlock = getConfig().getString("road-surface-block", "minecraft:dirt_path");
        String bridgeBlock = getConfig().getString("road-bridge-block", "auto");
        String bridgeRail = getConfig().getString("road-bridge-rail-block", "auto");
        var wall = config.cityWall();
        TerrainPreparer terrain = bindings.terrain(this, roadBlock, bridgeBlock, bridgeRail,
                wall.enabled(), wall.block(), wall.height());
        getLogger().info("整地：" + (bindings.isHybrid() ? "NeoForge 原生端（混合端）。" : "Bukkit 高度图端。"));

        GuildService service = new GuildService(guilds, manors, worldManager, terrain,
                config.layout(), config.levels(), config.terrainPrep(), getLogger());

        // 庄园升级回调：升到对应等级时由控制台执行对应命令（config: manor-upgrade-commands）。
        ManorUpgradeCommandHook upgradeHook = ManorUpgradeCommandHook.fromConfig(this);
        if (upgradeHook != null) {
            service.setUpgradeHook(upgradeHook);
            getLogger().info("庄园升级命令回调已启用（manor-upgrade-commands）。");
        }

        // 搬家系统
        var moveConfig = config.move();
        if (moveConfig.enabled()) {
            ManorMover mover = bindings.manorMover(this);
            getLogger().info("搬家系统: " + (bindings.isHybrid()
                    ? "NeoForge 原生端（chunk 级复制）。" : "Bukkit/WorldEdit（clipboard 复制）。"));
            ModDataMoverRegistry modDataMovers = new ModDataMoverRegistry();
            bindings.registerModDataMovers(modDataMovers, this); // 混合端注册 RS2 等 mod 数据搬运

            EconomyPort moveEconomy = VaultEconomy.tryCreate(getLogger());
            java.nio.file.Path worldContainer = getServer().getWorldContainer().toPath();
            service.setManorMover(mover, moveEconomy, moveConfig.cost(), moveConfig.cooldownDays(),
                    modDataMovers, worldContainer);
        }

        GuildWorldRegistry registry = new GuildWorldRegistry();
        // 统一配额解析器（优化量 + 机器；等级基础 + 管理员增量 + 玩家自调 cap）；注入 census 供上限拦截。
        this.entityCensus = new ManorEntityCensus(registry, config.performance().quotas(), config.cityLimits());

        // 合并缓存
        this.mergeRegistry = new MergeRegistry(manors);
        for (GuildWorld gw : guilds.findAll()) {
            List<Integer> slots = manors.findAll(gw.guild()).stream()
                    .map(org.windy.guildshelter.domain.model.Manor::slot).toList();
            mergeRegistry.load(gw.guild(), slots);
        }

        this.worldCache = new WorldCache(this.mergeRegistry);
        this.worldCache.setManorRepository(manors);
        this.supervisorCache = new SupervisorCache();
        GuildMemberCache memberCache = new GuildMemberCache(manors, guilds.findAll());

        // 宿主公会 provider（会长角色 + 人数上限），早于 ClaimGuard 选好以便注入主城权限判定。
        org.windy.guildshelter.domain.port.GuildProvider guildProvider;
        String guildSource; // 启动横幅用
        if (getServer().getPluginManager().getPlugin("PlayerGuild") != null) {
            guildProvider = new org.windy.guildshelter.adapter.provider.PlayerGuildProvider();
            guildSource = "PlayerGuild（会长角色 + 人数上限）";
            getLogger().info("宿主能力来源: " + guildSource + "。");
        } else if (getServer().getPluginManager().getPlugin("LegendaryGuildRemapped") != null) {
            guildProvider = new org.windy.guildshelter.adapter.provider.LegendaryGuildProvider();
            guildSource = "LegendaryGuild（会长 owner + 人数上限）";
            getLogger().info("宿主能力来源: " + guildSource + "。");
        } else {
            guildProvider = org.windy.guildshelter.domain.port.GuildProvider.NONE;
            guildSource = "无（仅 /gs admin 手动管理）";
        }
        service.setGuildProvider(guildProvider);
        this.worldManager.setGuildProvider(guildProvider);

        // 主城信任缓存（会长额外信任的会内成员可建主城）；启动全量加载。
        this.cityTrustCache = new org.windy.guildshelter.adapter.bukkit.CityTrustCache(
                storage.cityTrust(), guilds.findAll());

        // 主城 flag 缓存（会长/副会长设的主城 flag，热路径 resolveFlag 用）；启动全量加载。
        final org.windy.guildshelter.adapter.bukkit.CityFlagCache cityFlagCache =
                new org.windy.guildshelter.adapter.bukkit.CityFlagCache(storage.cityFlags(), guilds.findAll());

        // 限时路权缓存（管理员授予的临时路上建造权，惰性过期）；启动全量加载。
        this.roadPermitCache = new org.windy.guildshelter.adapter.bukkit.RoadPermitCache(storage.roadPermit());

        // 主城悬浮字后端：检测到 DecentHolograms 才构造实现类（否则 NOOP，绝不触发 DH 类加载）。
        final org.windy.guildshelter.adapter.bukkit.holo.HologramBackend holoBackend =
                (config.holograms().enabled() && getServer().getPluginManager().getPlugin("DecentHolograms") != null)
                        ? new org.windy.guildshelter.adapter.bukkit.holo.DecentHologramsBackend()
                        : org.windy.guildshelter.adapter.bukkit.holo.HologramBackend.NOOP;
        final org.windy.guildshelter.domain.port.CityHologramStore holoStore = storage.cityHolograms();
        if (holoBackend.available()) {
            getLogger().info("主城悬浮字: 已联动 DecentHolograms（上限 " + config.holograms().maxPerGuild() + " 个/公会）");
        }

        // 成员变更回调扇出：成员缓存 + 主城信任缓存（离会自动撤信任、解散清空）。
        final GuildMemberCache mc = memberCache;
        final org.windy.guildshelter.adapter.bukkit.CityTrustCache ctc = this.cityTrustCache;
        final org.windy.guildshelter.adapter.bukkit.CityFlagCache cfc = cityFlagCache;
        service.setMembershipListener(new org.windy.guildshelter.domain.port.MembershipChangeListener() {
            @Override public void onMemberAssigned(org.windy.guildshelter.domain.model.GuildId g, java.util.UUID p) {
                mc.onMemberAssigned(g, p); ctc.onMemberAssigned(g, p); cfc.onMemberAssigned(g, p);
            }
            @Override public void onMemberReleased(org.windy.guildshelter.domain.model.GuildId g, java.util.UUID p) {
                mc.onMemberReleased(g, p); ctc.onMemberReleased(g, p); cfc.onMemberReleased(g, p);
            }
            @Override public void onGuildDissolved(org.windy.guildshelter.domain.model.GuildId g) {
                mc.onGuildDissolved(g); ctc.onGuildDissolved(g); cfc.onGuildDissolved(g);
                // 解散：删该会全部主城悬浮字（后端 + 归属表）。
                for (var rec : holoStore.list(g)) {
                    holoBackend.remove(rec.name());
                }
                holoStore.clear(g);
            }
        });

        GsCommand command = new GsCommand(worldManager, guilds, manors, service, registry,
                config.levels(), entityCensus, this.mergeRegistry, proxyChannel, config.serverName(), getLogger(), this);
        command.setCampSpawnStore(storage.campSpawn()); // 营地成员/访客传送点
        command.setCityFlagCache(cityFlagCache); // 主城 flag（会长/副会长可设）
        command.setHolograms(holoBackend, holoStore, config.holograms().enabled(), config.holograms().maxPerGuild());
        PluginCommand gs = getCommand("gs");
        if (gs != null) {
            gs.setExecutor(command);
            gs.setTabCompleter(command);
        }

        for (GuildWorld gw : guilds.findAll()) {
            registry.register(gw);
        }

        getServer().getPluginManager().registerEvents(
                new RegionTitleListener(registry, this.worldCache, config.levels(),
                        this.cityTrustCache, guildProvider), this);

        // =========================================================================
        // 领地保护：加入极致啰嗦的启动诊断日志
        // =========================================================================
        if (getConfig().getBoolean("protection", true)) {
            this.claimGuard = new ClaimGuard(registry, new PermissionRules(), this.worldCache, this.supervisorCache,
                    memberCache, this.cityTrustCache, guildProvider, this.roadPermitCache,
                    getConfig().getBoolean("road-allow-fake-players", false),
                    new java.util.HashSet<>(getConfig().getStringList("main-city-blocked-blocks")));
            ManorLookup lookup = new ManorLookup(registry, manors, this.worldCache, cityFlagCache);
            this.manorLookup = lookup;
            this.interactionPolicy = new InteractionPolicy(claimGuard, lookup);

            getLogger().info("========== [启动诊断] 开始装配保护模块 ==========");
            // 接缝：混合端注册原生 NeoForge 保护并返回 true → 跳过下面三个 Bukkit 监听；纯 Bukkit 返回 false。
            boolean nativeProtection = bindings.registerNativeProtection(this);
            if (nativeProtection) {
                getLogger().info("[启动诊断] 混合端：已注册原生保护，跳过 Bukkit ManorProtection/Entity/Flag 监听。");
            } else {
                getServer().getPluginManager().registerEvents(new ManorProtectionListener(claimGuard, interactionPolicy), this);
                getServer().getPluginManager().registerEvents(new ManorEntityListener(interactionPolicy), this);
                getServer().getPluginManager().registerEvents(new ManorFlagListener(lookup), this);
                getLogger().info("[启动诊断] 纯 Bukkit：已注册 ManorProtection/Entity/Flag 监听。");
            }
            getLogger().info("========== [启动诊断] 保护模块装配结束 ==========");

            getServer().getPluginManager().registerEvents(new ManorFireListener(lookup), this);
            getServer().getPluginManager().registerEvents(new ManorEnvListener(lookup), this);
            getServer().getPluginManager().registerEvents(new ManorPlayerListener(lookup), this);
            getServer().getPluginManager().registerEvents(new ManorCapListener(lookup, entityCensus), this);

            EconomyPort economy = VaultEconomy.tryCreate(getLogger());
            VisitCounter visitCounter = new VisitCounter(manors, getLogger());
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override public void run() { visitCounter.flush(); }
            }.runTaskTimer(this, 60L * 20, 60L * 20);

            ManorAccessListener accessListener = new ManorAccessListener(lookup, economy, visitCounter, this.worldCache);
            getServer().getPluginManager().registerEvents(accessListener, this);
            command.setAccessListener(accessListener);
            accessListener.setOpenPlots(command.openPlots());

            getServer().getPluginManager().registerEvents(new ManorCommandListener(lookup), this);
            getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                    UUID id = event.getPlayer().getUniqueId();
                    claimGuard.onPlayerQuit(id);
                    supervisorCache.clearAll();
                    worldCache.removePlayerRef(id);
                }
            }, this);
            new ManorBuffTask(lookup).runTaskTimer(this, 20L, 20L);
            getLogger().info("庄园 Flag 执行已启用（访问/增益走 Bukkit，氛围按载体分流）。");
            new ManorParticleTask(lookup, registry, guilds).runTaskTimer(this, 10L, 10L);
            getServer().getPluginManager().registerEvents(new GuildMotdListener(registry, guilds), this);
        } else {
            getLogger().info("领地保护已禁用。");
        }

        // 接公会插件
        record GuildHook(String pluginName, Supplier<Listener> factory) {}
        List<GuildHook> hooks = List.of(
                new GuildHook("PlayerGuild",
                        () -> new PlayerGuildListener(service, guilds, registry, getLogger(), this)),
                new GuildHook("LegendaryGuildRemapped",
                        () -> new LegendaryGuildListener(service, guilds, registry, getLogger(), this)));

        boolean hooked = false;
        for (GuildHook hook : hooks) {
            if (getServer().getPluginManager().getPlugin(hook.pluginName()) != null) {
                getServer().getPluginManager().registerEvents(hook.factory().get(), this);
                getLogger().info("已挂接 " + hook.pluginName() + " 事件。");
                hooked = true;
            }
        }


        if (getServer().getPluginManager().getPlugin("Shetuan") != null) {
            ShetuanAccess access = ShetuanAccess.tryCreate(getServer().getPluginManager(), getLogger());
            if (access != null) {
                long periodTicks = Math.max(1L, getConfig().getLong("shetuan.sync-interval-seconds", 30)) * 20L;
                new ShetuanSyncTask(access, service, guilds, manors, registry, getLogger())
                        .runTaskTimer(this, 20L * 5, periodTicks);
                getLogger().info("已挂接 Shetuan(轮询同步,每 " + (periodTicks / 20L) + "s)。");
                hooked = true;
            }
        }

        if (getServer().getPluginManager().getPlugin("Guild") != null) {
            long periodTicks = Math.max(1L, getConfig().getLong("guild-plugin.sync-interval-seconds", 30)) * 20L;
            boolean disbandSweep = getConfig().getBoolean("guild-plugin.disband-sweep", true);
            GuildPluginSyncTask guildTask =
                    new GuildPluginSyncTask(service, guilds, manors, registry, getLogger(), disbandSweep);
            guildTask.runTaskTimer(this, 20L * 5, periodTicks);
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

        // ===== UI 后端（预留：插件 ↔ 模组联动）=====
        // 数据模型（UiView/UiItem/UiIcon）平台中立；易变的 Bukkit Inventory API 全部隔离在
        // BukkitInventoryUi 一个文件内。模组侧自定义 Screen = ModChannelUi（当前为桩）。
        // 注：尚无命令打开菜单，GUI 入口待接——uiRouter()/uiBackend() 即接线点。
        this.uiRouter = new UiActionRouter();
        BukkitInventoryUi bukkitUi = new BukkitInventoryUi(this.uiRouter);
        ModChannelUi modUi = new ModChannelUi(getLogger());
        String uiBackendCfg = getConfig().getString("ui.backend", "auto").toLowerCase(java.util.Locale.ROOT);
        boolean useMod = switch (uiBackendCfg) {
            case "mod" -> true;
            case "vanilla" -> false;
            default -> bindings.isHybrid() && modUi.available(); // auto：模组就绪才用，否则原版兜底
        };
        this.uiBackend = useMod ? modUi : bukkitUi;
        getServer().getPluginManager().registerEvents(new VanillaGuiListener(bukkitUi), this);
        this.guiLoader = new YamlGuiLoader(getDataFolder(), getLogger());
        getLogger().info("UI 后端: " + this.uiBackend.getClass().getSimpleName()
                + (this.uiBackend == modUi ? "（模组联动）" : "（原版 Inventory 兜底；模组联动 UI 预留中）"));

        if (getConfig().getBoolean("upkeep.enabled", false)) {
            double baseCost = getConfig().getDouble("upkeep.base-cost", 100);
            double perLevelCost = getConfig().getDouble("upkeep.per-level-cost", 50);
            long periodTicks = 20L * 60 * 20;
            new GuildUpkeepTask(guilds, registry, baseCost, perLevelCost, getLogger())
                    .runTaskTimer(this, periodTicks, periodTicks);
            getLogger().info("每日维护费已启用（基础 " + baseCost + "，每级 +" + perLevelCost + "）");
        }

        var perf = config.performance();

        // 掉落物上限：庄园等级表配了 drops，或开了主城限额，就开定时清理任务（同任务兼扫庄园+主城）。
        if (perf.quotas().isConfigured(org.windy.guildshelter.domain.rule.OptimizationLimit.DROPS)
                || config.cityLimits().enabled()) {
            long limitTicks = Math.max(1L, perf.limitCheckSeconds()) * 20L;
            new ManorLimitTask(registry, guilds, manors, entityCensus, perf.dropCleanMode(), getLogger())
                    .runTaskTimer(this, limitTicks, limitTicks);
            getLogger().info("掉落物限制: 庄园按等级 + 主城固定（" + (perf.dropCleanMode() ? "清理最旧+拦截" : "只拦截") + "）");
        }

        WorldOptimizer worldOptimizer = null;
        if (perf.optimizeEnabled()) {
            worldOptimizer = new WorldOptimizer(registry, guilds, perf.optimizeMode(),
                    perf.optimizeInactiveMinutes(), perf.keepSpawnLoaded(), getLogger());
            long optTicks = Math.max(1L, perf.optimizeCheckSeconds()) * 20L;
            worldOptimizer.runTaskTimer(this, optTicks, optTicks);
            getLogger().info("世界级优化: " + perf.optimizeMode() + "（" + perf.optimizeInactiveMinutes() + "分钟无玩家卸载）");
        }

        if (perf.statsEnabled()) {
            long statsTicks = Math.max(1L, perf.statsBroadcastSeconds()) * 20L;
            new PerformanceBroadcastTask(registry, guilds, manors, entityCensus,
                    perf.statsTopCount(), perf.weightTileTick(), perf.weightEntityTick(),
                    perf.weightDropTick(), perf.weightChunkTick(), getLogger())
                    .runTaskTimer(this, statsTicks, statsTicks);
            getLogger().info("性能排行广播: 每 " + perf.statsBroadcastSeconds() + " 秒, Top " + perf.statsTopCount());
        }

        ManorChunkManager chunkManager = null;
        if (perf.chunkUnloadEnabled()) {
            chunkManager = new ManorChunkManager(registry, guilds, manors,
                    perf.chunkUnloadInactiveMinutes(), perf.chunkUnloadKeepRoad(), getLogger());
            long chunkTicks = Math.max(1L, perf.chunkUnloadCheckSeconds()) * 20L;
            chunkManager.runTaskTimer(this, chunkTicks, chunkTicks);

            WorldOptimizer finalWorldOptimizer = worldOptimizer;
            ManorChunkManager finalChunkManager = chunkManager;
            getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                    finalChunkManager.onPlayerJoin(event.getPlayer().getUniqueId());
                    if (finalWorldOptimizer != null) {
                        for (GuildWorld gw : guilds.findAll()) {
                            finalWorldOptimizer.onPlayerEnter(gw.worldName());
                        }
                    }
                }
            }, this);
            getLogger().info("庄园区块卸载: " + perf.chunkUnloadInactiveMinutes() + " 分钟无上级在线卸载");
        }

        SchematicStore schematicStore = SchematicStores.autoDetect(getDataFolder().toPath(), this);
        if (schematicStore != null) {
            command.setSchematicStore(schematicStore);
            getLogger().info("Schematic 模板已启用（" + schematicStore.getClass().getSimpleName() + "）");
        } else {
            getLogger().info("未检测到 WorldEdit/FAWE，Schematic 模板未启用。");
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new GuildShelterPapi(manors, guilds, config.levels()).register();
            getLogger().info("PlaceholderAPI 扩展已注册。");
        }

        XaeroIntegration xaero = new XaeroIntegration(registry, guilds, manors, this, getLogger());
        getServer().getPluginManager().registerEvents(xaero, this);

        // Xaero 世界地图圈地（服务端半，PLAN_XAERO.md Phase 1）：注册 guildshelter:map 通道，进世界下发地皮、
        // 收客户端 mod 的圈地点击并裁决。无客户端 mod 时静默（出站无监听端即丢、入站永不发生）。
        org.windy.guildshelter.adapter.bukkit.map.MapClaimChannel mapChannel =
                new org.windy.guildshelter.adapter.bukkit.map.MapClaimChannel(
                        registry, guilds, manors, service, this, getLogger());
        mapChannel.register();
        command.setMapChannel(mapChannel); // 命令解锁后刷新地图高亮

        // ===== 启动横幅（彩色 logo + 信息汇总，载体一眼可辨）=====
        String protectionDesc = !getConfig().getBoolean("protection", true) ? "已禁用"
                : (bindings.isHybrid() ? "原生 NeoForge 事件" : "Bukkit 监听器");
        String schematicDesc = schematicStore != null
                ? schematicStore.getClass().getSimpleName() : "未启用（无 WorldEdit/FAWE）";
        // 运行时统计：公会数（= 已注册公会世界）+ 庄园总数。
        List<GuildWorld> allGuilds = guilds.findAll();
        int manorCount = 0;
        for (GuildWorld gw : allGuilds) {
            manorCount += manors.findAll(gw.guild()).size();
        }
        String statsDesc = allGuilds.size() + " 个公会 · " + manorCount + " 个庄园";
        getServer().getConsoleSender().sendMessage(Texts.startupBanner(
                bindings.isHybrid(),
                getDescription().getVersion(),
                bindings.carrierName(),
                config.storage().type(),
                protectionDesc,
                guildSource,
                schematicDesc,
                this.uiBackend.getClass().getSimpleName(),
                statsDesc));
    }

    /**
     * 加载等级系统配置 levels.yml：首启时释放默认文件，并把旧 config.yml 里的等级配置（如有）迁移过来，
     * 避免老服升级后等级被悄悄重置。返回可读的 FileConfiguration。
     */
    private org.bukkit.configuration.file.FileConfiguration loadLevelsConfig() {
        java.io.File file = new java.io.File(getDataFolder(), "levels.yml");
        boolean firstRun = !file.exists();
        if (firstRun) {
            saveResource("levels.yml", false);
        }
        org.bukkit.configuration.file.FileConfiguration lv =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        if (firstRun) {
            org.bukkit.configuration.file.FileConfiguration old = getConfig();
            boolean migrated = false;
            // 旧 member-plot.* → manor.*
            migrated |= copyInt(old, "member-plot.max-level", lv, "manor.max-level");
            migrated |= copyInt(old, "member-plot.initial-chunks", lv, "manor.initial-chunks");
            migrated |= copyInt(old, "member-plot.max-chunks", lv, "manor.max-chunks");
            migrated |= copyInt(old, "member-plot.grow-per-level", lv, "manor.grow-per-level");
            // 旧 guild.* → guild.*（键名不变）
            migrated |= copyInt(old, "guild.max-level", lv, "guild.max-level");
            migrated |= copyInt(old, "guild.members-per-level", lv, "guild.members-per-level");
            // 旧 main-city.* → guild.main-city.*
            migrated |= copyInt(old, "main-city.initial-chunks", lv, "guild.main-city.initial-chunks");
            migrated |= copyInt(old, "main-city.max-chunks", lv, "guild.main-city.max-chunks");
            if (migrated) {
                try {
                    lv.save(file);
                    getLogger().info("已把旧 config.yml 的等级配置迁移到 levels.yml。");
                } catch (java.io.IOException e) {
                    getLogger().warning("迁移 levels.yml 失败: " + e.getMessage());
                }
            }
        }
        return lv;
    }

    /** 若源有该键则复制到目标。返回是否复制了。 */
    private static boolean copyInt(org.bukkit.configuration.file.FileConfiguration src, String srcKey,
                                   org.bukkit.configuration.file.FileConfiguration dst, String dstKey) {
        if (src.contains(srcKey)) {
            dst.set(dstKey, src.getInt(srcKey));
            return true;
        }
        return false;
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