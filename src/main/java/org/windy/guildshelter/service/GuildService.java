package org.windy.guildshelter.service;

import org.windy.guildshelter.domain.layout.LayoutCalculator;
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
 * <p>这是"本插件作为附属品"的核心——上层（PlayerGuild provider 或 admin 命令）只调这里的方法，
 * 不关心世界/网格/整地细节。layout 区域换算到世界坐标用 {@link GuildWorld} 的 origin 偏移。
 */
public final class GuildService {

    private final GuildRepository guilds;
    private final ManorRepository manors;
    private final WorldControl worlds;
    private final TerrainPreparer terrain;
    private final LayoutCalculator layout;
    private final LevelRules levels;
    private final TerrainPrepMode prepMode;

    public GuildService(GuildRepository guilds, ManorRepository manors, WorldControl worlds,
                        TerrainPreparer terrain, LayoutCalculator layout, LevelRules levels,
                        TerrainPrepMode prepMode) {
        this.guilds = guilds;
        this.manors = manors;
        this.worlds = worlds;
        this.terrain = terrain;
        this.layout = layout;
        this.levels = levels;
        this.prepMode = prepMode;
    }

    /** 建会：创建（或返回已有的）公会世界（锚定陆地、随机种子由 worlds 决定）。 */
    public GuildWorld createGuild(GuildId guild, long seed) {
        Optional<GuildWorld> existing = guilds.find(guild);
        if (existing.isPresent()) {
            return existing.get();
        }
        GuildWorld gw = GuildWorld.create(guild, worlds.worldName(guild), seed);
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

        int slot = manors.nextFreeSlot(guild);
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

    /** 成员退出：释放其庄园 slot（留空缺，下次分配复用，保持螺旋紧凑）。 */
    public void releaseManor(GuildId guild, PlayerRef player) {
        manors.findByOwner(guild, player)
                .ifPresent(m -> manors.delete(guild, m.slot()));
    }

    /** 成员退出（不需事先知道公会）：找到其庄园并释放。 */
    public void releaseManorAnywhere(PlayerRef player) {
        manors.findByOwnerAnywhere(player)
                .ifPresent(m -> manors.delete(m.guild(), m.slot()));
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
        if (!levels.canUpgradeManor(manor.level(), gw.guildLevel())) {
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

    /** 把庄园当前实占范围（layout 坐标）平移到世界坐标后整地。 */
    private void prepareManorTerrain(GuildWorld gw, Manor manor) {
        if (prepMode == TerrainPrepMode.NONE) {
            return;
        }
        ChunkRegion active = layout.activeRegion(manor.slot(), manor.level());
        ChunkRegion world = active.shift(gw.originChunkX(), gw.originChunkZ());
        terrain.prepare(gw.worldName(), world, prepMode);
    }
}
