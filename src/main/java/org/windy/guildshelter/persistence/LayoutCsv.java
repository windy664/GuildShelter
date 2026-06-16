package org.windy.guildshelter.persistence;

import org.windy.guildshelter.domain.layout.LayoutConfig;

/** LayoutConfig ↔ CSV(8 个整数,逗号分隔,顺序同 record 组件)。JDBC 与平铺文件后端共用。 */
final class LayoutCsv {

    private LayoutCsv() {
    }

    static String toCsv(LayoutConfig l) {
        return l.plotChunks() + "," + l.roadChunks() + ","
                + l.mainCityHalfCellsInitial() + "," + l.mainCityHalfCellsMax() + ","
                + l.plotDefaultChunks() + "," + l.plotChunksPerLevel() + ","
                + l.baseY() + "," + l.marginChunks();
    }

    /** 解析；空或坏数据回退到 fallback（老库迁移/容错）。 */
    static LayoutConfig parse(String csv, LayoutConfig fallback) {
        if (csv == null || csv.isBlank()) {
            return fallback;
        }
        try {
            String[] p = csv.split(",");
            return new LayoutConfig(
                    Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
                    Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()),
                    Integer.parseInt(p[4].trim()), Integer.parseInt(p[5].trim()),
                    Integer.parseInt(p[6].trim()), Integer.parseInt(p[7].trim()));
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
