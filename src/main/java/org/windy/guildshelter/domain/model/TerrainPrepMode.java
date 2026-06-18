package org.windy.guildshelter.domain.model;

/** 分配/升级地皮时对其范围的整地方式；也决定公会世界的生成器类型。 */
public enum TerrainPrepMode {
    /** 不整地：地皮就是一块自然地，玩家自己改造。 */
    NONE,
    /** 清植被：保留自然地表高度，清掉草/花/雪/树等地表以上杂物。 */
    CLEAR_VEGETATION,
    /** 铺平：把地皮范围拉平到统一高度。 */
    FLATTEN,
    /** 空岛：虚空世界，中央主城由整地器铺一层草方块，成员地皮自行建造。 */
    VOID,
    /** 超平坦：用 Bukkit 超平坦生成器创建世界（默认 4 层：基岩+泥土x2+草方块）。 */
    FLAT
}
