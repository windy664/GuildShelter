package org.windy.guildshelter.domain.port;

/**
 * 搬家端口：跨世界复制庄园建筑（chunk 级，保留 TileEntity NBT，模组数据安全）。
 *
 * <p>坐标全用 chunk 数——与 {@link org.windy.guildshelter.domain.layout.LayoutCalculator} 对齐。
 * 复制范围 = 该庄园当前实占的 chunk 矩形（activeRegion），chunk 内部坐标零偏移。
 */
public interface ManorMover {

    /**
     * 把一块 chunk 矩形区域从源世界复制到目标世界。
     *
     * @param fromWorld 源世界名
     * @param fromCX    源区域最小 chunk X
     * @param fromCZ    源区域最小 chunk Z
     * @param sizeChunks 边长（chunk 数，正方形区域 = sizeChunks × sizeChunks）
     * @param toWorld   目标世界名
     * @param toCX      目标区域最小 chunk X
     * @param toCZ      目标区域最小 chunk Z
     * @return 是否成功
     */
    boolean copyRegion(String fromWorld, int fromCX, int fromCZ, int sizeChunks,
                       String toWorld, int toCX, int toCZ);

    /**
     * 清空一块 chunk 猪形区域（全部填空气）。
     *
     * @param world  世界名
     * @param minCX  最小 chunk X
     * @param minCZ  最小 chunk Z
     * @param maxCX  最大 chunk X
     * @param maxCZ  最大 chunk Z
     */
    void clearRegion(String world, int minCX, int minCZ, int maxCX, int maxCZ);

    /**
     * 检测搬家区域内的已知 mod 风险方块。
     * 扫描边界 chunk，发现跨边界的网络/多方块结构时返回警告。
     *
     * @return 风险描述列表；空 = 安全
     */
    java.util.List<String> detectRisks(String world, int minCX, int minCZ, int maxCX, int maxCZ);
}
