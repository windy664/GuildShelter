package org.windy.guildshelter.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

/** 地皮 flag Map ↔ 字符串("k=v;k=v")。JDBC 列 / 平铺文件字段共用。v1 值为布尔/整数,无需转义。 */
final class FlagsCsv {

    private FlagsCsv() {
    }

    static String toCsv(Map<String, String> flags) {
        if (flags == null || flags.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : flags.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    static Map<String, String> parse(String csv) {
        Map<String, String> out = new LinkedHashMap<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String pair : csv.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                out.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return out;
    }
}
