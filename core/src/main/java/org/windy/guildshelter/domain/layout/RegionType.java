package org.windy.guildshelter.domain.layout;

/** 公会营地里某个 chunk 的归类。 */
public enum RegionType {
    /** 公会主城（中心实心块，含格内路沟，连续无内部路）。 */
    MAIN_CITY,
    /** 某个成员庄园。 */
    PLOT,
    /** 庄园之间的间距带（路）。 */
    ROAD
}
