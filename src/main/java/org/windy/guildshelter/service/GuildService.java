package org.windy.guildshelter.service;

import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.layout.LayoutConfig;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.model.TerrainPrepMode;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;
import org.windy.guildshelter.domain.port.TerrainPreparer;
import org.windy.guildshelter.domain.port.WorldControl;
import org.windy.guildshelter.domain.rule.LevelRules;

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
    /** 仅用于给<b>新建</b>世界盖章的当前 config 布局；已存在世界一律用各自冻结的 {@code gw.layout()}。 */
    private final LayoutConfig currentLayout;
    private final LevelRules levels;
    private final TerrainPrepMode prepMode;

    public GuildService(GuildRepository guilds, ManorRepository manors, WorldControl worlds,
                        TerrainPreparer terrain, LayoutConfig currentLayout, LevelRules levels,
                        TerrainPrepMode prepMode) {
        this.guilds = guilds;
        this.manors = manors;
        this.worlds = worlds;
        this.terrain = terrain;
        this.currentLayout = currentLayout;
        this.levels = levels;
        this.prepMode = prepMode;
    }

    /** 建会：创建（或返回已有的）公会世界，按<b>当前 config</b> 冻结其布局参数。 */
    public GuildWorld createGuild(GuildId guild, long seed) {
        Optional<GuildWorld> existing = guilds.find(guild);
        if (existing.isPresent()) {
            return existing.get();
        }
        GuildWorld gw = GuildWorld.create(guild, worlds.worldName(guild), seed, currentLayout);
        gw = worlds.ensureWorld(gw);
        guilds.save(gw);
        return gw;
    }

    /**
     * 成员加入：分配下一个螺旋 slot、创建庄园、扩边界、整地。幂等——已有庄园则原样返回。
     *
     * @return 该成员的庄园（含 slot）
     */
    public Manor assignManor(GuildId guild, PlayerRef player) {
        GuildWorld gw = guilds.find(guild).orElseThrow(
                () -> new IllegalStateException("公会世界不存在: " + guild.value()));

        Optional<Manor> owned = manors.findByOwner(guild, player);
        if (owned.isPresent()) {
            return owned.get();
        }

        // 容量门控：公会等级决定名额数，名额满了不能再分新地皮（需先升级公会）。
        // 成员 slot 从 0 起紧凑铺，前 capacity 个为合法名额；nextFreeSlot 会复用退会留下的空缺，
        // 故"下一个空缺 ≥ capacity"即等价于"在册成员已达 capacity"。
        int slot = manors.nextFreeSlot(guild);
        int capacity = levels.maxMembers(gw.guildLevel());
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

        prepareManorTerrain(gw, manor);
        return manor;
    }

    /** 成员退出：释放其庄园 slot（留空缺，下次分配复用，保持螺旋紧凑）。keep flag 为 true 时跳过。 */
    public void releaseManor(GuildId guild, PlayerRef player) {
        manors.findByOwner(guild, player).ifPresent(m -> {
            if (!Flag.KEEP.resolveBool(m.flags())) {
                manors.delete(guild, m.slot());
            }
        });
    }

    /** 成员退出（不需事先知道公会）：找到其庄园并释放。keep flag 为 true 时跳过。 */
    public void releaseManorAnywhere(PlayerRef player) {
        manors.findByOwnerAnywhere(player).ifPresent(m -> {
            if (!Flag.KEEP.resolveBool(m.flags())) {
                manors.delete(m.guild(), m.slot());
            }
        });
    }

    /** 解散公会：卸载世界、删除其全部庄园与世界记录。 */
    public void dissolveGuild(GuildId guild) {
        for (Manor m : manors.findAll(guild)) {
            manors.delete(guild, m.slot());
        }
        worlds.unloadGuild(guild);
        guilds.delete(guild);
    }

    /** 庄园升级：受公会等级封顶；升级后对新扩出的实占范围整地。 */
    public boolean upgradeManor(GuildId guild, PlayerRef player) {
        GuildWorld gw = guilds.find(guild).orElseThrow();
        Manor manor = manors.findByOwner(guild, player).orElseThrow();
        if (!levels.canUpgradeManor(manor.level())) { // 庄园只受物理满级限制，与公会等级无关
            return false;
        }
        Manor upgraded = manor.withLevel(manor.level() + 1);
        manors.save(upgraded);
        prepareManorTerrain(gw, upgraded);
        return true;
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

    /** 把庄园当前实占范围（layout 坐标）平移到世界坐标后整地，并把本格道路铺成土径。 */
    private void prepareManorTerrain(GuildWorld gw, Manor manor) {
        if (prepMode == TerrainPrepMode.NONE) {
            return;
        }
        LayoutCalculator layout = new LayoutCalculator(gw.layout()); // 用该世界冻结的布局
        ChunkRegion active = layout.activeRegion(manor.slot(), manor.level());
        terrain.prepare(gw.worldName(), active.shift(gw.originChunkX(), gw.originChunkZ()), prepMode);
        // 顺带把本格的道路顶层铺成土径（清掉路上植被/树）。
        for (ChunkRegion road : layout.roadStripsFor(manor.slot())) {
            terrain.surfaceRoad(gw.worldName(), road.shift(gw.originChunkX(), gw.originChunkZ()));
        }
    }
}
