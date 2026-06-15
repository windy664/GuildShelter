package org.windy.guildshelter.domain.port;

import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.TerrainPrepMode;

/**
 * 整地端口：对给定世界的一块<b>世界坐标</b>区域按模式整地（清植被/铺平）。
 * 实现侧负责异步/分批，避免卡主线程（Phase: Bukkit 实现）。
 */
public interface TerrainPreparer {

    void prepare(String worldName, ChunkRegion worldRegion, TerrainPrepMode mode);
}
