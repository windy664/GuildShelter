package org.windy.guildshelter.domain.layout;

import org.windy.guildshelter.domain.model.ChunkRegion;

import java.util.OptionalInt;

/**
 * 布局单一真相源：把 {@link LayoutConfig} + {@link SpiralIndex} 的数学集中在这里。
 * 生成器、保护监听、chunk 加载管理三处共用本类，保证"生成的庄园"与"判权限的庄园"永远一致。
 *
 * <p>坐标系：网格格 (gx,gz) 的庄园占 chunk [gx*pitch, gx*pitch+P-1]，其后 R 个 chunk 为路沟。
 * 主城就是<b>中心那一格(cell 0)</b>的一块"地皮"：整格 plot footprint 都是主城领地（对应螺旋 slot [0, base)，
 * base=1），其后 R 个 chunk 为路沟，与成员格同构。成员庄园用螺旋 slot base, base+1, ... 紧凑向外铺。
 * {@code mainCityMaxChunks} 只决定主城能<b>解锁多少</b> chunk（额度上限），不是城/路边界。全部纯整数，无 GIS。
 */
public final class LayoutCalculator {

    private final LayoutConfig config;
    private final int pitch;
    private final int plot;
    private final int base;

    public LayoutCalculator(LayoutConfig config) {
        this.config = config;
        this.pitch = config.pitchChunks();
        this.plot = config.plotChunks();
        this.base = config.mainCityBase();
    }

    public LayoutConfig config() {
        return config;
    }

    /** 某 chunk 属于 主城 / 某成员庄园 / 路。 */
    public Classification classify(int chunkX, int chunkZ) {
        int gx = Math.floorDiv(chunkX, pitch);
        int gz = Math.floorDiv(chunkZ, pitch);
        int s = SpiralIndex.toIndex(gx, gz);
        int lx = Math.floorMod(chunkX, pitch);
        int lz = Math.floorMod(chunkZ, pitch);
        if (s < base) { // 中心格(cell 0) = 主城【地皮】：整格 plot footprint 都是主城领地，仅其后 R 个 chunk 穿行带为路（与成员格同构）。
            // 超出 mainCityMaxChunks 额度的城地 = 主城但【未解锁】——受保护、保持自然，会长有额度时可解锁（形状自由）；非"路/缓冲"。
            return (lx < plot && lz < plot) ? Classification.mainCity() : Classification.road();
        }
        boolean inPlot = lx < plot && lz < plot;
        if (inPlot) {
            boolean border = lx == 0 || lx == plot - 1 || lz == 0 || lz == plot - 1;
            return Classification.plot(s - base, border);
        }
        return Classification.road();
    }

    /** 便捷：该 chunk 若属于某成员庄园，返回其 slot。 */
    public OptionalInt slotAt(int chunkX, int chunkZ) {
        Classification c = classify(chunkX, chunkZ);
        return c.isPlot() ? OptionalInt.of(c.slot()) : OptionalInt.empty();
    }

    /**
     * 构造把<b>世界 chunk 坐标</b>映射到"是否路网"的 {@link RoadMask}，{@code origin} 为公会营地原点（chunk）。
     * 内部减去 origin 还原到布局坐标后复用 {@link #classify} 的路网规则，保证与生成器/铺路一致。
     * 整地铺路时传给整地端口，用于十字路口护栏抑制：水上桥边若外侧也是路，则不架栏（见 {@link RoadMask}）。
     */
    public RoadMask roadMask(int originChunkX, int originChunkZ) {
        return (cx, cz) -> classify(cx - originChunkX, cz - originChunkZ).isRoad();
    }

    /**
     * 成员 slot 庄园的<b>四周环路</b>条带：绕庄园 footprint 铺一圈宽 {@code roadChunks} 的路框。
     * 早期只铺右(+x)+下(+z)两条 L 形，庄园的 -x/-z 两侧路要等相邻格有成员才被铺（导致"负 z 轴没铺路"）；
     * 改成整圈后，每块庄园认领即四周环路，与邻格 through-road 在共享边自然重合（重复铺幂等无副作用）。
     */
    public java.util.List<ChunkRegion> roadStripsFor(int slot) {
        ChunkRegion p = plotRegion(slot);
        return ringStrips(p.minChunkX(), p.minChunkZ(), p.maxChunkX(), p.maxChunkZ());
    }

    /**
     * 主城<b>四周环路</b>的道路条带：绕主城整格 footprint([0..plotChunks-1]²) 铺一圈宽 {@code roadChunks} 的路框。
     * 主城占整个中心格(与成员格同尺寸)，此框正好落在网格 through-road 上、与成员路网无缝拼接。
     * 主城不是成员 slot、不会被 {@link #roadStripsFor} 覆盖，故由建会流程单独铺（幂等：与邻格路在共享边重合）。
     */
    public java.util.List<ChunkRegion> mainCityRoadStrips() {
        return ringStrips(0, 0, plot - 1, plot - 1);
    }

    /**
     * 绕方形 footprint [minX..maxX]×[minZ..maxZ] 外侧铺一圈宽 {@code roadChunks} 的路框，
     * 返回四条不重叠矩形（上/下满宽含角，左/右取中段）。roadChunks=0 返回空。庄园与主城共用。
     */
    private java.util.List<ChunkRegion> ringStrips(int minX, int minZ, int maxX, int maxZ) {
        int r = config.roadChunks();
        if (r <= 0) {
            return java.util.List.of();
        }
        ChunkRegion north = new ChunkRegion(minX - r, minZ - r, maxX + r, minZ - 1);
        ChunkRegion south = new ChunkRegion(minX - r, maxZ + 1, maxX + r, maxZ + r);
        ChunkRegion west = new ChunkRegion(minX - r, minZ, minX - 1, maxZ);
        ChunkRegion east = new ChunkRegion(maxX + 1, minZ, maxX + r, maxZ);
        return java.util.List.of(north, south, west, east);
    }

    /** 成员 slot → 其满级庄园的整 chunk 范围（P×P）。 */
    public ChunkRegion plotRegion(int slot) {
        if (slot < 0) {
            throw new IllegalArgumentException("slot < 0: " + slot);
        }
        SpiralIndex.GridCell cell = SpiralIndex.toCell(base + slot);
        int ox = cell.x() * pitch;
        int oz = cell.z() * pitch;
        return new ChunkRegion(ox, oz, ox + plot - 1, oz + plot - 1);
    }

    /**
     * @deprecated 庄园等级不再决定范围（改"额度 + 自由解锁"，可建范围请用 {@code Manor.unlockedChunks} 判定）。
     * 本方法保留仅为兼容<b>面积类</b>调用（实体计数 / 掉落上限 / 粒子边框 / 搬家复制 / 传送中心）——按既定
     * 选择一律返回<b>满级整块</b> {@link #plotRegion}。
     */
    @Deprecated
    public ChunkRegion activeRegion(int slot, int manorLevel) {
        return plotRegion(slot);
    }

    /**
     * 主城<b>领地</b>整 chunk 范围 = 中心格(cell 0)的整块 plot footprint [0..plotChunks-1]²（锚在 (0,0)，
     * 与成员庄园 {@link #plotRegion} 同尺寸）。<b>注意</b>：这是城的<b>territory</b>(归属/保护/水采样/围墙范围)，
     * 不是"能解锁多少"——后者由 {@code mainCityMaxChunks²} 额度决定，超出额度的城地属本范围但保持未解锁。
     * 成员庄园一律排在 cell 0 之外；世界边界以螺旋中心为准。
     */
    public ChunkRegion mainCityRegion() {
        return new ChunkRegion(0, 0, plot - 1, plot - 1);
    }

    /**
     * 给定公会等级下<b>当前实际</b>主城范围（角落锚定，从 initial 长到 max，⊆ {@link #mainCityRegion()}）。
     * 用于按等级铺城/整地/围墙；当前与最大之间那圈是"留给未来扩城"的预留空地。
     */
    public ChunkRegion currentCityRegion(int guildLevel, int maxGuildLevel) {
        int c = config.cityChunksAtLevel(guildLevel, maxGuildLevel);
        return new ChunkRegion(0, 0, c - 1, c - 1);
    }

    /** 世界出生点所在方块（主城锚定角 = cell 0 最小角所在 chunk 的中心，故出生点在角落）。 */
    public int spawnBlockX() {
        return 8;
    }

    public int spawnBlockZ() {
        return 8;
    }

    // ---- 世界边界（WorldBorder）派生 ----

    /**
     * 要把 {@code reservedSlots} 个成员庄园全部圈进去时，世界边界需覆盖到的外环（格）。
     * {@code reservedSlots} 由上层给出 = max(已分配, 当前公会等级的名额容量)，
     * 这样边界按"当前等级能容纳多少人"画出预留空地，公会升级放开更多名额时边界随之外扩。
     */
    public int borderRingCells(int reservedSlots) {
        return reservedSlots > 0
                ? SpiralIndex.ringOf(base + reservedSlots - 1)
                : 0; // 无成员时也至少圈住主城(cell 0) + margin
    }

    /** 世界边界中心方块 X（= 螺旋中心 cell 0 的中心，使边界对称圈住四周成员环；≠出生点角落）。 */
    public int borderCenterBlockX() {
        return pitch * 8;
    }

    public int borderCenterBlockZ() {
        return pitch * 8;
    }

    /** 世界边界全宽（方块），以中心向四周覆盖到外环庄园外沿 + margin（取偏宽的安全上界）。 */
    public double borderSizeBlocks(int reservedSlots) {
        int ring = borderRingCells(reservedSlots);
        int outerChunks = ring * pitch + plot + config.marginChunks();
        return (double) outerChunks * 2 * 16;
    }

    public int base() {
        return base;
    }

    public int pitchChunks() {
        return pitch;
    }
}
