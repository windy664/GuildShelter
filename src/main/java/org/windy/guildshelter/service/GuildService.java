package org.windy.guildshelter.service;

import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.layout.LayoutConfig;
import org.windy.guildshelter.domain.layout.RoadMask;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.model.TerrainPrepMode;
import org.windy.guildshelter.domain.port.EconomyPort;
import org.windy.guildshelter.domain.port.GuildProvider;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorMover;
import org.windy.guildshelter.domain.port.ManorRepository;
import org.windy.guildshelter.domain.port.ModDataMoverRegistry;
import org.windy.guildshelter.domain.port.TerrainPreparer;
import org.windy.guildshelter.domain.port.WorldControl;
import org.windy.guildshelter.domain.rule.LevelRules;
import org.windy.guildshelter.domain.port.MembershipChangeListener;

import java.util.Optional;

/**
 * 应用服务：协调建会 / 成员加入分配 slot+整地 / 退出释放 / 升级。纯 Java，依赖端口，可脱机测。
 *
 * <p>这是"本插件作为附属品"的核心——上层（LegendaryGuild provider 或 admin 命令）只调这里的方法，
 * 不关心世界/网格/整地细节。layout 区域换算到世界坐标用 {@link GuildWorld} 的 origin 偏移。
 */
public final class GuildService {

    private final GuildRepository guilds;
    private final ManorRepository manors;
    private final WorldControl worlds;
    private final TerrainPreparer terrain;
    private final java.util.logging.Logger logger;
    /** 仅用于给<b>新建</b>世界盖章的当前 config 布局；已存在世界一律用各自冻结的 {@code gw.layout()}。 */
    private final LayoutConfig currentLayout;
    private final LevelRules levels;
    private final TerrainPrepMode prepMode;
    private volatile MembershipChangeListener membershipListener; // 可选，延迟注入
    private volatile GuildProvider guildProvider = GuildProvider.NONE; // 宿主角色/容量来源，延迟注入
    private volatile org.windy.guildshelter.domain.port.ManorUpgradeHook upgradeHook; // 升级扩展回调，可选

    // 搬家相关（可选，null = 搬家未启用）
    private ManorMover manorMover;
    private EconomyPort economy;
    private double moveCost;
    private int moveCooldownDays;
    private ModDataMoverRegistry modDataMovers;
    private java.nio.file.Path worldContainer; // Bukkit worlds 根目录
    private java.util.List<String> lastMoveModResults = java.util.List.of(); // 最近一次搬家的 mod 数据结果

    public GuildService(GuildRepository guilds, ManorRepository manors, WorldControl worlds,
                        TerrainPreparer terrain, LayoutConfig currentLayout, LevelRules levels,
                        TerrainPrepMode prepMode) {
        this(guilds, manors, worlds, terrain, currentLayout, levels, prepMode,
                java.util.logging.Logger.getLogger("GuildShelter"));
    }

    public GuildService(GuildRepository guilds, ManorRepository manors, WorldControl worlds,
                        TerrainPreparer terrain, LayoutConfig currentLayout, LevelRules levels,
                        TerrainPrepMode prepMode, java.util.logging.Logger logger) {
        this.guilds = guilds;
        this.manors = manors;
        this.worlds = worlds;
        this.terrain = terrain;
        this.currentLayout = currentLayout;
        this.levels = levels;
        this.prepMode = prepMode;
        this.logger = logger;
    }

    /** 注入搬家依赖（config 启用时调用）。 */
    public void setManorMover(ManorMover mover, EconomyPort economy, double cost, int cooldownDays,
                              ModDataMoverRegistry modDataMovers, java.nio.file.Path worldContainer) {
        this.manorMover = mover;
        this.economy = economy;
        this.moveCost = cost;
        this.moveCooldownDays = cooldownDays;
        this.modDataMovers = modDataMovers;
        this.worldContainer = worldContainer;
    }

    /** 搬家是否已启用。 */
    public boolean isMoveEnabled() {
        return manorMover != null;
    }

    public double moveCost() { return moveCost; }
    public int moveCooldownDays() { return moveCooldownDays; }
    public ManorMover getManorMover() { return manorMover; }
    public java.util.List<String> getLastMoveModResults() { return lastMoveModResults; }

    /** 注册成员变更回调（Bukkit 适配层注入，供缓存同步用）。 */
    public void setMembershipListener(MembershipChangeListener listener) {
        this.membershipListener = listener;
    }

    /** 注入宿主公会 provider（角色/人数上限来源）；未注入时为 {@link GuildProvider#NONE}。 */
    public void setGuildProvider(GuildProvider provider) {
        this.guildProvider = provider != null ? provider : GuildProvider.NONE;
    }

    /** 该玩家是否为该公会会长/管理（会长 + 副会长/官员），供主城信任命令授权用。 */
    public boolean isGuildAdmin(PlayerRef player, GuildId guild) {
        return guildProvider.isGuildAdmin(player, guild);
    }

    /** 注入庄园升级扩展回调（buff/加成等玩法挂这里）；未注入则升级不触发额外效果。 */
    public void setUpgradeHook(org.windy.guildshelter.domain.port.ManorUpgradeHook hook) {
        this.upgradeHook = hook;
    }

    /**
     * 该公会当前<b>有效名额容量</b>（发多少庄园 slot）：宿主插件有公会人数上限就跟宿主，
     * 否则退回 GuildShelter 自己的等级容量（等级 × members-per-level）。
     */
    public int effectiveCapacity(GuildWorld gw) {
        return guildProvider.memberCap(gw.guild()).orElseGet(() -> levels.maxMembers(gw.guildLevel()));
    }

    /** 建会：创建（或返回已有的）公会营地，按<b>当前 config</b> 冻结其布局参数。 */
    public GuildWorld createGuild(GuildId guild, long seed) {
        return createGuild(guild, seed, prepMode);
    }

    /** 建会（指定地形模式）：允许创建时选择 VOID/FLAT/NONE 等不同地形。 */
    public GuildWorld createGuild(GuildId guild, long seed, TerrainPrepMode terrainMode) {
        return createGuild(guild, seed, terrainMode, "");
    }

    /** 建会（指定地形模式+服务器名）：跨服模式下标记世界所在服务器。 */
    public GuildWorld createGuild(GuildId guild, long seed, TerrainPrepMode terrainMode, String serverName) {
        Optional<GuildWorld> existing = guilds.find(guild);
        if (existing.isPresent()) {
            return existing.get();
        }
        GuildWorld gw = GuildWorld.create(guild, worlds.worldName(guild), seed, currentLayout, terrainMode, serverName);
        gw = worlds.ensureWorld(gw);
        gw = gw.withCityUnlockedChunks(initialCityUnlocked(gw)); // 初始解锁主城角落正方形（会长起点）
        guilds.save(gw);
        prepareMainCityRoads(gw); // 建会即给主城铺一圈环路（主城非成员 slot，否则四周路会"被吞"）
        buildCityWall(gw);        // 围墙（默认关）
        return gw;
    }

    /** 主城建会初始解锁的角落正方形（{@code initialCityUnlockSide²} 个 chunk，cell0 内部偏移）。 */
    private java.util.Set<Integer> initialCityUnlocked(GuildWorld gw) {
        int side = gw.layout().initialCityUnlockSide();
        java.util.Set<Integer> set = new java.util.HashSet<>();
        for (int dx = 0; dx < side; dx++) {
            for (int dz = 0; dz < side; dz++) {
                set.add(Manor.packOffset(dx, dz));
            }
        }
        return set;
    }

    /**
     * 会长用额度解锁主城 chunk（世界坐标）。校验：必须是主城格、未解锁、有剩余额度、与已解锁<b>相邻</b>。
     * 通过则加入主城解锁集合、存库、整这一格。返回更新后的 {@link GuildWorld}（调用方需更新 registry），
     * 失败返回 {@code null}（具体原因经 {@link #lastCityUnlockResult}）。
     */
    public GuildWorld unlockCityChunk(GuildId guild, int worldChunkX, int worldChunkZ) {
        GuildWorld gw = guilds.find(guild).orElse(null);
        if (gw == null) {
            lastCityUnlockResult = UnlockResult.NO_MANOR;
            return null;
        }
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        int lx = worldChunkX - gw.originChunkX();
        int lz = worldChunkZ - gw.originChunkZ();
        if (!layout.classify(lx, lz).isMainCity()) {
            lastCityUnlockResult = UnlockResult.NOT_YOUR_PLOT; // 不在主城范围
            return null;
        }
        int dx = lx, dz = lz; // 主城锚在 cell0 原点 (0,0)，内部偏移即 lx/lz
        if (gw.isCityUnlocked(dx, dz)) {
            lastCityUnlockResult = UnlockResult.ALREADY_UNLOCKED;
            return null;
        }
        if (gw.cityUnlockedChunks().size() >= gw.cityQuotaCap(levels.maxGuildLevel())) {
            lastCityUnlockResult = UnlockResult.NO_QUOTA;
            return null;
        }
        if (!(gw.isCityUnlocked(dx - 1, dz) || gw.isCityUnlocked(dx + 1, dz)
                || gw.isCityUnlocked(dx, dz - 1) || gw.isCityUnlocked(dx, dz + 1))) {
            lastCityUnlockResult = UnlockResult.NOT_ADJACENT;
            return null;
        }
        java.util.Set<Integer> nu = new java.util.HashSet<>(gw.cityUnlockedChunks());
        nu.add(Manor.packOffset(dx, dz));
        GuildWorld updated = gw.withCityUnlockedChunks(nu);
        guilds.save(updated);
        if (prepMode != TerrainPrepMode.NONE) {
            terrain.prepare(gw.worldName(),
                    new ChunkRegion(worldChunkX, worldChunkZ, worldChunkX, worldChunkZ), prepMode, true);
        }
        lastCityUnlockResult = UnlockResult.SUCCESS;
        return updated;
    }

    private volatile UnlockResult lastCityUnlockResult = UnlockResult.SUCCESS;

    /** 上次 {@link #unlockCityChunk} 的结果（命令层据此给玩家提示）。 */
    public UnlockResult lastCityUnlockResult() {
        return lastCityUnlockResult;
    }

    /** 主城剩余可解锁额度（额度上限 − 已解锁数）。 */
    public int cityRemainingQuota(GuildWorld gw) {
        return Math.max(0, gw.cityQuotaCap(levels.maxGuildLevel()) - gw.cityUnlockedChunks().size());
    }

    /**
     * 管理员<b>设置/增加</b>某公会主城的解锁额度上限（定量 set / 增量 add）。封顶主城 chunk 上限
     * {@code mainCityMaxChunks²}，下限 0。存进 {@link GuildWorld#cityQuotaOverride}，与公会等级<b>分离</b>。
     *
     * @return 设置后的新额度上限并已更新 registry；公会不存在返回 -1
     */
    public int setCityQuota(GuildId guild, int amount, boolean add, GuildWorldRegistryUpdater registryUpdater) {
        GuildWorld gw = guilds.find(guild).orElse(null);
        if (gw == null) {
            return -1;
        }
        int cap = gw.layout().mainCityMaxChunks() * gw.layout().mainCityMaxChunks();
        int base = add ? gw.cityQuotaCap(levels.maxGuildLevel()) : 0;
        int newQuota = Math.min(Math.max(0, base + amount), cap);
        GuildWorld updated = gw.withCityQuotaOverride(newQuota);
        guilds.save(updated);
        if (registryUpdater != null) {
            registryUpdater.update(updated);
        }
        return newQuota;
    }

    /** 让命令层把更新后的 GuildWorld 写回内存 registry（GuildService 不直接依赖 Bukkit registry）。 */
    @FunctionalInterface
    public interface GuildWorldRegistryUpdater {
        void update(GuildWorld gw);
    }

    /**
     * 给主城铺<b>四周环路</b>：主城不是成员 slot，{@link #prepareManorTerrain} 的成员路网不覆盖它，
     * 故单独铺 {@link LayoutCalculator#mainCityRoadStrips()} 这圈，让主城建会即被路环绕、与成员路网拼接。
     * 建会时调用一次（幂等：重复铺同一圈无副作用）。
     */
    public void prepareMainCityRoads(GuildWorld gw) {
        if (prepMode == TerrainPrepMode.NONE || gw.terrainMode() == TerrainPrepMode.VOID) {
            return;
        }
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        RoadMask roadMask = layout.roadMask(gw.originChunkX(), gw.originChunkZ());
        for (ChunkRegion strip : layout.mainCityRoadStrips()) {
            terrain.surfaceRoad(gw.worldName(), strip.shift(gw.originChunkX(), gw.originChunkZ()), roadMask);
        }
    }

    /**
     * 沿<b>最大主城</b>外缘建围墙：建会时自动、{@code /gs admin citywall} 手动重建。世界须已加载。
     * 只在外侧是成员庄园（非路）的主城边块上立墙——贴道路的边自动留口、永不踩成员庄园。
     */
    public void buildCityWall(GuildWorld gw) {
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        ChunkRegion maxCity = layout.mainCityRegion().shift(gw.originChunkX(), gw.originChunkZ());
        RoadMask roadMask = layout.roadMask(gw.originChunkX(), gw.originChunkZ());
        terrain.encloseMainCity(gw.worldName(), maxCity, roadMask);
    }

    /**
     * 成员加入：分配下一个螺旋 slot、创建庄园、扩边界、整地。幂等——已有庄园则原样返回。
     *
     * @return 该成员的庄园（含 slot）
     */
    public Manor assignManor(GuildId guild, PlayerRef player) {
        GuildWorld gw = guilds.find(guild).orElseThrow(
                () -> new IllegalStateException("公会营地不存在: " + guild.value()));

        Optional<Manor> owned = manors.findByOwner(guild, player);
        if (owned.isPresent()) {
            return owned.get();
        }

        // 容量门控：公会等级决定名额数，名额满了不能再分新庄园（需先升级公会）。
        // 成员 slot 从 0 起紧凑铺，前 capacity 个为合法名额；nextFreeSlot 会复用退会留下的空缺，
        // 故"下一个空缺 ≥ capacity"即等价于"在册成员已达 capacity"。
        int slot = manors.nextFreeSlot(guild);
        int capacity = effectiveCapacity(gw); // 宿主有人数上限就跟宿主，否则用我们的等级容量
        if (slot >= capacity) {
            throw new GuildFullException(guild, capacity, gw.guildLevel());
        }
        // 初始解锁角落的初始正方形（initialUnlockSide² 个 chunk），给新成员一块整好的起点。
        Manor manor = Manor.create(slot, guild, player).withUnlockedChunks(initialUnlocked(gw));
        manors.save(manor);

        int newAllocated = Math.max(gw.allocatedSlots(), slot + 1);
        if (newAllocated != gw.allocatedSlots()) {
            gw = gw.withAllocatedSlots(newAllocated);
            guilds.save(gw);
            worlds.applyBorder(gw); // 边界随分配扩
        }

        prepareManorTerrain(gw, manor, false); // 异步整地（默认行为，claim 会额外补一次同步整地）
        MembershipChangeListener l = membershipListener;
        if (l != null) l.onMemberAssigned(guild, player.uuid());
        return manor;
    }

    /** {@link #claimManorAt} 的结果。 */
    public enum ClaimResult {
        /** 认领成功。 */
        SUCCESS,
        /** 脚下不是可认领的地皮格（路/主城/预留外）。 */
        NOT_PLOT,
        /** 该 slot 已被占用。 */
        ALREADY_OWNED,
        /** 超出公会名额上限（需公会升级扩容）。 */
        GUILD_FULL
    }

    /**
     * 玩家<b>自助认领指定 slot</b> 的庄园（多庄园：站在空闲地皮格上认领额外一块）。
     * 与 {@link #assignManor} 的区别：不走 {@code nextFreeSlot}、不因玩家已有庄园而早退——这正是多庄园所需。
     *
     * <p><b>每玩家可拥有数</b>由命令层用权限节点校验（需在线 Player）；此处只校验
     * slot 合法 + 未被占 + 不超公会名额，并完成创建/整地/扩边界。
     */
    public ClaimResult claimManorAt(GuildId guild, PlayerRef player, int slot) {
        GuildWorld gw = guilds.find(guild).orElse(null);
        if (gw == null || slot < 0) {
            return ClaimResult.NOT_PLOT;
        }
        if (slot >= effectiveCapacity(gw)) {
            return ClaimResult.GUILD_FULL; // 该 slot 超出公会名额，需升级公会扩容
        }
        if (manors.findBySlot(guild, slot).isPresent()) {
            return ClaimResult.ALREADY_OWNED;
        }
        Manor manor = Manor.create(slot, guild, player).withUnlockedChunks(initialUnlocked(gw));
        manors.save(manor);

        int newAllocated = Math.max(gw.allocatedSlots(), slot + 1);
        if (newAllocated != gw.allocatedSlots()) {
            gw = gw.withAllocatedSlots(newAllocated);
            guilds.save(gw);
            worlds.applyBorder(gw); // 边界随分配扩
        }
        prepareManorTerrain(gw, manor, true); // 同步整地+铺路：给认领者一块整好的起点
        MembershipChangeListener l = membershipListener;
        if (l != null) l.onMemberAssigned(guild, player.uuid());
        return ClaimResult.SUCCESS;
    }

    /**
     * 成员退出：释放其在该公会的<b>全部</b>庄园 slot（多庄园：退会要清掉所有块，否则余下变孤儿泄漏）。
     * 留空缺，下次分配复用，保持螺旋紧凑。带 keep flag 的庄园跳过（保留）。
     */
    public void releaseManor(GuildId guild, PlayerRef player) {
        boolean released = false;
        for (Manor m : manors.findAllByOwner(guild, player)) {
            if (!Flag.KEEP.resolveBool(m.flags())) {
                manors.delete(guild, m.slot());
                released = true;
            }
        }
        if (released) {
            MembershipChangeListener l = membershipListener;
            if (l != null) l.onMemberReleased(guild, player.uuid());
        }
    }

    /** 成员退出（不需事先知道公会）：定位其所属公会并释放该公会内全部自有庄园。keep flag 跳过。 */
    public void releaseManorAnywhere(PlayerRef player) {
        manors.findByOwnerAnywhere(player).ifPresent(m -> releaseManor(m.guild(), player));
    }

    /** 解散公会：卸载世界、删除其全部庄园与世界记录。keep=true 的庄园也一并清除（公会都没了，保留无意义）。 */
    public void dissolveGuild(GuildId guild) {
        for (Manor m : manors.findAll(guild)) {
            manors.delete(guild, m.slot());
        }
        worlds.unloadGuild(guild);
        guilds.delete(guild);
        MembershipChangeListener l = membershipListener; // 缓存(成员/主城信任)经此回调清理
        if (l != null) l.onGuildDissolved(guild);
    }

    /**
     * 庄园升级：受公会等级封顶。升级<b>只解锁更大的可建造范围</b>（{@code activeRegion} 随等级扩大，
     * 权限判定 {@code PermissionRules} 自动放行新范围）——<b>系统不再自动整地</b>，新解锁的那圈是原始自然地形，
     * 由玩家自己开荒/建设（想要干净地基可自行 {@code /gs clear}）。这才是"自己经营自己庄园"的本意。
     */
    public boolean upgradeManor(GuildId guild, PlayerRef player) {
        // 旧签名：升该玩家在本公会的庄园（多庄园时为 findByOwner 命中的那块）。命令层多庄园已改传 slot。
        Manor manor = manors.findByOwner(guild, player).orElseThrow();
        return upgradeManor(guild, manor.slot());
    }

    /** 升级<b>指定 slot</b> 的庄园（多庄园寻址用）。slot 不存在抛 {@link java.util.NoSuchElementException}。 */
    public boolean upgradeManor(GuildId guild, int slot) {
        guilds.find(guild).orElseThrow(); // 校验世界存在
        Manor manor = manors.findBySlot(guild, slot).orElseThrow();
        if (!levels.canUpgradeManor(manor.level())) { // 庄园只受物理满级限制，与公会等级无关
            return false;
        }
        int oldLevel = manor.level();
        Manor upgraded = manor.withLevel(oldLevel + 1);
        manors.save(upgraded);
        var hook = upgradeHook; // 扩展回调：buff/加成等玩法在此挂接
        if (hook != null) {
            hook.onUpgrade(upgraded, oldLevel);
        }
        return true; // 升级只提升额度上限，不整地（玩家凭额度自由解锁）
    }

    /** 公会升级：扩主城上限 + 提升庄园上限；同步边界。 */
    public boolean upgradeGuild(GuildId guild) {
        GuildWorld gw = guilds.find(guild).orElseThrow();
        if (!levels.canUpgradeGuild(gw.guildLevel())) {
            return false;
        }
        gw = gw.withGuildLevel(gw.guildLevel() + 1);
        guilds.save(gw);
        worlds.applyBorder(gw);
        return true;
    }

    /** 清空成员当前实占范围的地表建筑（清植被式：清掉自然地面以上的方块）。供 /gs clear 用。 */
    public void clearManor(GuildId guild, PlayerRef player) {
        // 旧签名：清该玩家的庄园（多庄园时为 findByOwner 命中的那块）。命令层多庄园已改传 slot。
        Manor manor = manors.findByOwner(guild, player).orElseThrow();
        clearManor(guild, manor.slot());
    }

    /** 清空<b>指定 slot</b> 庄园的地表建筑（多庄园寻址：清脚下站的这块）。 */
    public void clearManor(GuildId guild, int slot) {
        GuildWorld gw = guilds.find(guild).orElseThrow();
        Manor manor = manors.findBySlot(guild, slot).orElseThrow();
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        ChunkRegion active = layout.activeRegion(manor.slot(), manor.level())
                .shift(gw.originChunkX(), gw.originChunkZ());
        terrain.prepare(gw.worldName(), active, TerrainPrepMode.CLEAR_VEGETATION);
    }

    /**
     * 搬家：把庄园建筑从当前公会营地复制到目标公会营地。
     * 建筑通过 chunk 级复制保留 TileEntity NBT（模组数据安全）。
     *
     * @param player 搬家的玩家
     * @param targetGuild 目标公会
     * @return 搬家结果
     */
    public MoveResult moveManor(PlayerRef player, GuildId targetGuild) {
        if (manorMover == null) {
            return MoveResult.NOT_ENABLED;
        }

        // 1. 查当前庄园
        Manor oldManor = manors.findByOwnerAnywhere(player).orElse(null);
        if (oldManor == null) {
            return MoveResult.NO_MANOR;
        }
        if (oldManor.guild().equals(targetGuild)) {
            return MoveResult.SAME_GUILD;
        }

        // 2. 冷却检查
        long lastMove = manors.getLastMoveTime(player.uuid());
        if (lastMove > 0 && moveCooldownDays > 0) {
            long cooldownMs = (long) moveCooldownDays * 24 * 60 * 60 * 1000;
            if (System.currentTimeMillis() - lastMove < cooldownMs) {
                return MoveResult.ON_COOLDOWN;
            }
        }

        // 3. 目标公会营地
        GuildWorld targetGw = guilds.find(targetGuild).orElse(null);
        if (targetGw == null) {
            return MoveResult.TARGET_NOT_EXIST;
        }

        // 4. 目标名额
        int capacity = levels.maxMembers(targetGw.guildLevel());
        int targetSlot = manors.nextFreeSlot(targetGuild);
        if (targetSlot >= capacity) {
            return MoveResult.TARGET_FULL;
        }

        // 5. 扣费
        if (moveCost > 0 && economy != null) {
            if (!economy.has(player, moveCost)) {
                return MoveResult.NOT_ENOUGH_MONEY;
            }
            economy.withdraw(player, moveCost);
        }

        // 6. 确保两个世界已加载
        GuildWorld oldGw = guilds.find(oldManor.guild()).orElse(null);
        if (oldGw == null) {
            return MoveResult.TARGET_NOT_EXIST;
        }
        oldGw = worlds.ensureWorld(oldGw);
        guilds.save(oldGw);
        targetGw = worlds.ensureWorld(targetGw);
        guilds.save(targetGw);

        // 7. 计算源/目标 chunk 区域
        // 用 plotRegion（满级范围）而非 activeRegion（当前等级范围），
        // 因为源世界和目标世界的 layout 可能不同（冻结参数），取较大者确保复制完整。
        LayoutCalculator oldLayout = new LayoutCalculator(oldGw.layout());
        LayoutCalculator newLayout = new LayoutCalculator(targetGw.layout());
        ChunkRegion srcRegion = oldLayout.plotRegion(oldManor.slot())
                .shift(oldGw.originChunkX(), oldGw.originChunkZ());
        ChunkRegion dstRegion = newLayout.plotRegion(targetSlot)
                .shift(targetGw.originChunkX(), targetGw.originChunkZ());

        // 复制范围 = 两者中较小的边长（避免越界）
        int sizeChunks = Math.min(srcRegion.widthChunks(), dstRegion.widthChunks());

        // 8. 复制建筑
        boolean copied = manorMover.copyRegion(
                oldGw.worldName(), srcRegion.minChunkX(), srcRegion.minChunkZ(), sizeChunks,
                targetGw.worldName(), dstRegion.minChunkX(), dstRegion.minChunkZ());
        if (!copied) {
            return MoveResult.COPY_FAILED;
        }

        // 8.5 搬运 mod 世界数据（.dat 文件，如 RS2 的存储盘数据）
        if (modDataMovers != null && worldContainer != null) {
            java.nio.file.Path srcDir = worldContainer.resolve(oldGw.worldName());
            java.nio.file.Path dstDir = worldContainer.resolve(targetGw.worldName());
            java.util.List<String> modResults = modDataMovers.moveAll(srcDir, dstDir,
                    oldGw.worldName(), targetGw.worldName(),
                    srcRegion.minChunkX(), srcRegion.minChunkZ(),
                    srcRegion.maxChunkX(), srcRegion.maxChunkZ());
            this.lastMoveModResults = modResults;
            for (String r : modResults) {
                logger.info("[GuildShelter] mod 数据: " + r.replaceAll("§[0-9a-fk-or]", ""));
            }
        } else {
            this.lastMoveModResults = java.util.List.of();
        }

        // 9. 清空旧位置
        manorMover.clearRegion(oldGw.worldName(),
                srcRegion.minChunkX(), srcRegion.minChunkZ(),
                srcRegion.maxChunkX(), srcRegion.maxChunkZ());

        // 10. 更新 DB：旧 manor 删 → 新 manor 保留数据（含已解锁 chunk 集合：解锁形状随搬家保留）
        manors.delete(oldManor.guild(), oldManor.slot());
        Manor newManor = new Manor(targetSlot, targetGuild, player, oldManor.level(),
                oldManor.coBuilders(), oldManor.members(), oldManor.denied(), oldManor.flags(),
                oldManor.unlockedChunks());
        manors.save(newManor);

        // 11. 更新目标世界 allocatedSlots + 边界
        int newAllocated = Math.max(targetGw.allocatedSlots(), targetSlot + 1);
        if (newAllocated != targetGw.allocatedSlots()) {
            targetGw = targetGw.withAllocatedSlots(newAllocated);
            guilds.save(targetGw);
            worlds.applyBorder(targetGw);
        }

        // 12. 记录搬家时间
        manors.recordMove(player.uuid(), System.currentTimeMillis());

        return MoveResult.SUCCESS;
    }

    /** 搬家结果枚举。 */
    public enum MoveResult {
        SUCCESS,
        NOT_ENABLED,
        NO_MANOR,
        SAME_GUILD,
        ON_COOLDOWN,
        TARGET_NOT_EXIST,
        TARGET_FULL,
        NOT_ENOUGH_MONEY,
        COPY_FAILED
    }

    /** 庄园分配时初始解锁的角落正方形（{@code initialUnlockSide²} 个 chunk 的内部偏移）。 */
    private java.util.Set<Integer> initialUnlocked(GuildWorld gw) {
        int side = gw.layout().initialUnlockSide();
        java.util.Set<Integer> set = new java.util.HashSet<>();
        for (int dx = 0; dx < side; dx++) {
            for (int dz = 0; dz < side; dz++) {
                set.add(Manor.packOffset(dx, dz));
            }
        }
        return set;
    }

    /** 解锁结果。 */
    public enum UnlockResult {
        SUCCESS, NO_MANOR, NOT_YOUR_PLOT, ALREADY_UNLOCKED, NO_QUOTA, NOT_ADJACENT
    }

    /**
     * 玩家用额度解锁<b>世界 chunk ({@code worldChunkX,worldChunkZ})</b>。校验：必须落在自己庄园格内、
     * 尚未解锁、还有剩余额度、且与已解锁区<b>相邻</b>（不能飞地）。通过则加入解锁集合、存库、整这一个 chunk。
     */
    public UnlockResult unlockChunk(GuildId guild, PlayerRef player, int worldChunkX, int worldChunkZ) {
        GuildWorld gw = guilds.find(guild).orElse(null);
        if (gw == null) {
            return UnlockResult.NO_MANOR;
        }
        // 按【脚下 chunk 所属 slot】定位庄园（多庄园正确：操作的是站立的这块，而非 owner 的第一块）。
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        int lx = worldChunkX - gw.originChunkX();
        int lz = worldChunkZ - gw.originChunkZ();
        var c = layout.classify(lx, lz);
        if (!c.isPlot()) {
            return UnlockResult.NOT_YOUR_PLOT; // 站的不是庄园格（路/主城/预留格外）
        }
        Manor manor = manors.findBySlot(guild, c.slot()).orElse(null);
        if (manor == null) {
            return UnlockResult.NO_MANOR; // 脚下这块尚无主
        }
        if (!manor.owner().equals(player)) {
            return UnlockResult.NOT_YOUR_PLOT; // 站在别人的庄园上
        }
        ChunkRegion plot = layout.plotRegion(manor.slot());
        int dx = lx - plot.minChunkX();
        int dz = lz - plot.minChunkZ();
        if (manor.isUnlocked(dx, dz)) {
            return UnlockResult.ALREADY_UNLOCKED;
        }
        if (manor.unlockedChunks().size() >= manor.quotaCap(gw.layout(), levels.manorMaxLevel())) {
            return UnlockResult.NO_QUOTA;
        }
        if (!(manor.isUnlocked(dx - 1, dz) || manor.isUnlocked(dx + 1, dz)
                || manor.isUnlocked(dx, dz - 1) || manor.isUnlocked(dx, dz + 1))) {
            return UnlockResult.NOT_ADJACENT;
        }
        java.util.Set<Integer> nu = new java.util.HashSet<>(manor.unlockedChunks());
        nu.add(Manor.packOffset(dx, dz));
        manors.save(manor.withUnlockedChunks(nu));
        // 整这一个 chunk（世界坐标，同步：单 chunk 很快）
        if (prepMode != TerrainPrepMode.NONE) {
            terrain.prepare(gw.worldName(),
                    new ChunkRegion(worldChunkX, worldChunkZ, worldChunkX, worldChunkZ), prepMode, true);
        }
        return UnlockResult.SUCCESS;
    }

    /** 该庄园剩余可解锁额度（总额度 − 已解锁数，≥0）。 */
    public int remainingQuota(GuildWorld gw, Manor manor) {
        return Math.max(0, manor.quotaCap(gw.layout(), levels.manorMaxLevel()) - manor.unlockedChunks().size());
    }

    /**
     * 管理员<b>设置/增加</b>某玩家庄园的解锁额度上限（定量 set / 增量 add）。一律封顶 <b>chunk 上限</b>
     * {@code plotChunks²}，下限 0。写入庄园 flags 的额度键，{@link Manor#quotaCap} 即据此为准（覆盖按等级算）。
     *
     * @param add true=在当前额度上加 amount；false=直接设为 amount
     * @return 设置后的新额度上限；庄园不存在返回 -1
     */
    public int setManorQuota(GuildId guild, PlayerRef player, int amount, boolean add) {
        // 旧签名：作用于该玩家命中的那块。命令层多庄园已改传 slot。
        Manor manor = manors.findByOwner(guild, player).orElse(null);
        if (manor == null) {
            return -1;
        }
        return setManorQuota(guild, manor.slot(), amount, add);
    }

    /** 设置/增加<b>指定 slot</b> 庄园的解锁额度上限（多庄园寻址）。庄园不存在返回 -1。 */
    public int setManorQuota(GuildId guild, int slot, int amount, boolean add) {
        GuildWorld gw = guilds.find(guild).orElse(null);
        if (gw == null) {
            return -1;
        }
        Manor manor = manors.findBySlot(guild, slot).orElse(null);
        if (manor == null) {
            return -1;
        }
        int cap = gw.layout().plotChunks() * gw.layout().plotChunks();
        int base = add ? manor.quotaCap(gw.layout(), levels.manorMaxLevel()) : 0;
        int newQuota = Math.min(Math.max(0, base + amount), cap);
        java.util.Map<String, String> nf = new java.util.HashMap<>(manor.flags());
        nf.put(Manor.QUOTA_FLAG, String.valueOf(newQuota));
        manors.save(manor.withFlags(nf));
        return newQuota;
    }

    /** 把庄园当前实占范围（layout 坐标）平移到世界坐标后整地，并把本格道路铺成土径。 */
    public void prepareManorTerrain(GuildWorld gw, Manor manor, boolean sync) {
        prepareManorTerrain(gw, manor, sync, true);
    }

    /**
     * @param includeRoads 是否同时铺该格四周的路。<b>仅首次分配(assign)时铺</b>；升级庄园传 false——
     *                     路是格子级的、与庄园等级无关，升级时重铺纯属浪费（且历史上还会触发路面逐级下沉）。
     */
    public void prepareManorTerrain(GuildWorld gw, Manor manor, boolean sync, boolean includeRoads) {
        if (prepMode == TerrainPrepMode.NONE) {
            return;
        }
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        // 首次分配只整【初始解锁的角落正方形】(initialUnlockSide²)，不是满级整块——其余靠玩家自己解锁开荒。
        ChunkRegion plot = layout.plotRegion(manor.slot());
        int side = gw.layout().initialUnlockSide();
        ChunkRegion initial = new ChunkRegion(plot.minChunkX(), plot.minChunkZ(),
                plot.minChunkX() + side - 1, plot.minChunkZ() + side - 1);
        terrain.prepare(gw.worldName(), initial.shift(gw.originChunkX(), gw.originChunkZ()), prepMode, sync);
        if (includeRoads) {
            RoadMask roadMask = layout.roadMask(gw.originChunkX(), gw.originChunkZ());
            for (ChunkRegion road : layout.roadStripsFor(manor.slot())) {
                terrain.surfaceRoad(gw.worldName(), road.shift(gw.originChunkX(), gw.originChunkZ()), roadMask);
            }
        }
    }
}
