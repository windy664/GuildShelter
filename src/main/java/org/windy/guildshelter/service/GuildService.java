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
        guilds.save(gw);
        prepareMainCityRoads(gw); // 建会即给主城铺一圈环路（主城非成员 slot，否则四周路会"被吞"）
        buildCityWall(gw);        // 围墙（默认关）
        return gw;
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
        Manor manor = Manor.create(slot, guild, player);
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

    /** 成员退出：释放其庄园 slot（留空缺，下次分配复用，保持螺旋紧凑）。keep flag 为 true 时跳过。 */
    public void releaseManor(GuildId guild, PlayerRef player) {
        manors.findByOwner(guild, player).ifPresent(m -> {
            if (!Flag.KEEP.resolveBool(m.flags())) {
                manors.delete(guild, m.slot());
                MembershipChangeListener l = membershipListener;
                if (l != null) l.onMemberReleased(guild, player.uuid());
            }
        });
    }

    /** 成员退出（不需事先知道公会）：找到其庄园并释放。keep flag 为 true 时跳过。 */
    public void releaseManorAnywhere(PlayerRef player) {
        manors.findByOwnerAnywhere(player).ifPresent(m -> {
            if (!Flag.KEEP.resolveBool(m.flags())) {
                manors.delete(m.guild(), m.slot());
                MembershipChangeListener l = membershipListener;
                if (l != null) l.onMemberReleased(m.guild(), player.uuid());
            }
        });
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
        guilds.find(guild).orElseThrow(); // 校验世界存在
        Manor manor = manors.findByOwner(guild, player).orElseThrow();
        if (!levels.canUpgradeManor(manor.level())) { // 庄园只受物理满级限制，与公会等级无关
            return false;
        }
        Manor upgraded = manor.withLevel(manor.level() + 1);
        manors.save(upgraded);
        return true; // 仅扩权限，不整地
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
        GuildWorld gw = guilds.find(guild).orElseThrow();
        Manor manor = manors.findByOwner(guild, player).orElseThrow();
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

        // 10. 更新 DB：旧 manor 删 → 新 manor 保留数据
        manors.delete(oldManor.guild(), oldManor.slot());
        Manor newManor = new Manor(targetSlot, targetGuild, player, oldManor.level(),
                oldManor.coBuilders(), oldManor.members(), oldManor.denied(), oldManor.flags());
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
        ChunkRegion active = layout.activeRegion(manor.slot(), manor.level());
        terrain.prepare(gw.worldName(), active.shift(gw.originChunkX(), gw.originChunkZ()), prepMode, sync);
        if (includeRoads) {
            RoadMask roadMask = layout.roadMask(gw.originChunkX(), gw.originChunkZ());
            for (ChunkRegion road : layout.roadStripsFor(manor.slot())) {
                terrain.surfaceRoad(gw.worldName(), road.shift(gw.originChunkX(), gw.originChunkZ()), roadMask);
            }
        }
    }
}
