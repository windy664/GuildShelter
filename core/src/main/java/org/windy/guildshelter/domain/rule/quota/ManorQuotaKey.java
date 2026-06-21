package org.windy.guildshelter.domain.rule.quota;

import org.windy.guildshelter.domain.flag.Flag;

/**
 * 一个可按等级缩放 + 管理员增量的「庄园配额维度」的标识。
 *
 * <p>这是统一抽象的扩展点：优化量（{@link org.windy.guildshelter.domain.rule.OptimizationLimit} 枚举）、
 * 机器配额（{@link MachineKey} 字符串 id）都实现它；将来的新配额只要再实现一个 key，
 * {@link QuotaRegistry} 的解析、{@code setManorBonus} 服务、{@code /gs admin limit} 命令都<b>无需改动</b>。
 */
public interface ManorQuotaKey {

    /** 稳定字符串 id：用于 config 表查找与展示（如 {@code "tiles"}、{@code "minecraft:blast_furnace"}）。 */
    String id();

    /** 管理员增量存的庄园内部 flag 键（{@code _} 前缀，不进 /gs flag 列表）。 */
    String bonusFlagKey();

    /** 玩家可自调的 cap Flag（与服务器预算取紧）；无则返回 null。 */
    default Flag ownerFlag() {
        return null;
    }
}
