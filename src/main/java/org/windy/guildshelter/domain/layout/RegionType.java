package org.windy.guildshelter.domain.layout;

/** 公会世界里某个 chunk 的归类。 */
public enum RegionType {
    /** 公会主城（中心实心块，含格内路沟，连续无内部路）。 */
    MAIN_CITY,
    /** 某个成员地皮。 */
    PLOT,
    /** 地皮之间的间距带（路）。 */
    ROAD
}
