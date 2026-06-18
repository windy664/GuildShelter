package org.windy.guildshelter.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.bukkit.plugin.Plugin;
import org.windy.guildshelter.domain.port.ManorMover;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * NeoForge 侧搬家实现：chunk 级复制，TileEntity NBT 完整保留。
 * 直接操作 NMS chunk，AE2/RS2/Mekanism 等 mod 数据安全。
 */
public final class NeoForgeManorMover implements ManorMover {

    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private final Plugin plugin;
    private final Logger logger;

    public NeoForgeManorMover(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public boolean copyRegion(String fromWorld, int fromCX, int fromCZ, int sizeChunks,
                              String toWorld, int toCX, int toCZ) {
        ServerLevel srcLevel = resolveLevel(fromWorld);
        ServerLevel dstLevel = resolveLevel(toWorld);
        if (srcLevel == null || dstLevel == null) {
            logger.warning("[GuildShelter] NeoForge 搬家失败：世界不存在");
            return false;
        }

        try {
            for (int dx = 0; dx < sizeChunks; dx++) {
                for (int dz = 0; dz < sizeChunks; dz++) {
                    copyChunk(srcLevel, fromCX + dx, fromCZ + dz,
                              dstLevel, toCX + dx, toCZ + dz);
                }
            }
            logger.info("[GuildShelter] NeoForge 搬家复制完成: " + fromWorld
                    + " chunk(" + fromCX + "," + fromCZ + ") size=" + sizeChunks
                    + " → " + toWorld + " chunk(" + toCX + "," + toCZ + ")");
            return true;
        } catch (Exception e) {
            logger.warning("[GuildShelter] NeoForge 搬家复制失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void copyChunk(ServerLevel srcLevel, int srcCX, int srcCZ,
                           ServerLevel dstLevel, int dstCX, int dstCZ) {
        LevelChunk srcChunk = srcLevel.getChunk(srcCX, srcCZ);
        LevelChunk dstChunk = dstLevel.getChunk(dstCX, dstCZ);

        int minY = srcLevel.getMinY();
        int maxY = srcLevel.getMaxY();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos srcPos = new BlockPos((srcCX << 4) + x, y, (srcCZ << 4) + z);
                    BlockPos dstPos = new BlockPos((dstCX << 4) + x, y, (dstCZ << 4) + z);

                    BlockState state = srcChunk.getBlockState(srcPos);
                    if (state.isAir()) continue;

                    // 用 level.setBlock（int flags），不用 chunk.setBlockState（boolean）
                    dstLevel.setBlock(dstPos, state, FLAGS);

                    // 复制 TileEntity（AE2/RS2/Mekanism 数据全在这里）
                    BlockEntity srcBE = srcChunk.getBlockEntity(srcPos);
                    if (srcBE != null) {
                        CompoundTag nbt = srcBE.saveWithFullMetadata(dstLevel.registryAccess());
                        nbt.putInt("x", dstPos.getX());
                        nbt.putInt("y", dstPos.getY());
                        nbt.putInt("z", dstPos.getZ());
                        BlockEntity dstBE = BlockEntity.loadStatic(dstPos, state, nbt, dstLevel.registryAccess());
                        if (dstBE != null) {
                            dstChunk.setBlockEntity(dstBE);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void clearRegion(String world, int minCX, int minCZ, int maxCX, int maxCZ) {
        ServerLevel level = resolveLevel(world);
        if (level == null) return;

        try {
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    clearChunk(level, cx, cz);
                }
            }
            logger.info("[GuildShelter] NeoForge 搬家清空完成");
        } catch (Exception e) {
            logger.warning("[GuildShelter] NeoForge 搬家清空失败: " + e.getMessage());
        }
    }

    private void clearChunk(ServerLevel level, int cx, int cz) {
        LevelChunk chunk = level.getChunk(cx, cz);
        int minY = level.getMinY();
        int maxY = level.getMaxY();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos((cx << 4) + x, y, (cz << 4) + z);
                    if (!chunk.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                }
            }
        }
    }

    @Override
    public List<String> detectRisks(String world, int minCX, int minCZ, int maxCX, int maxCZ) {
        ServerLevel level = resolveLevel(world);
        if (level == null) return List.of();

        List<String> risks = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        String currentDim = level.dimension().identifier().toString();

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                for (var entry : chunk.getBlockEntities().entrySet()) {
                    BlockEntity be = entry.getValue();
                    try {
                        CompoundTag nbt = be.saveWithFullMetadata(level.registryAccess());
                        if (hasCrossDimensionRef(nbt, currentDim)) {
                            String modId = be.getClass().getName();
                            int dot = modId.indexOf('.');
                            modId = dot > 0 ? modId.substring(0, dot) : modId;
                            if (seen.add(modId)) {
                                risks.add("§c⚠ " + modId + " — 存在跨维度引用，搬家后可能需要重新配置");
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        if (risks.isEmpty()) {
            risks.add("§a✓ 未检测到跨维度引用，mod 数据将完整保留");
        }
        return risks;
    }

    private boolean hasCrossDimensionRef(CompoundTag nbt, String currentDim) {
        for (String key : new String[]{"dimension", "Dimension", "dim", "Dim", "targetDim", "linkedDimension"}) {
            if (nbt.contains(key)) {
                try {
                    // NeoForge 26: getString returns Optional<String>
                    Object val = nbt.getString(key);
                    String str = val instanceof Optional<?> opt && opt.isPresent() ? opt.get().toString() : val.toString();
                    if (!str.isEmpty() && !str.equals(currentDim)) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        }
        // 递归检查嵌套 CompoundTag（getAllKeys 在 NeoForge 26 可能改名，用反射兜底）
        try {
            java.util.Set<String> keys = getCompoundTagKeys(nbt);
            if (keys != null) {
                for (String key : List.copyOf(keys)) {
                    try {
                        var tag = nbt.get(key);
                        if (tag instanceof CompoundTag nested) {
                            if (hasCrossDimensionRef(nested, currentDim)) return true;
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** 获取 CompoundTag 的所有 key（兼容 NeoForge 26 方法名变化）。全反射，不依赖编译期方法名。 */
    @SuppressWarnings("unchecked")
    private static java.util.Set<String> getCompoundTagKeys(CompoundTag nbt) {
        // NeoForge 26 CompoundTag 可能叫 getAllKeys / keys / keySet，反射尝试
        for (String methodName : new String[]{"getAllKeys", "keys", "keySet"}) {
            try {
                var method = nbt.getClass().getMethod(methodName);
                return (java.util.Set<String>) method.invoke(nbt);
            } catch (Exception ignored) {}
        }
        return java.util.Set.of();
    }

    private ServerLevel resolveLevel(String worldName) {
        for (ServerLevel level : ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
            Identifier id = level.dimension().identifier();
            if (id.getPath().equalsIgnoreCase(worldName) || id.toString().equalsIgnoreCase(worldName)) {
                return level;
            }
        }
        try {
            org.bukkit.World bukkitWorld = plugin.getServer().getWorld(worldName);
            if (bukkitWorld != null) {
                Method getHandle = bukkitWorld.getClass().getMethod("getHandle");
                Object handle = getHandle.invoke(bukkitWorld);
                if (handle instanceof ServerLevel sl) return sl;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
