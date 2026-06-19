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
import org.windy.guildshelter.adapter.bukkit.gui.GuiRegistry;
import org.windy.guildshelter.adapter.bukkit.gui.VanillaGuiListener;
import org.windy.guildshelter.adapter.bukkit.gui.VanillaGuiProvider;
import org.windy.guildshelter.adapter.bukkit.gui.YamlGuiLoader;
import org.windy.guildshelter.adapter.bukkit.XaeroIntegration;
import org.windy.guildshelter.adapter.bukkit.ManorLimitTask;
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
import org.windy.guildshelter.neoforge.NeoForgeHooks;
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
    private MergeRegistry mergeRegistry;
    private WorldCache worldCache;
    private SupervisorCache supervisorCache;
    private org.windy.guildshelter.adapter.bukkit.CityTrustCache cityTrustCache;
    private YamlGuiLoader guiLoader;

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

    /** 合并缓存注册表（provider 解散公会时需清理缓存）。 */
    public static MergeRegistry mergeRegistry() {
        return instance == null ? null : instance.mergeRegistry;
    }

    /** 主城信任缓存（命令授权、provider 解散/退会时清理）。onEnable 前为 null。 */
    public static org.windy.guildshelter.adapter.bukkit.CityTrustCache cityTrustCache() {
        return instance == null ? null : instance.cityTrustCache;
    }

    /** YAML GUI 加载器（从 gui.yml 读菜单定义）。 */
    public static YamlGuiLoader guiLoader() {
        return instance == null ? null : instance.guiLoader;
    }

    /** 给新分配地皮的玩家发欢迎消息。 */
    public static void sendWelcome(Player player, String guildName, int slot) {
        if (instance == null) return;
        player.sendMessage(Messages.get("success.welcome", guildName, slot));
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

    /** 检测 NeoForge mod 是否已加载（通过 ModList）。Bukkit 插件用 getPlugin 检测即可。 */
    private static boolean isModLoaded(String modId) {
        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList");
            Object modList = modListClass.getMethod("get").invoke(null);
            return (boolean) modListClass.getMethod("isLoaded", String.class).invoke(modList, modId);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        getServer().getConsoleSender().sendMessage(Texts.logo);

        getDataFolder().mkdirs();
        saveDefaultConfig();
        // 加载语言文件
        Messages.load(getConfig().getString("language", "zh_CN"), getDataFolder());

        GuildShelterConfig config = GuildShelterConfig.from(getConfig());

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
        // 整地按载体分流
        TerrainPreparer terrain;
        String roadBlock = getConfig().getString("road-surface-block", "minecraft:dirt_path");
        String bridgeBlock = getConfig().getString("road-bridge-block", "auto");
        String bridgeRail = getConfig().getString("road-bridge-rail-block", "auto");
        var wall = config.cityWall();
        if (isNeoForgePresent()) {
            terrain = new org.windy.guildshelter.neoforge.NeoForgeTerrainPreparer(this, roadBlock, bridgeBlock, bridgeRail,
                    wall.enabled(), wall.block(), wall.height());
            getLogger().info("整地：NeoForge 原生端（混合端）。");
        } else {
            terrain = new BukkitTerrainPreparer(this, roadBlock, bridgeBlock, bridgeRail,
                    wall.enabled(), wall.block(), wall.height());
        }

        GuildService service = new GuildService(guilds, manors, worldManager, terrain,
                config.layout(), config.levels(), config.terrainPrep(), getLogger());

        // 搬家系统
        var moveConfig = config.move();
        if (moveConfig.enabled()) {
            ManorMover mover;
            if (isNeoForgePresent()) {
                mover = new org.windy.guildshelter.neoforge.NeoForgeManorMover(this, getLogger());
                getLogger().info("搬家系统: NeoForge 原生端（chunk 级复制）。");
            } else {
                mover = new org.windy.guildshelter.adapter.bukkit.BukkitManorMover(this, getLogger());
                getLogger().info("搬家系统: Bukkit/WorldEdit（clipboard 复制）。");
            }
            ModDataMoverRegistry modDataMovers = new ModDataMoverRegistry();
            if (isModLoaded("refinedstorage")) {
                modDataMovers.register(new org.windy.guildshelter.adapter.bukkit.moddata.RefinedStorageDataMover(this, getLogger()));
                getLogger().info("搬家系统: 已注册 Refined Storage 数据搬运。");
            }

            EconomyPort moveEconomy = VaultEconomy.tryCreate(getLogger());
            java.nio.file.Path worldContainer = getServer().getWorldContainer().toPath();
            service.setManorMover(mover, moveEconomy, moveConfig.cost(), moveConfig.cooldownDays(),
                    modDataMovers, worldContainer);
        }

        GuildWorldRegistry registry = new GuildWorldRegistry();
        this.entityCensus = new ManorEntityCensus(registry);

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
        if (getServer().getPluginManager().getPlugin("PlayerGuild") != null) {
            guildProvider = new org.windy.guildshelter.adapter.provider.PlayerGuildProvider();
            getLogger().info("宿主能力来源: PlayerGuild（会长角色 + 人数上限）。");
        } else if (getServer().getPluginManager().getPlugin("LegendaryGuildRemapped") != null) {
            guildProvider = new org.windy.guildshelter.adapter.provider.LegendaryGuildProvider();
            getLogger().info("宿主能力来源: LegendaryGuild（会长 owner + 人数上限）。");
        } else {
            guildProvider = org.windy.guildshelter.domain.port.GuildProvider.NONE;
        }
        service.setGuildProvider(guildProvider);
        this.worldManager.setGuildProvider(guildProvider);

        // 主城信任缓存（会长额外信任的会内成员可建主城）；启动全量加载。
        this.cityTrustCache = new org.windy.guildshelter.adapter.bukkit.CityTrustCache(
                storage.cityTrust(), guilds.findAll());

        // 成员变更回调扇出：成员缓存 + 主城信任缓存（离会自动撤信任、解散清空）。
        final GuildMemberCache mc = memberCache;
        final org.windy.guildshelter.adapter.bukkit.CityTrustCache ctc = this.cityTrustCache;
        service.setMembershipListener(new org.windy.guildshelter.domain.port.MembershipChangeListener() {
            @Override public void onMemberAssigned(org.windy.guildshelter.domain.model.GuildId g, java.util.UUID p) {
                mc.onMemberAssigned(g, p); ctc.onMemberAssigned(g, p);
            }
            @Override public void onMemberReleased(org.windy.guildshelter.domain.model.GuildId g, java.util.UUID p) {
                mc.onMemberReleased(g, p); ctc.onMemberReleased(g, p);
            }
            @Override public void onGuildDissolved(org.windy.guildshelter.domain.model.GuildId g) {
                mc.onGuildDissolved(g); ctc.onGuildDissolved(g);
            }
        });

        GsCommand command = new GsCommand(worldManager, guilds, manors, service, registry,
                config.levels(), entityCensus, this.mergeRegistry, proxyChannel, config.serverName(), getLogger(), this);
        PluginCommand gs = getCommand("gs");
        if (gs != null) {
            gs.setExecutor(command);
            gs.setTabCompleter(command);
        }

        for (GuildWorld gw : guilds.findAll()) {
            registry.register(gw);
        }

        getServer().getPluginManager().registerEvents(
                new RegionTitleListener(registry, this.worldCache, config.levels()), this);

        // =========================================================================
        // 领地保护：加入极致啰嗦的启动诊断日志
        // =========================================================================
        if (getConfig().getBoolean("protection", true)) {
            this.claimGuard = new ClaimGuard(registry, new PermissionRules(), this.worldCache, this.supervisorCache,
                    memberCache, this.cityTrustCache, guildProvider);
            ManorLookup lookup = new ManorLookup(registry, manors, this.worldCache);
            this.manorLookup = lookup;
            this.interactionPolicy = new InteractionPolicy(claimGuard, lookup);

            getLogger().info("========== [启动诊断] 开始装配保护模块 ==========");
            boolean isForgeEnv = isNeoForgePresent();
            getLogger().info("[启动诊断] isNeoForgePresent() 检测结果: " + isForgeEnv);

            if (isForgeEnv) {
                getLogger().info("[启动诊断] 判定为【混合端】环境！准备加载 NeoForgeHooks...");
                NeoForgeHooks.register();
                getLogger().info("[启动诊断] NeoForgeHooks.register() 执行完毕。");
                getLogger().info("[启动诊断] >>> 已经彻底跳过 Bukkit 的 ManorProtectionListener 注册！ <<<");
            } else {
                getLogger().info("[启动诊断] 判定为【纯 Bukkit】环境！准备注册 Bukkit 监听器...");
                getServer().getPluginManager().registerEvents(new ManorProtectionListener(claimGuard, interactionPolicy), this);
                getServer().getPluginManager().registerEvents(new ManorEntityListener(interactionPolicy), this);
                getLogger().info("[启动诊断] Bukkit 监听器 ManorProtectionListener 与 ManorEntityListener 注册完毕。");
            }

            if (isForgeEnv) {
                getLogger().info("[启动诊断] Flag 氛围类：判定为混合端，跳过 Bukkit 的 ManorFlagListener 注册。");
            } else {
                getServer().getPluginManager().registerEvents(new ManorFlagListener(lookup), this);
                getLogger().info("[启动诊断] Flag 氛围类：已注册 Bukkit 的 ManorFlagListener。");
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
            getLogger().info("地皮 Flag 执行已启用（访问/增益走 Bukkit，氛围按载体分流）。");
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

        VanillaGuiProvider vanillaGui = new VanillaGuiProvider();
        GuiRegistry guiRegistry = new GuiRegistry(vanillaGui);
        getServer().getPluginManager().registerEvents(new VanillaGuiListener(vanillaGui), this);
        this.guiLoader = new YamlGuiLoader(getDataFolder(), getLogger());

        if (getConfig().getBoolean("upkeep.enabled", false)) {
            double baseCost = getConfig().getDouble("upkeep.base-cost", 100);
            double perLevelCost = getConfig().getDouble("upkeep.per-level-cost", 50);
            long periodTicks = 20L * 60 * 20;
            new GuildUpkeepTask(guilds, registry, baseCost, perLevelCost, getLogger())
                    .runTaskTimer(this, periodTicks, periodTicks);
            getLogger().info("每日维护费已启用（基础 " + baseCost + "，每级 +" + perLevelCost + "）");
        }

        var perf = config.performance();

        if (perf.maxDroppedItems() > 0) {
            long limitTicks = Math.max(1L, perf.limitCheckSeconds()) * 20L;
            new ManorLimitTask(registry, guilds, manors, perf.maxDroppedItems(), perf.dropCleanMode(), getLogger())
                    .runTaskTimer(this, limitTicks, limitTicks);
            getLogger().info("掉落物限制: " + perf.maxDroppedItems() + "（" + (perf.dropCleanMode() ? "清理最旧" : "只拦截") + "）");
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
            getLogger().info("地皮区块卸载: " + perf.chunkUnloadInactiveMinutes() + " 分钟无上级在线卸载");
        }

        SchematicStore schematicStore = SchematicStore.autoDetect(getDataFolder().toPath(), this);
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