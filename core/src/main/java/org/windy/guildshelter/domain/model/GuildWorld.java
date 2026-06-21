package org.windy.guildshelter.domain.model;

import org.windy.guildshelter.domain.layout.LayoutConfig;

import java.util.Objects;
import java.util.Set;

/**
 * 一个公会的世界状态（持久化的核心记录）。物理布局由 {@link LayoutConfig} 决定，
 * 这里只存随时间变化/创建时定下的状态。
 *
 * <p><b>布局参数随世界冻结</b>：{@code layout} 是该世界<b>创建时</b>的几何配置快照，
 * 此后该世界的所有网格换算都用这一份。服主改 config 只影响<b>新建</b>的世界，
 * 已存在世界保持原参数——否则改配置会让旧世界的庄园/建筑/权限全部错位。
 *
 * @param guild              公会
 * @param worldName          该公会独立世界名（如 {@code guild_<id>}）
 * @param seed               世界种子（每公会随机，使各公会地形不同）
 * @param originChunkX       网格原点在世界中的 chunk 偏移 X
 * @param originChunkZ       网格原点在世界中的 chunk 偏移 Z
 * @param guildLevel         公会等级
 * @param allocatedSlots     已分配出去的成员 slot 高水位
 * @param layout             创建时冻结的几何布局参数
 * @param cityUnlockedChunks 主城已解锁的 chunk（相对主城 cell0 原点的偏移，{@code (dx<<10)|dz}）。
 * @param cityQuotaOverride  主城解锁额度上限的<b>管理员覆盖值</b>；{@code -1}=未设(回退按公会等级算)。
 *                           与公会等级<b>分离</b>：公会等级管别的（名额/福利…），主城额度由管理员单独给。
 */
public record GuildWorld(GuildId guild, String worldName, long seed,
                         int originChunkX, int originChunkZ,
                         int guildLevel, int allocatedSlots,
                         LayoutConfig layout, double funds, String bulletin,
                         TerrainPrepMode terrainMode, String serverName,
                         Set<Integer> cityUnlockedChunks, int cityQuotaOverride) {

    public GuildWorld {
        Objects.requireNonNull(guild, "guild");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(layout, "layout");
        if (guildLevel < 1) {
            throw new IllegalArgumentException("guildLevel 必须 ≥1");
        }
        if (allocatedSlots < 0) {
            throw new IllegalArgumentException("allocatedSlots 必须 ≥0");
        }
        cityUnlockedChunks = Set.copyOf(cityUnlockedChunks == null ? Set.of() : cityUnlockedChunks);
    }

    /** 主城内部偏移 (dx,dz) 是否已解锁（可建造）。打包同 {@link Manor#packOffset}。 */
    public boolean isCityUnlocked(int dx, int dz) {
        return cityUnlockedChunks.contains(Manor.packOffset(dx, dz));
    }

    /**
     * 主城当前<b>解锁额度上限</b>：管理员设过({@code cityQuotaOverride>=0})用其值，否则回退按公会等级算
     * {@link LayoutConfig#cityQuotaAtLevel}（平滑兼容）。一律封顶主城 chunk 上限 {@code mainCityMaxChunks²}。
     */
    public int cityQuotaCap(int maxGuildLevel) {
        int cap = layout.mainCityMaxChunks() * layout.mainCityMaxChunks();
        if (cityQuotaOverride >= 0) {
            return Math.min(cityQuotaOverride, cap);
        }
        return Math.min(layout.cityQuotaAtLevel(guildLevel, maxGuildLevel), cap);
    }

    /** 兼容旧签名（12 参，无 cityUnlockedChunks/cityQuotaOverride）。 */
    public GuildWorld(GuildId guild, String worldName, long seed,
                      int originChunkX, int originChunkZ,
                      int guildLevel, int allocatedSlots,
                      LayoutConfig layout, double funds, String bulletin,
                      TerrainPrepMode terrainMode, String serverName) {
        this(guild, worldName, seed, originChunkX, originChunkZ, guildLevel, allocatedSlots,
                layout, funds, bulletin, terrainMode, serverName, Set.of(), -1);
    }

    /** 兼容签名（13 参，无 cityQuotaOverride）。 */
    public GuildWorld(GuildId guild, String worldName, long seed,
                      int originChunkX, int originChunkZ,
                      int guildLevel, int allocatedSlots,
                      LayoutConfig layout, double funds, String bulletin,
                      TerrainPrepMode terrainMode, String serverName, Set<Integer> cityUnlockedChunks) {
        this(guild, worldName, seed, originChunkX, originChunkZ, guildLevel, allocatedSlots,
                layout, funds, bulletin, terrainMode, serverName, cityUnlockedChunks, -1);
    }

    /** 用给定（当前 config 的）布局参数新建一个世界记录。 */
    public static GuildWorld create(GuildId guild, String worldName, long seed, LayoutConfig layout,
                                    TerrainPrepMode terrainMode, String serverName) {
        return new GuildWorld(guild, worldName, seed, 0, 0, 1, 0, layout, 0, "", terrainMode, serverName, Set.of(), -1);
    }

    /** 兼容旧签名（terrainMode 默认 CLEAR_VEGETATION，serverName 为空）。 */
    public static GuildWorld create(GuildId guild, String worldName, long seed, LayoutConfig layout, TerrainPrepMode terrainMode) {
        return create(guild, worldName, seed, layout, terrainMode, "");
    }

    /** 兼容旧签名。 */
    public static GuildWorld create(GuildId guild, String worldName, long seed, LayoutConfig layout) {
        return create(guild, worldName, seed, layout, TerrainPrepMode.CLEAR_VEGETATION, "");
    }

    public GuildWorld withOrigin(int chunkX, int chunkZ) {
        return new GuildWorld(guild, worldName, seed, chunkX, chunkZ, guildLevel, allocatedSlots, layout, funds, bulletin, terrainMode, serverName, cityUnlockedChunks, cityQuotaOverride);
    }

    /** 换世界种子（仅用于首建时为避开海洋而重掷种子；已有世界禁用，会与磁盘存档错位）。 */
    public GuildWorld withSeed(long newSeed) {
        return new GuildWorld(guild, worldName, newSeed, originChunkX, originChunkZ, guildLevel, allocatedSlots, layout, funds, bulletin, terrainMode, serverName, cityUnlockedChunks, cityQuotaOverride);
    }

    public GuildWorld withGuildLevel(int newLevel) {
        return new GuildWorld(guild, worldName, seed, originChunkX, originChunkZ, newLevel, allocatedSlots, layout, funds, bulletin, terrainMode, serverName, cityUnlockedChunks, cityQuotaOverride);
    }

    public GuildWorld withAllocatedSlots(int newAllocated) {
        return new GuildWorld(guild, worldName, seed, originChunkX, originChunkZ, guildLevel, newAllocated, layout, funds, bulletin, terrainMode, serverName, cityUnlockedChunks, cityQuotaOverride);
    }

    public GuildWorld withFunds(double newFunds) {
        return new GuildWorld(guild, worldName, seed, originChunkX, originChunkZ, guildLevel, allocatedSlots, layout, newFunds, bulletin, terrainMode, serverName, cityUnlockedChunks, cityQuotaOverride);
    }

    public GuildWorld withBulletin(String newBulletin) {
        return new GuildWorld(guild, worldName, seed, originChunkX, originChunkZ, guildLevel, allocatedSlots, layout, funds, newBulletin, terrainMode, serverName, cityUnlockedChunks, cityQuotaOverride);
    }

    public GuildWorld withTerrainMode(TerrainPrepMode mode) {
        return new GuildWorld(guild, worldName, seed, originChunkX, originChunkZ, guildLevel, allocatedSlots, layout, funds, bulletin, mode, serverName, cityUnlockedChunks, cityQuotaOverride);
    }

    public GuildWorld withServerName(String name) {
        return new GuildWorld(guild, worldName, seed, originChunkX, originChunkZ, guildLevel, allocatedSlots, layout, funds, bulletin, terrainMode, name, cityUnlockedChunks, cityQuotaOverride);
    }

    public GuildWorld withCityUnlockedChunks(Set<Integer> newUnlocked) {
        return new GuildWorld(guild, worldName, seed, originChunkX, originChunkZ, guildLevel, allocatedSlots, layout, funds, bulletin, terrainMode, serverName, newUnlocked, cityQuotaOverride);
    }

    /** 管理员设置主城额度覆盖值（{@code -1}=清除覆盖，回退按公会等级）。 */
    public GuildWorld withCityQuotaOverride(int override) {
        return new GuildWorld(guild, worldName, seed, originChunkX, originChunkZ, guildLevel, allocatedSlots, layout, funds, bulletin, terrainMode, serverName, cityUnlockedChunks, override);
    }
}
