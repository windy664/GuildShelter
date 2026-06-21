package org.windy.guildshelter.persistence;

import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

/** 庄园已解锁 chunk 集合（packed int）↔ 逗号分隔 CSV。三存储后端共用。 */
final class UnlockedCsv {

    private UnlockedCsv() {
    }

    static String toCsv(Set<Integer> packed) {
        if (packed == null || packed.isEmpty()) {
            return "";
        }
        StringJoiner sj = new StringJoiner(",");
        for (int p : packed) {
            sj.add(Integer.toString(p));
        }
        return sj.toString();
    }

    static Set<Integer> parse(String csv) {
        Set<Integer> out = new HashSet<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                try {
                    out.add(Integer.parseInt(t));
                } catch (NumberFormatException ignored) {
                    // 坏数据跳过
                }
            }
        }
        return out;
    }
}
