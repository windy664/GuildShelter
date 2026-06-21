package org.windy.guildshelter.domain.rule.quota;

import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.model.Manor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * 全项目<b>唯一</b>的庄园配额解析器：把「按庄园等级查表的上限 + 管理员增量 + 玩家自调 cap」合成单个有效上限。
 *
 * <p>等级上限走<b>明确的等级表</b>（不是公式）——配置里写"几级 → 多少"，采用<b>里程碑 + 向下继承</b>：
 * 某维度在某等级的上限 = 该维度<b>≤当前等级的最近一个写过的等级</b>的值（{@code floorEntry}）；
 * 没有任何 ≤当前等级 的里程碑 → -1（不限）。优化量与机器共用同一张表（id 不冲突）。
 *
 * <p>有效上限 = min(服务器预算=等级上限+管理员增量, 玩家自调cap)，{@code -1} 视作不限（+∞）。
 * 新增配额维度只需再实现一个 {@link ManorQuotaKey} + 在表里写它的等级值。
 */
public final class QuotaRegistry {

    /** key.id() → 有序(等级 → 上限)。 */
    private final Map<String, NavigableMap<Integer, Integer>> perLevel;
    /** 机器 id → 展示名（仅机器有）。 */
    private final Map<String, String> display;
    /** 属于「机器」的 id 子集（用于方块实体计数与命令校验）。 */
    private final Set<String> machineIds;

    public QuotaRegistry(Map<String, ? extends Map<Integer, Integer>> perLevel,
                         Map<String, String> display, Set<String> machineIds) {
        Map<String, NavigableMap<Integer, Integer>> copy = new HashMap<>();
        perLevel.forEach((k, v) -> copy.put(k, Collections.unmodifiableNavigableMap(new TreeMap<>(v))));
        this.perLevel = Map.copyOf(copy);
        this.display = Map.copyOf(display);
        this.machineIds = Set.copyOf(machineIds);
    }

    public static QuotaRegistry empty() {
        return new QuotaRegistry(Map.of(), Map.of(), Set.of());
    }

    // ── 元信息 ────────────────────────────────────────────────────────────────

    /** 所有被配额的机器 id（小写）。 */
    public Set<String> machineIds() {
        return machineIds;
    }

    /** 该机器 id 是否被配额。 */
    public boolean hasMachine(String blockId) {
        return blockId != null && machineIds.contains(blockId.toLowerCase(Locale.ROOT));
    }

    /** 该维度是否在等级表里出现过（任一等级写了上限）；用于判断某维度是否启用。 */
    public boolean isConfigured(ManorQuotaKey key) {
        NavigableMap<Integer, Integer> m = perLevel.get(key.id());
        return m != null && !m.isEmpty();
    }

    /** 展示名；非机器或未配置则回退 id 本身。 */
    public String display(String id) {
        String key = id == null ? "" : id.toLowerCase(Locale.ROOT);
        return display.getOrDefault(key, id);
    }

    // ── 解析 ──────────────────────────────────────────────────────────────────

    /**
     * 该维度在某庄园等级下的<b>服务器等级上限</b>（未加增量）：里程碑向下继承（floor）。
     * 未配置该维度、或无 ≤当前等级 的里程碑 → 返回 -1（不限）。
     */
    public int levelBase(ManorQuotaKey key, int manorLevel) {
        NavigableMap<Integer, Integer> m = perLevel.get(key.id());
        if (m == null) {
            return -1;
        }
        Map.Entry<Integer, Integer> e = m.floorEntry(manorLevel);
        return e == null ? -1 : e.getValue();
    }

    /** 管理员给该庄园该维度设的增量（{@code key.bonusFlagKey()} flag），缺省/坏值 = 0。 */
    public int bonus(Manor manor, ManorQuotaKey key) {
        String s = manor.flags().get(key.bonusFlagKey());
        if (s == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(s.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /** 该庄园该维度的<b>最终有效上限</b>；-1 = 不限。 */
    public int effectiveCap(Manor manor, ManorQuotaKey key) {
        int base = levelBase(key, manor.level());
        int server = base < 0 ? -1 : base + bonus(manor, key);
        Flag ownerFlag = key.ownerFlag();
        int owner = ownerFlag == null ? -1 : ownerFlag.resolveInt(manor.flags());
        return minCap(server, owner);
    }

    /** 取较紧的上限：负数(=不限)视作 +∞；两者皆不限返回 -1。 */
    private static int minCap(int a, int b) {
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }
}
