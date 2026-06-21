package org.windy.guildshelter.adapter.bukkit;

import org.windy.guildshelter.domain.flag.ManorEntityClass;

/**
 * 公会<b>主城</b>的固定限额预算（config {@code main-city-limits}）。主城不是 {@link org.windy.guildshelter.domain.model.Manor}，
 * 没有按等级的配额，故用一组全公会统一的固定上限。{@code -1} = 不限。
 *
 * @param enabled      是否启用主城限额（关 = 全部不限，零开销）
 * @param maxDrops     主城掉落物上限（clean 模式超限清最旧）
 * @param maxTiles     主城方块实体总数上限（拦截新放置）
 * @param maxAnimals   主城被动动物上限
 * @param maxHostiles  主城敌对生物上限
 * @param maxMobs      主城生物总数上限（动物+敌对+其它）
 * @param maxVehicles  主城载具（船/矿车）上限
 */
public record CityLimits(boolean enabled, int maxDrops, int maxTiles,
                         int maxAnimals, int maxHostiles, int maxMobs, int maxVehicles) {

    /** 全部不限（未配置 / 关闭时用）。 */
    public static CityLimits disabled() {
        return new CityLimits(false, -1, -1, -1, -1, -1, -1);
    }

    /** 某类生物在主城的上限；OTHER_MOB 无自身上限（靠 {@link #maxMobs} 总数），返回 -1。 */
    public int capOf(ManorEntityClass cls) {
        return switch (cls) {
            case ANIMAL -> maxAnimals;
            case HOSTILE -> maxHostiles;
            case VEHICLE -> maxVehicles;
            case OTHER_MOB -> -1;
        };
    }
}
