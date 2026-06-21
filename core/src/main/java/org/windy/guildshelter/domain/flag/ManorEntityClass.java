package org.windy.guildshelter.domain.flag;

/**
 * 实体数量上限的分类（平台中立）。两载体把"将要生成/放置的是什么实体"归到这里某一类，
 * 再由计数服务按对应 cap flag 判定是否超限。同一套分类也供未来"家园卡/评分"等功能复用。
 *
 * <p>归类规则：被动生物→ANIMAL，敌对生物→HOSTILE，其余非玩家生物→OTHER_MOB（三者合计=mob 总数），
 * 船/矿车→VEHICLE。物品/弹射物/掉落经验等非生物非载具实体不计入。
 */
public enum ManorEntityClass {

    ANIMAL(Flag.ANIMAL_CAP),
    HOSTILE(Flag.HOSTILE_CAP),
    /** 其余非玩家生物（蝙蝠/铁傀儡/水生等），自身无独立 cap，但计入 mob 总数。 */
    OTHER_MOB(null),
    VEHICLE(Flag.VEHICLE_CAP);

    private final Flag ownCap;

    ManorEntityClass(Flag ownCap) {
        this.ownCap = ownCap;
    }

    /** 该类自身对应的 cap flag；OTHER_MOB 无独立 cap 返回 null（只受 mob-cap 总量约束）。 */
    public Flag ownCap() {
        return ownCap;
    }

    /** 是否计入生物总数（mob-cap）。载具不算生物。 */
    public boolean isLiving() {
        return this != VEHICLE;
    }
}
