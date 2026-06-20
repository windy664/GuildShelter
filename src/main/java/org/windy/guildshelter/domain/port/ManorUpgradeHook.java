package org.windy.guildshelter.domain.port;

import org.windy.guildshelter.domain.model.Manor;

/**
 * 庄园升级<b>扩展回调</b>：管理员命令 {@code /gs admin upgrade-manor} 升级成功后触发。供未来玩法挂接——
 * 如按新等级给庄主/成员发 buff、加成、经济结算、解锁特权等，无需改 GuildService 核心。
 *
 * <p>可选注入（{@link org.windy.guildshelter.service.GuildService#setUpgradeHook}）；未注入则无操作。
 * 多个扩展可在适配层组合成一个再注入。
 */
@FunctionalInterface
public interface ManorUpgradeHook {

    /**
     * @param manor    升级<b>后</b>的最新庄园（{@code manor.level()} 即新等级）
     * @param oldLevel 升级前等级
     */
    void onUpgrade(Manor manor, int oldLevel);
}
