package org.windy.guildshelter.domain.layout;

/**
 * 判定某 chunk（<b>世界坐标</b>）是否落在路网带上。
 *
 * <p>由 {@link LayoutCalculator#roadMask} 闭式构造（复用 {@link LayoutCalculator#classify} 的路网规则），
 * 取模判定 O(1)、命中路/城时零分配、且<b>与铺路顺序无关</b>。整地铺路时传给整地端口：
 * 水上架桥决定是否在桥边加护栏时，若<b>外侧那格是另一条路</b>则不加——避免十字路口被栅栏围死
 * （旧逻辑只看"外侧是水"，两条路在水面相交时会因谁先铺好而把路口拦腰栏住）。
 */
@FunctionalInterface
public interface RoadMask {

    boolean isRoadChunk(int worldChunkX, int worldChunkZ);

    /** 永远判否：roadChunks=0 或无需路口感知时的安全默认，等价于退回"外侧是水即架护栏"的旧行为。 */
    RoadMask NONE = (cx, cz) -> false;
}
