package org.windy.guildshelter.domain.port;

import org.windy.guildshelter.domain.model.PlayerRef;

/**
 * 经济系统的平台无关端口。适配层对接 Vault / XConomy 等具体实现。
 * 由 config 驱动是否启用（无实现时 price flag 不生效）。
 */
public interface EconomyPort {

    /** 玩家余额是否 ≥ amount。 */
    boolean has(PlayerRef player, double amount);

    /** 扣除玩家余额。返回 true 表示成功（余额不足时 false 且不扣）。 */
    boolean withdraw(PlayerRef player, double amount);

    /** 给玩家增加余额（如退费）。 */
    void deposit(PlayerRef player, double amount);

    /** 格式化金额为显示文本（如 "10.00 金币"）。 */
    String format(double amount);
}
