package org.windy.guildshelter.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
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

    public NeoForgeTerrainPreparer(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void prepare(String worldName, ChunkRegion region, TerrainPrepMode mode) {
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

        // FLATTEN 的目标高度 = 区域中心的实心地面高度（与 Bukkit 实现一致）
        int targetY = mode == TerrainPrepMode.FLATTEN
                ? topSolidY(level, (minX + maxX) / 2, (minZ + maxZ) / 2)
                : 0;

        Deque<int[]> queue = columns(minX, maxX, minZ, maxZ);
        new BukkitRunnable() {
            @Override
            public void run() {
                int n = 0;
                while (n < COLUMNS_PER_TICK && !queue.isEmpty()) {
                    int[] c = queue.poll();
                    if (mode == TerrainPrepMode.CLEAR_VEGETATION) {
                        clearColumn(level, c[0], c[1]);
                    } else {
                        flattenColumn(level, c[0], c[1], targetY);
                    }
                    n++;
                }
                if (queue.isEmpty()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void surfaceRoad(String worldName, ChunkRegion region) {
        ServerLevel level = resolve(worldName);
        if (level == null) {
            return;
        }
        plugin.getLogger().info("[GuildShelter] 铺土径(NeoForge) " + worldName + " 区域 ("
                + region.minBlockX() + "," + region.minBlockZ() + ")~("
                + region.maxBlockX() + "," + region.maxBlockZ() + ")");
        Deque<int[]> queue = columns(region.minBlockX(), region.maxBlockX(),
                region.minBlockZ(), region.maxBlockZ());
        new BukkitRunnable() {
            @Override
            public void run() {
                int n = 0;
                while (n < COLUMNS_PER_TICK && !queue.isEmpty()) {
                    int[] c = queue.poll();
                    pathColumn(level, c[0], c[1]);
                    n++;
                }
                if (queue.isEmpty()) {
                    cancel();
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
    private void clearColumn(ServerLevel level, int x, int z) {
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
                level.setBlock(pos, AIR, FLAGS);
            }
        }
    }

    /** 把该列拉平到 targetY：上方削成空气，下方用泥土补齐，顶层铺草。 */
    private void flattenColumn(ServerLevel level, int x, int z, int targetY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        for (int y = targetY + 1; y <= surfaceY; y++) {
            level.setBlock(pos.set(x, y, z), AIR, FLAGS);
        }
        int groundY = topSolidY(level, x, z);
        for (int y = groundY + 1; y < targetY; y++) {
            level.setBlock(pos.set(x, y, z), DIRT, FLAGS);
        }
        level.setBlock(pos.set(x, targetY, z), GRASS_BLOCK, FLAGS);
    }

    /** 把一列道路顶层铺成土径：向下穿过并清掉植被/树/雪，定位真正的自然地面再铺；水/虚空跳过。 */
    private void pathColumn(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4); // 道路条带常在地皮远端，确保区块已生成再操作
        int min = level.getMinY();
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (y > min) {
            pos.set(x, y, z);
            BlockState b = level.getBlockState(pos);
            if (isNaturalGround(b)) {
                break;
            }
            if (!b.isAir()) {
                level.setBlock(pos, AIR, FLAGS); // 清掉植被/树木/积雪
            }
            y--;
        }
        BlockState ground = level.getBlockState(pos.set(x, y, z));
        if (!ground.getFluidState().isEmpty() || ground.isAir()) {
            return; // 水面/虚空不铺路
        }
        level.setBlock(pos.set(x, y, z), DIRT_PATH, FLAGS);
    }

    /** 实心地面顶的 y（OCEAN_FLOOR：忽略流体，水下取河床/海床）。 */
    private static int topSolidY(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
    }

    /** 自然地面 = 阻挡移动的实心方块且非原木/树叶（避免把路铺到树干/树冠上）。 */
    private static boolean isNaturalGround(BlockState state) {
        return state.blocksMotion()
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
