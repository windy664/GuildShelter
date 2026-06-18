package org.windy.guildshelter.domain.port;

import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.TerrainPrepMode;

/**
 * 整地端口：对给定世界的一块<b>世界坐标</b>区域按模式整地（清植被/铺平）。
 * 实现侧负责异步/分批，避免卡主线程（Phase: Bukkit 实现）。
 */
public interface TerrainPreparer {

    void prepare(String worldName, ChunkRegion worldRegion, TerrainPrepMode mode);

    /** 同步/异步整地。sync=true 一次处理完（claim 用），sync=false 分批（升级/重置用）。 */
    default void prepare(String worldName, ChunkRegion region, TerrainPrepMode mode, boolean sync) {
        prepare(worldName, region, mode);
    }

    /**
     * 把给定区域的<b>道路顶层</b>铺成土径：穿过并清掉植被/树木/积雪定位真正的自然地面，
     * 再把地面顶层换成土径；水面/虚空跳过。实现侧负责异步/分批。
     */
    void surfaceRoad(String worldName, ChunkRegion worldRegion);
}
