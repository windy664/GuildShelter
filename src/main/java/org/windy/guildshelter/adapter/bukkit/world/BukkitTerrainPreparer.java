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

    private final Plugin plugin;

    public BukkitTerrainPreparer(Plugin plugin) {
        this.plugin = plugin;
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
                    int n = 0;
                    while (n < COLUMNS_PER_TICK && !queue.isEmpty()) {
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
    public void surfaceRoad(String worldName, ChunkRegion region) {
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
                int n = 0;
                while (n < COLUMNS_PER_TICK && !queue.isEmpty()) {
                    int[] c = queue.poll();
                    boolean edge = narrowIsX ? (c[0] == minX || c[0] == maxX)
                                             : (c[1] == minZ || c[1] == maxZ);
                    int r = pathColumn(world, c[0], c[1], edge);
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

    /**
     * 把一列道路铺好：向下穿过并清掉植被/树/雪，定位真正的自然地面铺土径；
     * 遇水改架<b>木板栈桥</b>（保留桥下水源，edge 列加栅栏护栏）；纯虚空列跳过。
     *
     * @return 1=铺了土径 2=架了桥 0=跳过（纯虚空列），供调用方统计。
     */
    private int pathColumn(World world, int x, int z, boolean edge) {
        world.loadChunk(x >> 4, z >> 4, true); // 道路条带常在地皮远端，确保区块已生成再操作
        int min = world.getMinHeight();
        int y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        while (y > min) {
            Block b = world.getBlockAt(x, y, z);
            if (isNaturalGround(b.getType())) {
                b.setType(Material.DIRT_PATH, false); // 自然地面顶层→土径
                return 1;
            }
            if (b.isLiquid()) {
                bridgeColumn(world, x, y, z, edge); // 水面：架桥而非铺路/填水
                return 2;
            }
            if (b.getType() != Material.AIR) {
                b.setType(Material.AIR, false); // 清掉植被/树木/积雪
            }
            y--;
        }
        return 0; // 落到底仍没遇到地面（纯虚空列）：不铺。
    }

    /** 在水面那一列架木板桥面（保留桥下水源）；edge 列在桥面上加栅栏护栏。木材按群系挑。 */
    private void bridgeColumn(World world, int x, int waterTopY, int z, boolean edge) {
        Material[] wood = woodFor(world, x, waterTopY, z); // [0]=木板 [1]=栅栏
        world.getBlockAt(x, waterTopY, z).setType(wood[0], false); // 顶层水→木板，下方的水保留
        if (edge) {
            world.getBlockAt(x, waterTopY + 1, z).setType(wood[1], true); // 护栏：带物理让栅栏相连
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

    /** 自然地面 = 实心方块且非原木/树叶（避免把路铺到树干/树冠上）。 */
    private static boolean isNaturalGround(Material m) {
        return m.isSolid() && !Tag.LOGS.isTagged(m) && !Tag.LEAVES.isTagged(m);
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
