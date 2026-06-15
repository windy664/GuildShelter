package org.windy.guildshelter.domain.model;

/** 分配/升级地皮时对其范围的整地方式。 */
public enum TerrainPrepMode {
    /** 不整地：地皮就是一块自然地，玩家自己改造。 */
    NONE,
    /** 清植被：保留自然地表高度，清掉草/花/雪/树等地表以上杂物。 */
    CLEAR_VEGETATION,
    /** 铺平：把地皮范围拉平到统一高度。 */
    FLATTEN
}
