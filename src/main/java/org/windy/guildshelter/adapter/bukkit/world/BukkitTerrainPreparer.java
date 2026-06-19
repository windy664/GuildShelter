package org.windy.guildshelter.adapter.bukkit.world;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.TerrainPrepMode;
import org.windy.guildshelter.domain.layout.RoadMask;
import org.windy.guildshelter.domain.port.TerrainPreparer;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * {@link TerrainPreparer} 的 Bukkit 实现：对地皮范围按列整地，主线程<b>分批</b>处理避免卡顿。
 *
 * <ul>
 *   <li>CLEAR_VEGETATION：保留自然地表高度，清掉地表（实心地面）以上的草/花/雪/树等。</li>
 *   <li>FLATTEN：把整块拉平到该区域中心的地面高度（上方削平、下方用泥土填、顶层铺草）。</li>
 * </ul>
 */
public final class BukkitTerrainPreparer implements TerrainPreparer {

    private static final int COLUMNS_PER_TICK = 256;
    /** 每 tick 在整地/铺路上花的墙钟上限(纳秒)：到点立刻停、下 tick 续，硬性防卡帧（比单纯限列数更稳，
     *  能挡住单个区块加载的偶发长耗时）。8ms ≪ 50ms 的 tick 预算，留足时间给游戏本身。 */
    private static final long MAX_NANOS_PER_TICK = 8_000_000L;

    private final Plugin plugin;
    /** 陆地路面方块（config `road-surface-block`，默认土径）。 */
    private final Material roadSurface;
    /** 水面桥桥面 / 护栏；null = 按群系自动选木（config `auto`）。 */
    private final Material bridgeDeck;
    private final Material bridgeRail;
    /** 主城围墙块（config `city-wall.block`，默认圆石墙）+ 层高 + 是否启用。 */
    private final Material wallBlock;
    private final int wallHeight;
    private final boolean wallEnabled;

    public BukkitTerrainPreparer(Plugin plugin) {
        this(plugin, "minecraft:dirt_path", "auto", "auto", true, "minecraft:cobblestone_wall", 1);
    }

    public BukkitTerrainPreparer(Plugin plugin, String roadBlockId, String bridgeBlockId, String bridgeRailId,
                                 boolean wallEnabled, String wallBlockId, int wallHeight) {
        this.plugin = plugin;
        this.roadSurface = parseBlock(roadBlockId, Material.DIRT_PATH, "road-surface-block");
        this.bridgeDeck = parseBridge(bridgeBlockId, "road-bridge-block");
        this.bridgeRail = parseBridge(bridgeRailId, "road-bridge-rail-block");
        this.wallEnabled = wallEnabled;
        this.wallBlock = parseBlock(wallBlockId, Material.COBBLESTONE_WALL, "city-wall.block");
        this.wallHeight = Math.max(1, wallHeight);
    }

    /** 桥配置：auto/空 → null（按群系自动）；否则解析方块，无效也回退 null（仍走自动）。 */
    private Material parseBridge(String id, String key) {
        if (id == null || id.isBlank() || id.equalsIgnoreCase("auto")) {
            return null;
        }
        return parseBlock(id, null, key);
    }

    /** 解析 config 里的方块 id 为 Material；无效则回退 fallback 并告警。 */
    private Material parseBlock(String id, Material fallback, String key) {
        Material m = Material.matchMaterial(id);
        if (m == null || !m.isBlock()) {
            plugin.getLogger().warning("[GuildShelter] " + key + " 无效方块: " + id + "，回退默认。");
            return fallback;
        }
        return m;
    }

    @Override
    public void prepare(String worldName, ChunkRegion region, TerrainPrepMode mode) {
        prepare(worldName, region, mode, false);
    }

    @Override
    public void prepare(String worldName, ChunkRegion region, TerrainPrepMode mode, boolean sync) {
        if (mode == TerrainPrepMode.NONE) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        int minX = region.minBlockX();
        int maxX = region.maxBlockX();
        int minZ = region.minBlockZ();
        int maxZ = region.maxBlockZ();

        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                queue.add(new int[]{x, z});
            }
        }
        int targetY = mode == TerrainPrepMode.FLATTEN
                ? world.getHighestBlockYAt((minX + maxX) / 2, (minZ + maxZ) / 2, HeightMap.OCEAN_FLOOR)
                : 0;

        long totalCols = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        plugin.getLogger().info("[GuildShelter] 整地开始(Bukkit/" + mode + "/" + (sync ? "同步" : "异步") + ") " + worldName
                + " 共 " + totalCols + " 列");
        long t0 = System.currentTimeMillis();

        if (sync) {
            // 同步模式：一次处理完（claim 时用，确保玩家到达时世界状态已稳定）
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                if (mode == TerrainPrepMode.CLEAR_VEGETATION) {
                    clearColumn(world, c[0], c[1]);
                } else {
                    flattenColumn(world, c[0], c[1], targetY);
                }
            }
            plugin.getLogger().info("[GuildShelter] 整地完成(同步) 共 " + totalCols + " 列, 耗时 "
                    + (System.currentTimeMillis() - t0) + "ms");
        } else {
            // 异步模式：分批处理（升级/重置时用，不卡主线程）
            new BukkitRunnable() {
                @Override
                public void run() {
                    long tickStart = System.nanoTime();
                    int n = 0;
                    while (n < COLUMNS_PER_TICK && !queue.isEmpty()
                            && System.nanoTime() - tickStart < MAX_NANOS_PER_TICK) {
                        int[] c = queue.poll();
                        if (mode == TerrainPrepMode.CLEAR_VEGETATION) {
                            clearColumn(world, c[0], c[1]);
                        } else {
                            flattenColumn(world, c[0], c[1], targetY);
                        }
                        n++;
                    }
                    if (queue.isEmpty()) {
                        cancel();
                        plugin.getLogger().info("[GuildShelter] 整地完成(异步) 共 " + totalCols + " 列, 耗时 "
                                + (System.currentTimeMillis() - t0) + "ms");
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }
    }

    @Override
    public void surfaceRoad(String worldName, ChunkRegion region, RoadMask roadMask) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        int minX = region.minBlockX();
        int maxX = region.maxBlockX();
        int minZ = region.minBlockZ();
        int maxZ = region.maxBlockZ();
        // 护栏放在路条带"窄轴"的两条长边上（竖条→x 两侧；横条→z 两侧）。
        boolean narrowIsX = (maxX - minX) <= (maxZ - minZ);
        long totalCols = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        plugin.getLogger().info("[GuildShelter] 铺路开始(Bukkit) " + worldName + " ("
                + minX + "," + minZ + ")~(" + maxX + "," + maxZ + ") 共 " + totalCols + " 列");
        long t0 = System.currentTimeMillis();
        int[] stat = new int[2]; // [0]=土径列 [1]=架桥列
        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                queue.add(new int[]{x, z});
            }
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                long tickStart = System.nanoTime();
                int n = 0;
                while (n < COLUMNS_PER_TICK && !queue.isEmpty()
                        && System.nanoTime() - tickStart < MAX_NANOS_PER_TICK) {
                    int[] c = queue.poll();
                    // 路带窄轴两条长边 → 外侧单位向量(指向路外)；非边缘列 (0,0)。架桥护栏据此判断外侧是否为水。
                    int outDx = 0, outDz = 0;
                    if (narrowIsX) {
                        if (c[0] == minX) outDx = -1;
                        else if (c[0] == maxX) outDx = 1;
                    } else {
                        if (c[1] == minZ) outDz = -1;
                        else if (c[1] == maxZ) outDz = 1;
                    }
                    int r = pathColumn(world, c[0], c[1], outDx, outDz, roadMask);
                    if (r == 1) stat[0]++;
                    else if (r == 2) stat[1]++;
                    n++;
                }
                if (queue.isEmpty()) {
                    cancel();
                    plugin.getLogger().info("[GuildShelter] 铺路完成(Bukkit) " + worldName
                            + " 土径 " + stat[0] + " 列, 架桥 " + stat[1] + " 列, 耗时 "
                            + (System.currentTimeMillis() - t0) + "ms");
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void encloseMainCity(String worldName, ChunkRegion region, RoadMask roadMask) {
        if (!wallEnabled || wallBlock == null) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        int minX = region.minBlockX(), maxX = region.maxBlockX();
        int minZ = region.minBlockZ(), maxZ = region.maxBlockZ();
        Deque<int[]> queue = perimeter(minX, maxX, minZ, maxZ);
        long total = queue.size();
        plugin.getLogger().info("[GuildShelter] 主城围墙开始(Bukkit) " + worldName + " 周长 " + total + " 列");
        long t0 = System.currentTimeMillis();
        int[] stat = new int[1];
        new BukkitRunnable() {
            @Override
            public void run() {
                long tickStart = System.nanoTime();
                int n = 0;
                while (n < COLUMNS_PER_TICK && !queue.isEmpty()
                        && System.nanoTime() - tickStart < MAX_NANOS_PER_TICK) {
                    int[] c = queue.poll(); // {x, z, outDx, outDz}
                    // 只在外侧那格是成员地皮（非路）时立墙：贴路的边自动留口，且永不踩到成员地皮。
                    if (!roadMask.isRoadChunk((c[0] + c[2]) >> 4, (c[1] + c[3]) >> 4)) {
                        wallColumn(world, c[0], c[1]);
                        stat[0]++;
                    }
                    n++;
                }
                if (queue.isEmpty()) {
                    cancel();
                    plugin.getLogger().info("[GuildShelter] 主城围墙完成(Bukkit) " + worldName
                            + " 立墙 " + stat[0] + " 列, 耗时 " + (System.currentTimeMillis() - t0) + "ms");
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** 最大主城矩形四条边的边块队列，元素 {@code {x, z, outDx, outDz}}（outD*=指向城外的单位向量）。 */
    private static Deque<int[]> perimeter(int minX, int maxX, int minZ, int maxZ) {
        Deque<int[]> q = new ArrayDeque<>();
        for (int z = minZ; z <= maxZ; z++) {
            q.add(new int[]{minX, z, -1, 0});
            q.add(new int[]{maxX, z, 1, 0});
        }
        for (int x = minX; x <= maxX; x++) {
            q.add(new int[]{x, minZ, 0, -1});
            q.add(new int[]{x, maxZ, 0, 1});
        }
        return q;
    }

    /**
     * 在一列上立围墙：向下穿过并清掉植被/树（含巨型蘑菇，复用 {@link #isNaturalGround}）定位自然地面，
     * 在其上叠 {@code wallHeight} 格围墙块（带物理让墙相连）；遇水面则不立。
     */
    private void wallColumn(World world, int x, int z) {
        world.loadChunk(x >> 4, z >> 4, true);
        int min = world.getMinHeight();
        int y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        while (y > min) {
            Block b = world.getBlockAt(x, y, z);
            if (isNaturalGround(b.getType())) {
                for (int h = 1; h <= wallHeight; h++) {
                    world.getBlockAt(x, y + h, z).setType(wallBlock, true);
                }
                return;
            }
            if (b.isLiquid()) {
                return; // 水面：不立墙
            }
            if (b.getType() != Material.AIR) {
                b.setType(Material.AIR, false);
            }
            y--;
        }
    }

    /**
     * 把一列道路铺好：向下穿过并清掉植被/树/雪，定位真正的自然地面铺土径；
     * 遇水改架<b>木板栈桥</b>（保留桥下水源，edge 列加栅栏护栏）；纯虚空列跳过。
     *
     * @return 1=铺了土径 2=架了桥 0=跳过（纯虚空列），供调用方统计。
     */
    private int pathColumn(World world, int x, int z, int outDx, int outDz, RoadMask roadMask) {
        world.loadChunk(x >> 4, z >> 4, true); // 道路条带常在地皮远端，确保区块已生成再操作
        int min = world.getMinHeight();
        int y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        while (y > min) {
            Block b = world.getBlockAt(x, y, z);
            if (isAlreadyPaved(b.getType())) {
                return 1; // 已是路面/桥面（重复铺路）→ 原地保留，不再下挖（修"每升一级路面下沉一格"）
            }
            if (isNaturalGround(b.getType())) {
                b.setType(roadSurface, false); // 自然地面顶层→路面块
                return 1;
            }
            if (b.isLiquid()) {
                bridgeColumn(world, x, y, z, outDx, outDz, roadMask); // 水面：架桥而非铺路/填水
                return 2;
            }
            if (b.getType() != Material.AIR) {
                b.setType(Material.AIR, false); // 清掉植被/树木/积雪/竹子等非整方块
            }
            y--;
        }
        return 0; // 落到底仍没遇到地面（纯虚空列）：不铺。
    }

    /** 已是本插件铺的路面/桥面 → 重复铺路时原地停，避免逐级下沉/桥面叠土径。见 NeoForge 同名注释。 */
    private boolean isAlreadyPaved(Material m) {
        if (m == roadSurface) {
            return true;
        }
        if (bridgeDeck != null && m == bridgeDeck) {
            return true;
        }
        return m == Material.OAK_PLANKS || m == Material.SPRUCE_PLANKS || m == Material.JUNGLE_PLANKS;
    }

    /**
     * 在水面那一列架木板桥面（保留桥下水源）；木材按群系挑。
     * 护栏只在「边缘列、外侧那格确实是水、<b>且外侧不属于另一条路</b>」时加——"外侧是水"挡住与陆地相接处，
     * "外侧非路"({@code roadMask})再挡住<b>水上十字路口</b>(两条路在水面相交时外侧正是垂直那条路，
     * 旧逻辑只看水面会因铺路顺序把路口栏死)；闭式取模判定，与顺序无关。
     */
    private void bridgeColumn(World world, int x, int waterTopY, int z, int outDx, int outDz, RoadMask roadMask) {
        // 桥面/护栏：config 指定则用之，否则(null)按群系自动选木。
        Material[] biome = (bridgeDeck == null || bridgeRail == null) ? woodFor(world, x, waterTopY, z) : null;
        Material deckBlock = bridgeDeck != null ? bridgeDeck : biome[0];
        Material railBlock = bridgeRail != null ? bridgeRail : biome[1];
        world.getBlockAt(x, waterTopY, z).setType(deckBlock, false); // 顶层水→桥面，下方的水保留
        if ((outDx != 0 || outDz != 0)
                && !roadMask.isRoadChunk((x + outDx) >> 4, (z + outDz) >> 4)
                && world.getBlockAt(x + outDx, waterTopY, z + outDz).isLiquid()) {
            world.getBlockAt(x, waterTopY + 1, z).setType(railBlock, true); // 外侧是水的真·桥边才加护栏（带物理让栅栏相连）
        }
    }

    /** 按所在群系挑桥用木材：针叶/雪地→云杉，丛林→丛林木，其余→橡木。 */
    private static Material[] woodFor(World world, int x, int y, int z) {
        String biome = world.getBiome(x, y, z).getKey().getKey(); // 群系 id 路径（小写）
        if (biome.contains("taiga") || biome.contains("snowy") || biome.contains("frozen")
                || biome.contains("grove") || biome.contains("pine")) {
            return new Material[]{Material.SPRUCE_PLANKS, Material.SPRUCE_FENCE};
        }
        if (biome.contains("jungle") || biome.contains("bamboo")) {
            return new Material[]{Material.JUNGLE_PLANKS, Material.JUNGLE_FENCE};
        }
        return new Material[]{Material.OAK_PLANKS, Material.OAK_FENCE};
    }

    /**
     * 自然地面 = 完整不透光实心方块且非原木/树叶（避免把路铺到树干/树冠上）。
     * 加 {@code isOccluding}（整块不透光）排除竹子/仙人掌/甘蔗/作物等：它们 isSolid 但非整方块，
     * 不算地面 → 在 pathColumn 里被清成空气、继续向下找真地面（修"竹子没除去就在其顶端铺路"）。
     */
    private static boolean isNaturalGround(Material m) {
        return m.isSolid() && m.isOccluding()
                && !Tag.LOGS.isTagged(m) && !Tag.LEAVES.isTagged(m)
                && !isHugeFungus(m); // 巨型蘑菇/菌：整方块但属"树"，别在菌盖顶上铺路
    }

    /**
     * 巨型蘑菇/菌方块（蘑菇盖/柄、菌核、菌光）：整方块且不在 LOGS/LEAVES，无聚合标签，按具体方块列举。
     * {@link #isNaturalGround} 据此排除 → 在 pathColumn 里清成空气继续下探（修"蘑菇树顶铺路"，与竹子同源）。
     */
    private static boolean isHugeFungus(Material m) {
        return m == Material.RED_MUSHROOM_BLOCK
                || m == Material.BROWN_MUSHROOM_BLOCK
                || m == Material.MUSHROOM_STEM
                || m == Material.SHROOMLIGHT
                || m == Material.NETHER_WART_BLOCK
                || m == Material.WARPED_WART_BLOCK;
    }

    /** 清掉实心地面以上的植被/积雪/树木（保留自然起伏的地面；跳过水/岩浆，不破坏水源）。 */
    private void clearColumn(World world, int x, int z) {
        int groundY = world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR);   // 实心地面顶（水下为河床/海床）
        int surfaceY = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE); // 含植被/树/水的顶
        for (int y = groundY + 1; y <= surfaceY; y++) {
            Block b = world.getBlockAt(x, y, z);
            if (b.isLiquid()) {
                continue; // 保留水/岩浆，避免破坏水源
            }
            b.setType(Material.AIR, false);
        }
    }

    /** 把该列拉平到 targetY：上方削成空气，下方用泥土补齐，顶层铺草。 */
    private void flattenColumn(World world, int x, int z, int targetY) {
        int surfaceY = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        for (int y = targetY + 1; y <= surfaceY; y++) {
            world.getBlockAt(x, y, z).setType(Material.AIR, false);
        }
        int groundY = world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR);
        for (int y = groundY + 1; y < targetY; y++) {
            world.getBlockAt(x, y, z).setType(Material.DIRT, false);
        }
        world.getBlockAt(x, targetY, z).setType(Material.GRASS_BLOCK, false);
    }
}
