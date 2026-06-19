package org.windy.guildshelter.domain.model;

import org.windy.guildshelter.domain.layout.LayoutConfig;

import java.util.Objects;

/**
 * 一个公会的世界状态（持久化的核心记录）。物理布局由 {@link LayoutConfig} 决定，
 * 这里只存随时间变化/创建时定下的状态。
 *
 * <p><b>布局参数随世界冻结</b>：{@code layout} 是该世界<b>创建时</b>的几何配置快照，
 * 此后该世界的所有网格换算都用这一份。服主改 config 只影响<b>新建</b>的世界，
 * 已存在世界保持原参数——否则改配置会让旧世界的地皮/建筑/权限全部错位。
 *
 * @param guild          公会
 * @param worldName      该公会独立世界名（如 {@code guild_<id>}）
 * @param seed           世界种子（每公会随机，使各公会地形不同）
 * @param originChunkX   网格原点在世界中的 chunk 偏移 X
 * @param originChunkZ   网格原点在世界中的 chunk 偏移 Z
 * @param guildLevel     公会等级
 * @param allocatedSlots 已分配出去的成员 slot 高水位
 * @param layout         创建时冻结的几何布局参数
 */
public record GuildWorld(GuildId guild, String worldName, long seed,
                         int originChunkX, int originChunkZ,
                         int guildLevel, int allocatedSlots,
                         LayoutConfig layout, double funds, String bulletin,
                         TerrainPrepMode terrainMode, String serverName) {

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
    }

    /** 用给定（当前 config 的）布局参数新建一个世界记录。 */
    public static GuildWorld create(GuildId guild, String worldName, long seed, LayoutConfig layout,
                                    TerrainPrepMode terrainMode, String serverName) {
        return new GuildWorld(guild, worldName, seed, 0, 0, 1, 0, layout, 0, "", terrainMode, serverName);
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
        return new GuildWorld(guild, worldName, seed, chunkX, chunkZ, guildLevel, allocatedSlots, layout, funds, bulletin, terrainMode, serverName);
    }

    /** 换世界种子（仅用于首建时为避开海洋而重掷种子；已有世界禁用，会与磁盘存档错位）。 */
    public GuildWorld withSeed(long newSeed) {
        return new GuildWorld(guild, worldName, newSeed, originChunkX, originChunkZ, guildLevel, allocatedSlots, layout, funds, bulletin, terrainMode, serverName);
    }

    public GuildWorld withGuildLevel(int newLevel) {
        return new GuildWorld(guild, worldName, seed, originChunkX, originChunkZ, newLevel, allocatedSlots, layout, funds, bulletin, terrainMode, serverName);
    }

    public GuildWorld withAllocatedSlots(int newAllocated) {
        return new GuildWorld(guild, worldName, seed, originChunkX, originChunkZ, guildLevel, newAllocated, layout, funds, bulletin, terrainMode, serverName);
    }

    public GuildWorld withFunds(double newFunds) {
        return new GuildWorld(guild, worldName, seed, originChunkX, originChunkZ, guildLevel, allocatedSlots, layout, newFunds, bulletin, terrainMode, serverName);
    }

    public GuildWorld withBulletin(String newBulletin) {
        return new GuildWorld(guild, worldName, seed, originChunkX, originChunkZ, guildLevel, allocatedSlots, layout, funds, newBulletin, terrainMode, serverName);
    }

    public GuildWorld withTerrainMode(TerrainPrepMode mode) {
        return new GuildWorld(guild, worldName, seed, originChunkX, originChunkZ, guildLevel, allocatedSlots, layout, funds, bulletin, mode, serverName);
    }

    public GuildWorld withServerName(String name) {
        return new GuildWorld(guild, worldName, seed, originChunkX, originChunkZ, guildLevel, allocatedSlots, layout, funds, bulletin, terrainMode, name);
    }
}
