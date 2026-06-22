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
 * {@link TerrainPreparer} 的 Bukkit 实现：对庄园范围按列整地，主线程<b>分批</b>处理避免卡顿。
 *
 * <ul>
 *   <li>CLEAR_VEGETATION：保留自然地表高度，清掉地表（实心地面）以上的草/花/雪/树等。</li>
 *   <li>FLATTEN：把整块拉平到该区域中心的地面高度（上方削平、下方用泥土填、顶层铺草）。</li>
 * </ul>
 *
 * <p><b>写方块后端</b>（{@link BlockSink}）：读仍走 Bukkit（高度图/方块状态），写走 sink，
 * <b>强制 FAWE/WE</b>（{@link org.windy.guildshelter.adapter.fawe.FaweTerrainSink}，EditSession 批量提交+重光照）。
 * 已<b>砍掉原生 {@code setType} 兜底</b>：FAWE/WE 不在场时整地/铺路<b>跳过并告警</b>，请安装 FastAsyncWorldEdit。
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

    // ---- 写方块后端 -----------------------------------------------------------

    /**
     * 写方块原语：读走 Bukkit，写走此接口，唯一实现是 {@link org.windy.guildshelter.adapter.fawe.FaweTerrainSink}
     * （FAWE EditSession 批量提交+重光照）。<b>已砍掉原生 setType 兜底</b>，强制 FAWE。
     */
    public interface BlockSink {
        /** @param physics 是否触发邻居物理（墙/栅栏相连用 true）；FAWE 后端忽略此参数（批量统一侧效）。 */
        void set(int x, int y, int z, Material m, boolean physics);
        /** 提交本批：FAWE 后端在此 close EditSession 触发重发+重光照。 */
        void flush();
    }

    /** FAWE/WE 是否在场（探测一次缓存）。强制走 FAWE：不在场则整地/铺路跳过并告警。 */
    private static Boolean faweAvailable;
    private boolean faweAvailable() {
        if (faweAvailable == null) {
            boolean cls;
            try {
                Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                cls = true;
            } catch (Throwable t) {
                cls = false;
            }
            boolean plug = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
            faweAvailable = cls && plug;
            if (faweAvailable) {
                plugin.getLogger().info("[GuildShelter] 整地/铺路写方块后端: FAWE EditSession（强制，批量+重光照）");
            } else {
                // plugin.yml 已硬前置 FastAsyncWorldEdit，正常到不了这里；留作防御（如 FAWE 加载失败）。
                plugin.getLogger().severe("[GuildShelter] 未检测到 FastAsyncWorldEdit："
                        + "整地/铺路已强制走 FAWE，缺它将【跳过整地/铺路】。请安装 FastAsyncWorldEdit。");
            }
        }
        return faweAvailable;
    }

    /**
     * 选写方块后端：强制 FAWE。FAWE 不在场或创建失败 → 返回 {@code null}，调用方据此<b>跳过</b>本次整地/铺路
     * （不再有原生兜底）。
     */
    private BlockSink newSink(World world) {
        if (!faweAvailable()) {
            return null;
        }
        try {
            return new org.windy.guildshelter.adapter.fawe.FaweTerrainSink(world);
        } catch (Throwable t) {
            plugin.getLogger().severe("[GuildShelter] FAWE 整地后端创建失败，本次整地/铺路跳过（强制 FAWE，无原生兜底）: " + t);
            return null;
        }
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

        BlockSink sink = newSink(world);
        if (sink == null) {
            plugin.getLogger().severe("[GuildShelter] 整地跳过(Bukkit/" + worldName + "): 缺 FAWE/WorldEdit。");
            return;
        }
        long totalCols = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        plugin.getLogger().info("[GuildShelter] 整地开始(Bukkit/" + mode + "/" + (sync ? "同步" : "异步") + ") " + worldName
                + " 共 " + totalCols + " 列");
        long t0 = System.currentTimeMillis();

        if (sync) {
            // 同步模式：一次处理完（claim 时用，确保玩家到达时世界状态已稳定）
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                if (mode == TerrainPrepMode.CLEAR_VEGETATION) {
                    clearColumn(world, sink, c[0], c[1]);
                } else {
                    flattenColumn(world, sink, c[0], c[1], targetY);
                }
            }
            sink.flush(); // 提交整批 → FAWE 重发+重光照
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
                            clearColumn(world, sink, c[0], c[1]);
                        } else {
                            flattenColumn(world, sink, c[0], c[1], targetY);
                        }
                        n++;
                    }
                    sink.flush(); // 每 tick 提交本批：FAWE 重发+重光照，增量可见
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
        BlockSink sink = newSink(world);
        if (sink == null) {
            plugin.getLogger().severe("[GuildShelter] 铺路跳过(Bukkit/" + worldName + "): 缺 FAWE/WorldEdit。");
            return;
        }
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
                    int r = pathColumn(world, sink, c[0], c[1], outDx, outDz, roadMask);
                    if (r == 1) stat[0]++;
                    else if (r == 2) stat[1]++;
                    n++;
                }
                sink.flush(); // 每 tick 提交本批：FAWE 重发+重光照
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
        BlockSink sink = newSink(world);
        if (sink == null) {
            plugin.getLogger().severe("[GuildShelter] 主城围墙跳过(Bukkit/" + worldName + "): 缺 FAWE/WorldEdit。");
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
                    // 只在外侧那格是成员庄园（非路）时立墙：贴路的边自动留口，且永不踩到成员庄园。
                    if (!roadMask.isRoadChunk((c[0] + c[2]) >> 4, (c[1] + c[3]) >> 4)) {
                        wallColumn(world, sink, c[0], c[1]);
                        stat[0]++;
                    }
                    n++;
                }
                sink.flush();
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
    private void wallColumn(World world, BlockSink sink, int x, int z) {
        world.loadChunk(x >> 4, z >> 4, true);
        int min = world.getMinHeight();
        int y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        while (y > min) {
            Block b = world.getBlockAt(x, y, z);
            if (isNaturalGround(b.getType())) {
                for (int h = 1; h <= wallHeight; h++) {
                    sink.set(x, y + h, z, wallBlock, true); // 带物理让墙相连（FAWE 后端忽略，独立块）
                }
                return;
            }
            if (b.isLiquid()) {
                return; // 水面：不立墙
            }
            if (b.getType() != Material.AIR) {
                sink.set(x, y, z, Material.AIR, false);
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
    private int pathColumn(World world, BlockSink sink, int x, int z, int outDx, int outDz, RoadMask roadMask) {
        world.loadChunk(x >> 4, z >> 4, true); // 道路条带常在庄园远端，确保区块已生成再操作
        int min = world.getMinHeight();
        int y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        while (y > min) {
            Block b = world.getBlockAt(x, y, z);
            if (isAlreadyPaved(b.getType())) {
                return 1; // 已是路面/桥面（重复铺路）→ 原地保留，不再下挖（修"每升一级路面下沉一格"）
            }
            if (isNaturalGround(b.getType())) {
                sink.set(x, y, z, roadSurface, false); // 自然地面顶层→路面块
                return 1;
            }
            if (b.isLiquid()) {
                bridgeColumn(world, sink, x, y, z, outDx, outDz, roadMask); // 水面：架桥而非铺路/填水
                return 2;
            }
            if (b.getType() != Material.AIR) {
                sink.set(x, y, z, Material.AIR, false); // 清掉植被/树木/积雪/竹子等非整方块
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
    private void bridgeColumn(World world, BlockSink sink, int x, int waterTopY, int z, int outDx, int outDz, RoadMask roadMask) {
        // 桥面/护栏：config 指定则用之，否则(null)按群系自动选木。
        Material[] biome = (bridgeDeck == null || bridgeRail == null) ? woodFor(world, x, waterTopY, z) : null;
        Material deckBlock = bridgeDeck != null ? bridgeDeck : biome[0];
        Material railBlock = bridgeRail != null ? bridgeRail : biome[1];
        sink.set(x, waterTopY, z, deckBlock, false); // 顶层水→桥面，下方的水保留
        if ((outDx != 0 || outDz != 0)
                && !roadMask.isRoadChunk((x + outDx) >> 4, (z + outDz) >> 4)
                && world.getBlockAt(x + outDx, waterTopY, z + outDz).isLiquid()) {
            sink.set(x, waterTopY + 1, z, railBlock, true); // 外侧是水的真·桥边才加护栏（带物理让栅栏相连）
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
    private void clearColumn(World world, BlockSink sink, int x, int z) {
        int groundY = world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR);   // 实心地面顶（水下为河床/海床）
        int surfaceY = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE); // 含植被/树/水的顶
        for (int y = groundY + 1; y <= surfaceY; y++) {
            Block b = world.getBlockAt(x, y, z);
            if (b.isLiquid()) {
                continue; // 保留水/岩浆，避免破坏水源
            }
            sink.set(x, y, z, Material.AIR, false);
        }
    }

    /** 把该列拉平到 targetY：上方削成空气，下方用泥土补齐，顶层铺草。 */
    private void flattenColumn(World world, BlockSink sink, int x, int z, int targetY) {
        int surfaceY = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        for (int y = targetY + 1; y <= surfaceY; y++) {
            sink.set(x, y, z, Material.AIR, false);
        }
        int groundY = world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR);
        for (int y = groundY + 1; y < targetY; y++) {
            sink.set(x, y, z, Material.DIRT, false);
        }
        sink.set(x, targetY, z, Material.GRASS_BLOCK, false);
    }
}
