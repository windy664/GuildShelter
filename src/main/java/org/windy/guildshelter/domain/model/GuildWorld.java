package org.windy.guildshelter.domain.model;

import java.util.Objects;

/**
 * 一个公会的世界状态（持久化的核心记录）。物理布局由 LayoutCalculator 从配置算出，
 * 这里只存随时间变化/创建时定下的状态。
 *
 * @param guild          公会
 * @param worldName      该公会独立世界名（如 {@code guild_<id>}）
 * @param seed           世界种子（每公会随机，使各公会地形不同）
 * @param originChunkX   网格原点在世界中的 chunk 偏移 X（把主城/地皮网格整体平移到陆地上，避免出生在海里）
 * @param originChunkZ   网格原点在世界中的 chunk 偏移 Z
 * @param guildLevel     公会等级（影响主城大小、庄园等级上限、世界边界下限）
 * @param allocatedSlots 已分配出去的成员 slot 高水位
 */
public record GuildWorld(GuildId guild, String worldName, long seed,
                         int originChunkX, int originChunkZ,
                         int guildLevel, int allocatedSlots) {

    public GuildWorld {
        Objects.requireNonNull(guild, "guild");
        Objects.requireNonNull(worldName, "worldName");
        if (guildLevel < 1) {
            throw new IllegalArgumentException("guildLevel 必须 ≥1");
        }
        if (allocatedSlots < 0) {
            throw new IllegalArgumentException("allocatedSlots 必须 ≥0");
        }
    }

    public static GuildWorld create(GuildId guild, String worldName, long seed) {
        return new GuildWorld(guild, worldName, seed, 0, 0, 1, 0);
    }

    public GuildWorld withOrigin(int chunkX, int chunkZ) {
        return new GuildWorld(guild, worldName, seed, chunkX, chunkZ, guildLevel, allocatedSlots);
    }

    public GuildWorld withGuildLevel(int newLevel) {
        return new GuildWorld(guild, worldName, seed, originChunkX, originChunkZ, newLevel, allocatedSlots);
    }

    public GuildWorld withAllocatedSlots(int newAllocated) {
        return new GuildWorld(guild, worldName, seed, originChunkX, originChunkZ, guildLevel, newAllocated);
    }
}
