package org.windy.guildshelter.domain.layout;

import org.junit.jupiter.api.Test;
import org.windy.guildshelter.domain.layout.SpiralIndex.GridCell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class  SpiralIndexTest {

    @Test
    void knownSmallValues() {
        assertEquals(new GridCell(0, 0), SpiralIndex.toCell(0));
        assertEquals(new GridCell(1, 0), SpiralIndex.toCell(1));
        assertEquals(new GridCell(1, 1), SpiralIndex.toCell(2));
        assertEquals(new GridCell(0, 1), SpiralIndex.toCell(3));
        assertEquals(new GridCell(-1, 1), SpiralIndex.toCell(4));
        assertEquals(new GridCell(-1, 0), SpiralIndex.toCell(5));
        assertEquals(new GridCell(-1, -1), SpiralIndex.toCell(6));
        assertEquals(new GridCell(0, -1), SpiralIndex.toCell(7));
        assertEquals(new GridCell(1, -1), SpiralIndex.toCell(8));
        assertEquals(new GridCell(2, -1), SpiralIndex.toCell(9));
    }

    @Test
    void indexToCellToIndexRoundTrip() {
        for (int i = 0; i < 5000; i++) {
            GridCell c = SpiralIndex.toCell(i);
            assertEquals(i, SpiralIndex.toIndex(c.x(), c.z()), "round-trip 失败 @ index=" + i);
        }
    }

    @Test
    void cellToIndexToCellRoundTrip() {
        for (int x = -25; x <= 25; x++) {
            for (int z = -25; z <= 25; z++) {
                int i = SpiralIndex.toIndex(x, z);
                assertEquals(new GridCell(x, z), SpiralIndex.toCell(i),
                        "逆向 round-trip 失败 @ (" + x + "," + z + ")");
            }
        }
    }

    @Test
    void ringOfBoundaries() {
        assertEquals(0, SpiralIndex.ringOf(0));
        for (int i = 1; i <= 8; i++) {
            assertEquals(1, SpiralIndex.ringOf(i));
        }
        for (int i = 9; i <= 24; i++) {
            assertEquals(2, SpiralIndex.ringOf(i));
        }
        for (int i = 25; i <= 48; i++) {
            assertEquals(3, SpiralIndex.ringOf(i));
        }
    }

    /** 紧凑性：前 (2r+1)² 个 slot 全部落在 max(|x|,|z|) ≤ r 的方块内（取任意前缀即近正方紧凑块）。 */
    @Test
    void prefixIsCompactSquare() {
        for (int r = 0; r <= 12; r++) {
            int count = SpiralIndex.filledCount(r);
            for (int i = 0; i < count; i++) {
                GridCell c = SpiralIndex.toCell(i);
                int maxAbs = Math.max(Math.abs(c.x()), Math.abs(c.z()));
                assertTrue(maxAbs <= r, "index " + i + " 超出半径 " + r + " -> " + c);
            }
            // 环 r 的第一个 index 恰好是 (2r-1)² = 上一实心块大小
            if (r >= 1) {
                assertEquals(SpiralIndex.filledCount(r - 1), (2 * r - 1) * (2 * r - 1));
            }
        }
    }
}
