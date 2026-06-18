package org.windy.guildshelter.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.TerrainPrepMode;
import org.windy.guildshelter.domain.port.TerrainPreparer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.StringJoiner;

/**
 * {@link TerrainPreparer} 的 <b>NeoForge 原生实现</b>（混合端启用）。
 *
 * <p>为什么不复用 {@link org.windy.guildshelter.adapter.bukkit.world.BukkitTerrainPreparer}：
 * Youer 混合端上 Bukkit 的 {@code HeightMap}/{@code setType} 走的是兼容层，高度图常不准、
 * 物理刷新也时灵时不灵——表现为除草无效或误删。这里直接拿 {@link ServerLevel} 用原版
 * {@link Heightmap} 定位、{@code setBlock} 改方块，结果与原版世界生成一致，也能覆盖模组方块。
 *
 * <p>线程：所有方块操作必须在服务器主线程。批处理沿用 Bukkit 调度器（Youer 上即服务器 tick
 * 线程），与 Bukkit 实现完全相同的"每 tick 一批列"节流，避免卡顿。
 *
 * <p>除草<b>跳过液体</b>（{@code getFluidState 非空}），不破坏水源/岩浆——与 Bukkit 端同一修复。
 *
 * <p><b>需编译验证</b>（本项目 NeoForge API 历来以本地编译为准）：
 * <ul>
 *   <li>{@code level.getMinY()}：旧版叫 {@code getMinBuildHeight()}，按编译报错二选一。</li>
 *   <li>世界名→维度解析见 {@link #resolve(String)}：若公会世界的维度 id 不是
 *       {@code <namespace>:<worldName>}，启动日志会列出已加载维度，按需调整匹配。</li>
 * </ul>
 */
public final class NeoForgeTerrainPreparer implements TerrainPreparer {

    private static final int COLUMNS_PER_TICK = 256;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState GRASS_BLOCK = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState DIRT_PATH = Blocks.DIRT_PATH.defaultBlockState();

    /** 只发客户端、不触发邻居物理（等价 Bukkit 的 applyPhysics=false），批量整地避免连锁水流/掉落。 */
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private final Plugin plugin;
    /** 路面方块（config `road-surface-block` 可自定义，默认 dirt_path）。 */
    private final BlockState roadSurface;

    public NeoForgeTerrainPreparer(Plugin plugin) {
        this(plugin, "minecraft:dirt_path");
    }

    public NeoForgeTerrainPreparer(Plugin plugin, String roadBlockId) {
        this.plugin = plugin;
        this.roadSurface = parseBlock(roadBlockId, DIRT_PATH);
    }

    /** 解析 config 里的方块 id（如 minecraft:dirt_path / 模组方块）为 BlockState；无效则回退默认并告警。 */
    private BlockState parseBlock(String id, BlockState fallback) {
        try {
            Identifier rid = Identifier.parse(id);
            net.minecraft.world.level.block.Block block =
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(rid);
            // 注册表对未知 id 返回 AIR；避免把路面铺成空气。
            if (block == null || block == Blocks.AIR) {
                plugin.getLogger().warning("[GuildShelter] road-surface-block 未知方块: " + id + "，回退默认。");
                return fallback;
            }
            return block.defaultBlockState();
        } catch (Exception e) {
            plugin.getLogger().warning("[GuildShelter] road-surface-block 解析失败: " + id + " (" + e.getMessage() + ")，回退默认。");
            return fallback;
        }
    }

    /**
     * 写方块原语：读仍走原生 {@link ServerLevel}，写走此接口。WE 在场→{@link WeTerrainSink}
     * （在 {@link #flush()} 时整块重发 + 重光照，治幽灵方块）；否则→{@link NativeSink} 原生 setBlock。
     * 仅含 MC 类型，故纯 Bukkit/无 WE 环境也能正常加载。
     */
    interface BlockSink {
        void set(BlockPos pos, BlockState state);
        /** 提交本批：WE 后端在此 close EditSession 触发重发+重光照；原生后端为空操作。 */
        void flush();
    }

    /** 原生兜底：直接 {@code level.setBlock(pos, state, UPDATE_CLIENTS)}，与历史行为一致。 */
    private static final class NativeSink implements BlockSink {
        private final ServerLevel level;
        NativeSink(ServerLevel level) { this.level = level; }
        @Override public void set(BlockPos pos, BlockState state) { level.setBlock(pos, state, FLAGS); }
        @Override public void flush() { /* 原生增量包即时发出，无需收尾 */ }
    }

    /** WE 模组版是否在场（探测一次缓存，首次记录用了哪个后端便于排查幽灵方块）。 */
    private static Boolean weAvailable;
    private boolean weAvailable() {
        if (weAvailable == null) {
            try {
                Class.forName("com.sk89q.worldedit.neoforge.NeoForgeWorldEdit");
                weAvailable = Boolean.TRUE;
                plugin.getLogger().info("[GuildShelter] 整地/铺路写方块后端: WorldEdit EditSession（重发+重光照）");
            } catch (Throwable t) {
                weAvailable = Boolean.FALSE;
                plugin.getLogger().info("[GuildShelter] 整地/铺路写方块后端: 原生 setBlock（未检测到 WE 模组版 NeoForgeWorldEdit）");
            }
        }
        return weAvailable;
    }

    /** 选写方块后端：WE 在场用 WE（修幽灵方块+重光照），否则原生兜底。 */
    private BlockSink newSink(ServerLevel level) {
        if (weAvailable()) {
            try {
                return new WeTerrainSink(level);
            } catch (Throwable t) {
                plugin.getLogger().warning("[GuildShelter] WE 整地后端创建失败，回退原生 setBlock: " + t);
            }
        }
        return new NativeSink(level);
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
        ServerLevel level = resolve(worldName);
        if (level == null) {
            return;
        }
        int minX = region.minBlockX();
        int maxX = region.maxBlockX();
        int minZ = region.minBlockZ();
        int maxZ = region.maxBlockZ();

        int targetY = mode == TerrainPrepMode.FLATTEN
                ? topSolidY(level, (minX + maxX) / 2, (minZ + maxZ) / 2)
                : 0;

        long totalCols = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        plugin.getLogger().info("[GuildShelter] 整地开始(NeoForge/" + mode + "/" + (sync ? "同步" : "异步") + ") "
                + worldName + " 共 " + totalCols + " 列");
        long t0 = System.currentTimeMillis();
        Deque<int[]> queue = columns(minX, maxX, minZ, maxZ);
        BlockSink sink = newSink(level);

        if (sync) {
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                if (mode == TerrainPrepMode.CLEAR_VEGETATION) {
                    clearColumn(level, sink, c[0], c[1]);
                } else {
                    flattenColumn(level, sink, c[0], c[1], targetY);
                }
            }
            sink.flush(); // 提交整批 → WE 重发+重光照
            plugin.getLogger().info("[GuildShelter] 整地完成(NeoForge/同步) 共 " + totalCols + " 列, 耗时 "
                    + (System.currentTimeMillis() - t0) + "ms");
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    int n = 0;
                    while (n < COLUMNS_PER_TICK && !queue.isEmpty()) {
                        int[] c = queue.poll();
                        if (mode == TerrainPrepMode.CLEAR_VEGETATION) {
                            clearColumn(level, sink, c[0], c[1]);
                        } else {
                            flattenColumn(level, sink, c[0], c[1], targetY);
                        }
                        n++;
                    }
                    sink.flush(); // 每 tick 提交本批：WE 重发+重光照，增量可见
                    if (queue.isEmpty()) {
                        cancel();
                        plugin.getLogger().info("[GuildShelter] 整地完成(NeoForge/异步) 共 " + totalCols + " 列, 耗时 "
                                + (System.currentTimeMillis() - t0) + "ms");
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }
    }

    @Override
    public void surfaceRoad(String worldName, ChunkRegion region) {
        ServerLevel level = resolve(worldName);
        if (level == null) {
            return;
        }
        int minX = region.minBlockX();
        int maxX = region.maxBlockX();
        int minZ = region.minBlockZ();
        int maxZ = region.maxBlockZ();
        // 护栏放在路条带"窄轴"的两条长边上（竖条→x 两侧；横条→z 两侧）。
        boolean narrowIsX = (maxX - minX) <= (maxZ - minZ);
        long totalCols = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        plugin.getLogger().info("[GuildShelter] 铺路开始(NeoForge) " + worldName + " ("
                + minX + "," + minZ + ")~(" + maxX + "," + maxZ + ") 共 " + totalCols + " 列");
        long t0 = System.currentTimeMillis();
        int[] stat = new int[2]; // [0]=土径列 [1]=架桥列
        Deque<int[]> queue = columns(minX, maxX, minZ, maxZ);
        BlockSink sink = newSink(level);
        new BukkitRunnable() {
            @Override
            public void run() {
                int n = 0;
                while (n < COLUMNS_PER_TICK && !queue.isEmpty()) {
                    int[] c = queue.poll();
                    // 路带窄轴的两条长边 → 外侧单位向量(指向路外)；非边缘列为 (0,0)。架桥护栏据此判断外侧是否为水。
                    int outDx = 0, outDz = 0;
                    if (narrowIsX) {
                        if (c[0] == minX) outDx = -1;
                        else if (c[0] == maxX) outDx = 1;
                    } else {
                        if (c[1] == minZ) outDz = -1;
                        else if (c[1] == maxZ) outDz = 1;
                    }
                    int r = pathColumn(level, sink, c[0], c[1], outDx, outDz);
                    if (r == 1) stat[0]++;
                    else if (r == 2) stat[1]++;
                    n++;
                }
                sink.flush(); // 每 tick 提交本批：WE 重发+重光照
                if (queue.isEmpty()) {
                    cancel();
                    plugin.getLogger().info("[GuildShelter] 铺路完成(NeoForge) " + worldName
                            + " 土径 " + stat[0] + " 列, 架桥 " + stat[1] + " 列, 耗时 "
                            + (System.currentTimeMillis() - t0) + "ms");
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static Deque<int[]> columns(int minX, int maxX, int minZ, int maxZ) {
        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                queue.add(new int[]{x, z});
            }
        }
        return queue;
    }

    /** 清掉实心地面以上的植被/积雪/树木（保留自然起伏的地面；跳过水/岩浆，不破坏水源）。 */
    private void clearColumn(ServerLevel level, BlockSink sink, int x, int z) {
        int groundY = topSolidY(level, x, z);                                   // 实心地面顶（水下为河床/海床）
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1; // 含植被/树/水的顶
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = groundY + 1; y <= surfaceY; y++) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                continue; // 保留水/岩浆，避免破坏水源
            }
            if (!state.isAir()) {
                sink.set(pos, AIR);
            }
        }
    }

    /** 把该列拉平到 targetY：上方削成空气，下方用泥土补齐，顶层铺草。 */
    private void flattenColumn(ServerLevel level, BlockSink sink, int x, int z, int targetY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        for (int y = targetY + 1; y <= surfaceY; y++) {
            sink.set(pos.set(x, y, z), AIR);
        }
        int groundY = topSolidY(level, x, z);
        for (int y = groundY + 1; y < targetY; y++) {
            sink.set(pos.set(x, y, z), DIRT);
        }
        sink.set(pos.set(x, targetY, z), GRASS_BLOCK);
    }

    /**
     * 把一列道路铺好：向下穿过并清掉植被/树/雪，定位真正的自然地面铺土径；
     * 遇水改架<b>木板栈桥</b>（保留桥下水源，edge 列加栅栏护栏）；纯虚空列跳过。
     *
     * @return 1=铺了土径 2=架了桥 0=跳过（纯虚空列），供调用方统计。
     */
    private int pathColumn(ServerLevel level, BlockSink sink, int x, int z, int outDx, int outDz) {
        level.getChunk(x >> 4, z >> 4); // 道路条带常在地皮远端，确保区块已生成再操作
        int min = level.getMinY();
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (y > min) {
            pos.set(x, y, z);
            BlockState b = level.getBlockState(pos);
            if (isNaturalGround(level, pos, b)) {
                sink.set(pos, roadSurface); // 自然地面顶层→路面块
                return 1;
            }
            if (!b.getFluidState().isEmpty()) {
                bridgeColumn(level, sink, x, y, z, outDx, outDz); // 水面：架桥而非铺路/填水
                return 2;
            }
            if (!b.isAir()) {
                sink.set(pos, AIR); // 清掉植被/树木/积雪/竹子等非整方块
            }
            y--;
        }
        return 0; // 落到底仍没遇到地面（纯虚空列）：不铺。
    }

    /**
     * 在水面那一列架木板桥面（保留桥下水源）；木材按群系挑。
     *
     * <p>护栏只在「该列是路带窄轴边缘({@code outDx/outDz} 非 0) 且外侧那格确实是水」时加——
     * 这样十字路口、与陆地/另一条路相接处（外侧不是水）就不会被栅栏围住（修"十字路口围墙"）。
     *
     * <p>注：经 WE 后端时关了 NEIGHBORS，栅栏不会自动连成一条（呈独立栏杆）；这是为避免批量整地
     * 触发物理连锁的取舍，属水上桥面的次要观感。原生兜底亦统一 UPDATE_CLIENTS。
     */
    private void bridgeColumn(ServerLevel level, BlockSink sink, int x, int waterTopY, int z, int outDx, int outDz) {
        BlockState[] wood = woodFor(level, new BlockPos(x, waterTopY, z)); // [0]=木板 [1]=栅栏
        BlockPos deck = new BlockPos(x, waterTopY, z);
        sink.set(deck, wood[0]); // 顶层水→木板，下方的水保留
        if (outDx != 0 || outDz != 0) {
            BlockPos outside = new BlockPos(x + outDx, waterTopY, z + outDz);
            if (!level.getBlockState(outside).getFluidState().isEmpty()) {
                sink.set(deck.above(), wood[1]); // 外侧是水的真·桥边才加护栏
            }
        }
    }

    /** 按所在群系挑桥用木材：针叶林→云杉，丛林→丛林木，其余→橡木（用 BiomeTags，覆盖各变体）。 */
    private static BlockState[] woodFor(ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_TAIGA)) {
            return new BlockState[]{Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_FENCE.defaultBlockState()};
        }
        if (biome.is(BiomeTags.IS_JUNGLE)) {
            return new BlockState[]{Blocks.JUNGLE_PLANKS.defaultBlockState(), Blocks.JUNGLE_FENCE.defaultBlockState()};
        }
        return new BlockState[]{Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState()};
    }

    /** 实心地面顶的 y（OCEAN_FLOOR：忽略流体，水下取河床/海床）。 */
    private static int topSolidY(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
    }

    /**
     * 自然地面 = 完整实心方块且非原木/树叶（避免把路铺到树干/树冠上）。
     * 要求<b>整方块碰撞形状</b>(isCollisionShapeFullBlock)：竹子/仙人掌/甘蔗/作物等虽能挡移动但非整方块，
     * 不算地面 → 在 pathColumn 里会被清成空气、继续向下找真地面（修"竹子没除去就在其顶端铺路"）。
     */
    private static boolean isNaturalGround(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        return state.blocksMotion()
                && state.isCollisionShapeFullBlock(level, pos)
                && !state.is(BlockTags.LOGS)
                && !state.is(BlockTags.LEAVES);
    }

    /**
     * 世界名 → {@link ServerLevel}。混合端上 {@code Bukkit.createWorld} 建出的维度 id 形如
     * {@code <namespace>:<worldName>}，这里按 path 或完整 id 大小写不敏感匹配；找不到则告警并
     * 列出已加载维度，便于按实际命名调整。
     */
    private ServerLevel resolve(String worldName) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null; // 服务器尚未就绪
        }
        for (ServerLevel level : server.getAllLevels()) {
            Identifier id = level.dimension().identifier(); // MC26: ResourceLocation→Identifier, location()→identifier()
            if (id.getPath().equalsIgnoreCase(worldName) || id.toString().equalsIgnoreCase(worldName)) {
                return level;
            }
        }
        StringJoiner dims = new StringJoiner(", ");
        for (ServerLevel level : server.getAllLevels()) {
            dims.add(level.dimension().identifier().toString());
        }
        plugin.getLogger().warning("[GuildShelter] NeoForge 整地找不到世界 '" + worldName
                + "'，已加载维度: [" + dims + "]");
        return null;
    }
}
