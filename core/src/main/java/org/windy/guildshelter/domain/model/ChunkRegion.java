package org.windy.guildshelter.domain.model;

/**
 * 以 chunk 为单位的轴对齐矩形（AABB），上下界闭区间（inclusive）。
 *
 * <p>领域层的一切区域（主城、庄园、实占范围）都用它表示——纯整数、无 GIS。
 * 提供 chunk↔block 的换算：一个 chunk = 16×16 个方块，chunk c 覆盖方块 [c*16, c*16+15]。
 */
public record ChunkRegion(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {

    public ChunkRegion {
        if (minChunkX > maxChunkX || minChunkZ > maxChunkZ) {
            throw new IllegalArgumentException(
                    "非法 ChunkRegion: (" + minChunkX + "," + minChunkZ + ")-(" + maxChunkX + "," + maxChunkZ + ")");
        }
    }

    public int widthChunks() {
        return maxChunkX - minChunkX + 1;
    }

    public int depthChunks() {
        return maxChunkZ - minChunkZ + 1;
    }

    /** 整体平移（chunk），用于把 layout 坐标的区域换算到世界坐标（加 origin 偏移）。 */
    public ChunkRegion shift(int dChunkX, int dChunkZ) {
        return new ChunkRegion(minChunkX + dChunkX, minChunkZ + dChunkZ,
                maxChunkX + dChunkX, maxChunkZ + dChunkZ);
    }

    public boolean containsChunk(int chunkX, int chunkZ) {
        return chunkX >= minChunkX && chunkX <= maxChunkX
                && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
    }

    public boolean containsBlock(int blockX, int blockZ) {
        return containsChunk(blockX >> 4, blockZ >> 4);
    }

    public int minBlockX() {
        return minChunkX << 4;
    }

    public int minBlockZ() {
        return minChunkZ << 4;
    }

    public int maxBlockX() {
        return (maxChunkX << 4) + 15;
    }

    public int maxBlockZ() {
        return (maxChunkZ << 4) + 15;
    }

    /** 中心方块 X（向下取整）。 */
    public int centerBlockX() {
        return (minBlockX() + maxBlockX()) / 2;
    }

    public int centerBlockZ() {
        return (minBlockZ() + maxBlockZ()) / 2;
    }
}
