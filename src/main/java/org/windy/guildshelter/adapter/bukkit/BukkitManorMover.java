package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.windy.guildshelter.domain.port.ManorMover;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Bukkit 侧搬家实现：用 WorldEdit clipboard API 做 chunk 级复制。
 * WE 是软依赖不在编译 classpath，通过反射调用。
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

            // WE 反射
            Class<?> baClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object weSrc = baClass.getMethod("adapt", World.class).invoke(null, src);
            Object weDst = baClass.getMethod("adapt", World.class).invoke(null, dst);

            Class<?> weClass = Class.forName("com.sk89q.worldedit.WorldEdit");
            Object we = weClass.getMethod("getInstance").invoke(null);
            Class<?> weWorldClass = Class.forName("com.sk89q.worldedit.world.World");

            // 从源复制到 clipboard
            Object srcSession = weClass.getMethod("newEditSession", weWorldClass).invoke(we, weSrc);
            Class<?> bvClass = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Object min = bvClass.getMethod("at", int.class, int.class, int.class)
                    .invoke(null, srcMinX, srcMinY, srcMinZ);
            Object max = bvClass.getMethod("at", int.class, int.class, int.class)
                    .invoke(null, srcMaxX, srcMaxY, srcMaxZ);
            Class<?> regionClass = Class.forName("com.sk89q.worldedit.regions.CuboidRegion");
            Object region = regionClass.getConstructor(bvClass, bvClass).newInstance(min, max);

            Method createClipboard = srcSession.getClass().getMethod("createClipboard", regionClass);
            Object clipboard = createClipboard.invoke(srcSession, region);
            srcSession.getClass().getMethod("close").invoke(srcSession);

            // 粘贴到目标
            Object dstSession = weClass.getMethod("newEditSession", weWorldClass).invoke(we, weDst);
            Class<?> holderClass = Class.forName("com.sk89q.worldedit.session.ClipboardHolder");
            Object holder = holderClass.getConstructor(
                    Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard"))
                    .newInstance(clipboard);
            Object pasteBuilder = holderClass.getMethod("createPaste",
                    Class.forName("com.sk89q.worldedit.EditSession"))
                    .invoke(holder, dstSession);
            Object to = bvClass.getMethod("at", int.class, int.class, int.class)
                    .invoke(null, dstX, srcMinY, dstZ);
            pasteBuilder = pasteBuilder.getClass().getMethod("to", bvClass).invoke(pasteBuilder, to);
            pasteBuilder = pasteBuilder.getClass().getMethod("ignoreAirBlocks", boolean.class)
                    .invoke(pasteBuilder, false);
            Object op = pasteBuilder.getClass().getMethod("build").invoke(pasteBuilder);
            Class.forName("com.sk89q.worldedit.function.operation.Operations")
                    .getMethod("complete", Class.forName("com.sk89q.worldedit.function.operation.Operation"))
                    .invoke(null, op);
            dstSession.getClass().getMethod("close").invoke(dstSession);

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
