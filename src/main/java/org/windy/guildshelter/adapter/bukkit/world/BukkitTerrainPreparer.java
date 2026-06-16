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
        // FLATTEN 的目标高度 = 区域中心的实心地面高度
        int targetY = mode == TerrainPrepMode.FLATTEN
                ? world.getHighestBlockYAt((minX + maxX) / 2, (minZ + maxZ) / 2, HeightMap.OCEAN_FLOOR)
                : 0;

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
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void surfaceRoad(String worldName, ChunkRegion region) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        plugin.getLogger().info("[GuildShelter] 铺土径 " + worldName + " 区域 ("
                + region.minBlockX() + "," + region.minBlockZ() + ")~("
                + region.maxBlockX() + "," + region.maxBlockZ() + ")");
        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = region.minBlockX(); x <= region.maxBlockX(); x++) {
            for (int z = region.minBlockZ(); z <= region.maxBlockZ(); z++) {
                queue.add(new int[]{x, z});
            }
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                int n = 0;
                while (n < COLUMNS_PER_TICK && !queue.isEmpty()) {
                    int[] c = queue.poll();
                    pathColumn(world, c[0], c[1]);
                    n++;
                }
                if (queue.isEmpty()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** 把一列道路顶层铺成土径：向下穿过并清掉植被/树/雪，定位真正的自然地面再铺；水/虚空跳过。 */
    private void pathColumn(World world, int x, int z) {
        world.loadChunk(x >> 4, z >> 4, true); // 道路条带常在地皮远端，确保区块已生成再操作
        int min = world.getMinHeight();
        int y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        while (y > min) {
            Block b = world.getBlockAt(x, y, z);
            if (isNaturalGround(b.getType())) {
                break;
            }
            if (b.getType() != Material.AIR) {
                b.setType(Material.AIR, false); // 清掉植被/树木/积雪
            }
            y--;
        }
        Block ground = world.getBlockAt(x, y, z);
        if (ground.isLiquid() || ground.getType().isAir()) {
            return; // 水面/虚空不铺路
        }
        ground.setType(Material.DIRT_PATH, false);
    }

    /** 自然地面 = 实心方块且非原木/树叶（避免把路铺到树干/树冠上）。 */
    private static boolean isNaturalGround(Material m) {
        return m.isSolid() && !Tag.LOGS.isTagged(m) && !Tag.LEAVES.isTagged(m);
    }

    /** 清掉实心地面以上的植被/积雪/树木（保留自然起伏的地面）。 */
    private void clearColumn(World world, int x, int z) {
        int groundY = world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR);   // 实心地面顶
        int surfaceY = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE); // 含植被/树的顶
        for (int y = groundY + 1; y <= surfaceY; y++) {
            world.getBlockAt(x, y, z).setType(Material.AIR, false);
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
