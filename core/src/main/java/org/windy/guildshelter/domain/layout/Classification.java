package org.windy.guildshelter.domain.layout;

/**
 * {@link LayoutCalculator#classify} 的结果：某 chunk 属于主城 / 某成员庄园 / 路。
 *
 * @param type   归类
 * @param slot   当 type==PLOT 时为成员 slot 号（0 起）；否则为 -1
 * @param border 当 type==PLOT 且该 chunk 落在庄园最外一圈时为 true（供生成器铺边框，归属不变）
 */
public record Classification(RegionType type, int slot, boolean border) {

    private static final Classification MAIN_CITY = new Classification(RegionType.MAIN_CITY, -1, false);
    private static final Classification ROAD = new Classification(RegionType.ROAD, -1, false);

    public static Classification mainCity() {
        return MAIN_CITY;
    }

    public static Classification road() {
        return ROAD;
    }

    public static Classification plot(int slot, boolean border) {
        return new Classification(RegionType.PLOT, slot, border);
    }

    public boolean isPlot() {
        return type == RegionType.PLOT;
    }

    public boolean isMainCity() {
        return type == RegionType.MAIN_CITY;
    }

    public boolean isRoad() {
        return type == RegionType.ROAD;
    }
}
