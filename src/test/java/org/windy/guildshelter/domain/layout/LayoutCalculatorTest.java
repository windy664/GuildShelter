package org.windy.guildshelter.domain.layout;

import org.junit.jupiter.api.Test;
import org.windy.guildshelter.domain.model.ChunkRegion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutCalculatorTest {

    // 测试用固定配置（不跟生产默认值走，免得 defaults() 调尺寸时几何断言全废）：
    // P=4, R=1, pitch=5, 主城 initial=max=1(不成长,固定 3×3), base=9, 初始实占2 每级+1
    private final LayoutCalculator calc =
            new LayoutCalculator(new LayoutConfig(4, 1, 1, 1, 2, 1, 64, 2));

    @Test
    void centerIsMainCity() {
        assertTrue(calc.classify(0, 0).isMainCity());
        ChunkRegion city = calc.mainCityRegion();
        assertTrue(city.containsBlock(calc.spawnBlockX(), calc.spawnBlockZ()));
    }

    @Test
    void mainCityBlockIsFullyMainCity() {
        ChunkRegion city = calc.mainCityRegion();
        assertEquals(-5, city.minChunkX());
        assertEquals(9, city.maxChunkX());
        for (int cx = city.minChunkX(); cx <= city.maxChunkX(); cx++) {
            for (int cz = city.minChunkZ(); cz <= city.maxChunkZ(); cz++) {
                assertTrue(calc.classify(cx, cz).isMainCity(), "应为主城 @ (" + cx + "," + cz + ")");
            }
        }
        // 主城外一圈不再是主城
        assertFalse(calc.classify(10, 0).isMainCity());
    }

    @Test
    void everyPlotChunkClassifiesToItsSlot() {
        for (int slot = 0; slot < 80; slot++) {
            ChunkRegion plot = calc.plotRegion(slot);
            assertEquals(4, plot.widthChunks());
            assertEquals(4, plot.depthChunks());
            for (int cx = plot.minChunkX(); cx <= plot.maxChunkX(); cx++) {
                for (int cz = plot.minChunkZ(); cz <= plot.maxChunkZ(); cz++) {
                    Classification c = calc.classify(cx, cz);
                    assertTrue(c.isPlot(), "应为地皮 @ (" + cx + "," + cz + ") slot=" + slot);
                    assertEquals(slot, c.slot(), "slot 不一致 @ (" + cx + "," + cz + ")");
                }
            }
        }
    }

    @Test
    void plotsDoNotOverlap() {
        for (int a = 0; a < 60; a++) {
            for (int b = a + 1; b < 60; b++) {
                assertFalse(overlap(calc.plotRegion(a), calc.plotRegion(b)),
                        "地皮重叠 slot " + a + " vs " + b);
            }
        }
    }

    @Test
    void activeRegionWithinPlotAnchoredAtMinCorner() {
        for (int slot = 0; slot < 20; slot++) {
            ChunkRegion plot = calc.plotRegion(slot);
            int[] expectedSize = {2, 3, 4, 4, 4}; // level 1..5, 封顶 P=4
            for (int level = 1; level <= 5; level++) {
                ChunkRegion active = calc.activeRegion(slot, level);
                assertEquals(expectedSize[level - 1], active.widthChunks(), "level " + level + " 实占尺寸");
                assertTrue(contains(plot, active), "实占应 ⊆ 地皮 @ slot " + slot + " level " + level);
                // 半螺旋式：锚定在地皮最小角，从角落向外扩
                assertEquals(plot.minChunkX(), active.minChunkX(), "实占应锚定地皮最小角X @ slot " + slot);
                assertEquals(plot.minChunkZ(), active.minChunkZ(), "实占应锚定地皮最小角Z @ slot " + slot);
            }
        }
    }

    @Test
    void roadBetweenPlots() {
        // slot 0 的格 = (2,-1) → 地皮 chunk [10..13]×[-5..-2], 路沟在 cx=14
        assertTrue(calc.classify(14, -5).isRoad());
    }

    @Test
    void borderSizeMonotonicInAllocation() {
        double prev = -1;
        for (int allocated = 0; allocated <= 300; allocated++) {
            double size = calc.borderSizeBlocks(allocated);
            assertTrue(size >= prev, "边界应随分配单调不减 @ allocated=" + allocated);
            prev = size;
        }
    }

    private static boolean overlap(ChunkRegion a, ChunkRegion b) {
        return !(a.maxChunkX() < b.minChunkX() || b.maxChunkX() < a.minChunkX()
                || a.maxChunkZ() < b.minChunkZ() || b.maxChunkZ() < a.minChunkZ());
    }

    private static boolean contains(ChunkRegion outer, ChunkRegion inner) {
        return inner.minChunkX() >= outer.minChunkX() && inner.maxChunkX() <= outer.maxChunkX()
                && inner.minChunkZ() >= outer.minChunkZ() && inner.maxChunkZ() <= outer.maxChunkZ();
    }
}
