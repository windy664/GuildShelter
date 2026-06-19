package org.windy.guildshelter.domain.flag;

/**
 * 访客交互的分类（平台中立）。两载体的监听器把"玩家在动什么"（方块/容器/展示框/载具）
 * 归到这里的某一类，再交平台中立的 {@code InteractionPolicy} 按对应 {@link Flag} 判定能否放行。
 *
 * <p>"是什么东西"的识别按载体各写（Bukkit 用 Material/InventoryHolder，NeoForge 用
 * BlockState.getMenuProvider/实体类型），但"访客能不能动这一类"的决策共用一处。
 */
public enum InteractCategory {

    /** 门/按钮/拉杆/压力板/告示牌等无库存交互。 */
    USE(Flag.USE),
    /** 箱子/熔炉/漏斗/酿造台等带库存方块。 */
    CONTAINER(Flag.CONTAINER),
    /** 展示框/盔甲架（旋转·取放·破坏合并为一类）。 */
    ITEM_FRAME(Flag.ITEM_FRAME),
    /** 船/矿车（乘坐·破坏；放置由方块保护管）。 */
    VEHICLE(Flag.VEHICLE_USE);

    private final Flag flag;

    InteractCategory(Flag flag) {
        this.flag = flag;
    }

    /** 该交互类对应的庄园 flag。 */
    public Flag flag() {
        return flag;
    }
}
