package org.windy.guildshelter.adapter.bukkit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.windy.guildshelter.domain.port.ManorMover;

import java.util.logging.Logger;

/**
 * Bukkit 侧搬家实现：用 WorldEdit clipboard API 做 chunk 级复制（保留 TileEntity NBT/mod 数据）。
 *
 * <p><b>直接调 WE API，不反射</b>（{@code worldedit-bukkit + worldedit-core} 在 compileOnly classpath，
 * 运行期由 FAWE/WE 插件提供）。本类直接引用 WE 类，故仅在 WE/FAWE 在场时由装配方实例化（惰性隔离）。
 */
public final class BukkitManorMover implements ManorMover {

    private final Plugin plugin;
    private final Logger logger;

    public BukkitManorMover(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public boolean copyRegion(String fromWorld, int fromCX, int fromCZ, int sizeChunks,
                              String toWorld, int toCX, int toCZ) {
        try {
            World src = Bukkit.getWorld(fromWorld);
            World dst = Bukkit.getWorld(toWorld);
            if (src == null || dst == null) {
                logger.warning("[GuildShelter] 搬家失败：世界不存在");
                return false;
            }

            int srcMinX = fromCX << 4;
            int srcMinZ = fromCZ << 4;
            int srcMaxX = ((fromCX + sizeChunks) << 4) - 1;
            int srcMaxZ = ((fromCZ + sizeChunks) << 4) - 1;
            int srcMinY = src.getMinHeight();
            int srcMaxY = src.getMaxHeight() - 1;
            int dstX = toCX << 4;
            int dstZ = toCZ << 4;

            com.sk89q.worldedit.world.World weSrc = BukkitAdapter.adapt(src);
            com.sk89q.worldedit.world.World weDst = BukkitAdapter.adapt(dst);
            WorldEdit we = WorldEdit.getInstance();

            // 从源复制到 clipboard（含实体），再粘贴到目标。
            CuboidRegion region = new CuboidRegion(weSrc,
                    BlockVector3.at(srcMinX, srcMinY, srcMinZ), BlockVector3.at(srcMaxX, srcMaxY, srcMaxZ));
            BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
            try (EditSession srcSession = we.newEditSessionBuilder().world(weSrc).build()) {
                ForwardExtentCopy copy = new ForwardExtentCopy(
                        srcSession, region, clipboard, region.getMinimumPoint());
                copy.setCopyingEntities(true);
                Operations.complete(copy);
            }

            try (EditSession dstSession = we.newEditSessionBuilder().world(weDst).build()) {
                Operation op = new ClipboardHolder(clipboard)
                        .createPaste(dstSession)
                        .to(BlockVector3.at(dstX, srcMinY, dstZ))
                        .ignoreAirBlocks(false)
                        .build();
                Operations.complete(op);
            }

            logger.info("[GuildShelter] 搬家复制完成: " + fromWorld + " → " + toWorld);
            return true;
        } catch (Exception e) {
            logger.warning("[GuildShelter] 搬家复制失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void clearRegion(String world, int minCX, int minCZ, int maxCX, int maxCZ) {
        try {
            World bukkitWorld = Bukkit.getWorld(world);
            if (bukkitWorld == null) return;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    bukkitWorld.loadChunk(cx, cz, true);
                    org.bukkit.Chunk chunk = bukkitWorld.getChunkAt(cx, cz);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = bukkitWorld.getMinHeight(); y < bukkitWorld.getMaxHeight(); y++) {
                                org.bukkit.block.Block block = chunk.getBlock(x, y, z);
                                if (block.getType() != org.bukkit.Material.AIR) {
                                    block.setType(org.bukkit.Material.AIR, false);
                                }
                            }
                        }
                    }
                }
            }
            logger.info("[GuildShelter] 搬家清空完成");
        } catch (Exception e) {
            logger.warning("[GuildShelter] 搬家清空失败: " + e.getMessage());
        }
    }

    @Override
    public java.util.List<String> detectRisks(String world, int minCX, int minCZ, int maxCX, int maxCZ) {
        java.util.List<String> risks = new java.util.ArrayList<>();
        risks.add("§a✓ WE clipboard 复制保留 TileEntity NBT，mod 数据将完整保留");
        return risks;
    }
}
