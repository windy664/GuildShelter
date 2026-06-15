package org.windy.guildshelter.domain.layout;

/**
 * 方形螺旋（Ulam 螺旋）索引：slot 序号 ↔ 网格坐标 的双向映射。
 *
 * <p>slot 0 落在 (0,0)，之后逆时针向外一圈圈铺。性质：取前 N 个 slot 永远是一坨接近正方形
 * 的紧凑块——所以"按 slot 排序尽量排满"、世界边界压到最小，都靠它。
 *
 * <p>{@link #toIndex} 是 O(1)，{@link #toCell} 是 O(1)（按环闭式直接算，非逐格模拟）。
 * 二者互逆，由单元测试 round-trip 保证。
 */
public final class SpiralIndex {

    private SpiralIndex() {
    }

    /** 网格格坐标（不是方块/chunk 坐标，是螺旋的抽象格）。 */
    public record GridCell(int x, int z) {
    }

    /** slot 序号 → 网格格。 */
    public static GridCell toCell(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index < 0: " + index);
        }
        if (index == 0) {
            return new GridCell(0, 0);
        }
        int r = ringOf(index);
        int offset = index - (2 * r - 1) * (2 * r - 1); // 0 .. 8r-1
        int seg = offset / (2 * r);                     // 0..3
        int t = offset % (2 * r);                       // 0..2r-1
        return switch (seg) {
            case 0 -> new GridCell(r, -(r - 1) + t);    // 右边: x=r, z 上行
            case 1 -> new GridCell(r - 1 - t, r);       // 顶边: z=r, x 左行
            case 2 -> new GridCell(-r, r - 1 - t);      // 左边: x=-r, z 下行
            default -> new GridCell(-(r - 1) + t, -r);  // 底边: z=-r, x 右行
        };
    }

    /** 网格格 → slot 序号。 */
    public static int toIndex(int x, int z) {
        int r = Math.max(Math.abs(x), Math.abs(z));
        if (r == 0) {
            return 0;
        }
        int start = (2 * r - 1) * (2 * r - 1);
        int offset;
        if (x == r && z >= -(r - 1) && z <= r) {            // 右边 (seg0)
            offset = z + (r - 1);
        } else if (z == r && x >= -r && x <= r - 1) {        // 顶边 (seg1)
            offset = 2 * r + (r - 1 - x);
        } else if (x == -r && z >= -r && z <= r - 1) {       // 左边 (seg2)
            offset = 4 * r + (r - 1 - z);
        } else {                                             // 底边 (seg3): z==-r, x∈[-(r-1),r]
            offset = 6 * r + (x + (r - 1));
        }
        return start + offset;
    }

    /**
     * index 所在环号 r（max(|x|,|z|)）。环 r（r≥1）含 index 区间 [(2r-1)², (2r+1)²)，共 8r 个。
     * 用闭式估算 + 浮点修正，O(1)。
     */
    public static int ringOf(int index) {
        if (index <= 0) {
            return 0;
        }
        int r = (int) Math.ceil((Math.sqrt(index) - 1) / 2.0);
        while ((2 * r - 1) * (2 * r - 1) > index) {
            r--;
        }
        while ((2 * r + 1) * (2 * r + 1) <= index) {
            r++;
        }
        return r;
    }

    /** 半径 r 的方形螺旋"实心块"含的格子数 = (2r+1)²（环 0..r 全部）。 */
    public static int filledCount(int radius) {
        int side = 2 * radius + 1;
        return side * side;
    }
}
