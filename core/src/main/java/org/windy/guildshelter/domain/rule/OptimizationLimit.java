package org.windy.guildshelter.domain.rule;

import org.windy.guildshelter.domain.flag.Flag;
import org.windy.guildshelter.domain.rule.quota.ManorQuotaKey;

/**
 * 庄园「优化量」限额的固定种类——{@link ManorQuotaKey} 的枚举实现。每一类都遵循统一配额模型：
 * <ul>
 *   <li><b>等级基础值</b>——config 的 base→max 随<b>庄园等级</b>线性缩放；</li>
 *   <li><b>管理员增量</b>——存庄园 flags 的内部键 {@link #bonusFlagKey()}（{@code _lim_<kind>}，下划线前缀不进
 *       {@code /gs flag} 列表），由 {@code /gs admin limit} 设置，让付费玩家单方面加量；</li>
 *   <li><b>玩家自调 cap</b>（仅实体类有）——既存的 {@link #ownerFlag()}（如 {@link Flag#ANIMAL_CAP}）。</li>
 * </ul>
 * 缩放与合成统一由 {@link org.windy.guildshelter.domain.rule.quota.QuotaRegistry} 负责。
 */
public enum OptimizationLimit implements ManorQuotaKey {

    /** 掉落物上限（定时清理/拦截）。无玩家自调 flag。 */
    DROPS(null),
    /** 方块实体上限（放置时拦截）。无玩家自调 flag。 */
    TILES(null),
    /** 动物上限。玩家自调 = {@link Flag#ANIMAL_CAP}。 */
    ANIMAL(Flag.ANIMAL_CAP),
    /** 敌对生物上限。玩家自调 = {@link Flag#HOSTILE_CAP}。 */
    HOSTILE(Flag.HOSTILE_CAP),
    /** 生物总数上限（动物+敌对+其它，不含载具）。玩家自调 = {@link Flag#MOB_CAP}。 */
    MOB(Flag.MOB_CAP),
    /** 载具上限。玩家自调 = {@link Flag#VEHICLE_CAP}。 */
    VEHICLE(Flag.VEHICLE_CAP);

    private final Flag ownerFlag;

    OptimizationLimit(Flag ownerFlag) {
        this.ownerFlag = ownerFlag;
    }

    /** 管理员增量存的庄园内部 flag 键。 */
    @Override
    public String bonusFlagKey() {
        return "_lim_" + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** 玩家自调 cap 的 Flag（实体类才有；DROPS/TILES 返回 null）。 */
    @Override
    public Flag ownerFlag() {
        return ownerFlag;
    }

    /** config/命令用的小写名（drops/tiles/animal/hostile/mob/vehicle）= {@link ManorQuotaKey#id()}。 */
    @Override
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** {@link #id()} 的别名（历史命名保留）。 */
    public String key() {
        return id();
    }

    /** 解析命令参数 → 种类；无匹配返回 null。 */
    public static OptimizationLimit fromKey(String key) {
        if (key == null) return null;
        for (OptimizationLimit l : values()) {
            if (l.key().equalsIgnoreCase(key)) return l;
        }
        return null;
    }
}
