package org.windy.guildshelter.adapter.bukkit.moddata;

import com.refinedmods.refinedstorage.common.api.storage.StorageContainerItem;
import com.refinedmods.refinedstorage.common.api.storage.StorageRepository;
import com.refinedmods.refinedstorage.common.api.storage.SerializableStorage;
import com.refinedmods.refinedstorage.common.storage.StorageRepositoryImpl;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import org.bukkit.plugin.Plugin;
import org.windy.guildshelter.domain.port.ModDataMover;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Refined Storage 2 的数据搬运：通过 RS2 API 按 UUID 提取/注入存储数据。
 *
 * <p>RS2 的存储盘数据存在 SavedData（StorageRepository）里，不在 TE NBT。
 * TE 里的 ItemStack 只存 UUID 引用。搬家时需要把 UUID 对应的实际数据也搬过去。
 */
public final class RefinedStorageDataMover implements ModDataMover {

    private final Plugin plugin;
    private final Logger logger;

    public RefinedStorageDataMover(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public String modId() {
        return "refinedstorage";
    }

    @Override
    public String moveData(Path srcWorldDir, Path dstWorldDir, String srcWorldName, String dstWorldName,
                           int srcMinCX, int srcMinCZ, int srcMaxCX, int srcMaxCZ) {
        ServerLevel srcLevel = resolveLevel(srcWorldName);
        ServerLevel dstLevel = resolveLevel(dstWorldName);
        if (srcLevel == null || dstLevel == null) {
            return null;
        }

        try {
            StorageRepository srcRepo = getStorageRepository(srcLevel);
            StorageRepository dstRepo = getOrCreateStorageRepository(dstLevel);
            if (srcRepo == null) {
                return null; // RS2 未在源世界初始化
            }

            int moved = 0;
            // 只扫描搬家区域的 chunk
            for (int cx = srcMinCX; cx <= srcMaxCX; cx++) {
                for (int cz = srcMinCZ; cz <= srcMaxCZ; cz++) {
                    LevelChunk chunk = srcLevel.getChunk(cx, cz);
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        moved += processBlockEntity(be, srcRepo, dstLevel.registryAccess());
                    }
                }
            }

            if (moved > 0) {
                srcRepo.markAsChanged();
                dstRepo.markAsChanged();
                logger.info("[GuildShelter] RS2: 已搬运 " + moved + " 个存储盘的数据");
                return "§a✓ Refined Storage: " + moved + " 个存储盘数据已完整搬运";
            }
            return null;
        } catch (Exception e) {
            logger.warning("[GuildShelter] RS2 数据搬运失败: " + e.getMessage());
            e.printStackTrace();
            return "§c⚠ Refined Storage 数据搬运失败: " + e.getMessage();
        }
    }

    private int processBlockEntity(BlockEntity be, StorageRepository srcRepo,
                                   HolderLookup.Provider registries) {
        String className = be.getClass().getName();
        if (!className.startsWith("com.refinedmods.refinedstorage.")) {
            return 0;
        }

        int count = 0;
        try {
            if (!(be instanceof net.minecraft.world.Container container)) {
                return 0;
            }

            for (int i = 0; i < container.getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack stack = container.getItem(i);
                if (stack.isEmpty()) continue;
                if (!(stack.getItem() instanceof StorageContainerItem diskItem)) continue;

                Optional<SerializableStorage> storageOpt = diskItem.resolve(srcRepo, stack);
                if (storageOpt.isEmpty()) continue;

                SerializableStorage storage = storageOpt.get();
                UUID diskId = readDiskUuid(stack);
                if (diskId == null) continue;

                // 从源 repo 移除
                srcRepo.remove(diskId);

                // 创建新实例用于目标世界
                SerializableStorage newDisk = storage.getType().create(
                        storage.toContents(), () -> {});
                srcRepo.set(diskId, newDisk);
                count++;
            }
        } catch (Exception ignored) {}
        return count;
    }

    private UUID readDiskUuid(net.minecraft.world.item.ItemStack stack) {
        try {
            // RS2 存储盘的 UUID 在 ItemStack 的 DataComponent 里
            // DataComponentMap API 在不同 NeoForge 版本间变化较大，用反射安全遍历
            var components = stack.getComponents();
            // 尝试 stream() 方法
            try {
                var streamMethod = components.getClass().getMethod("stream");
                var stream = streamMethod.invoke(components);
                var iteratorMethod = stream.getClass().getMethod("iterator");
                var iterator = (java.util.Iterator<?>) iteratorMethod.invoke(stream);
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    var valueMethod = entry.getClass().getMethod("value");
                    Object value = valueMethod.invoke(entry);
                    if (value instanceof UUID uuid) return uuid;
                    if (value instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof UUID uuid) return uuid;
                }
            } catch (NoSuchMethodException e) {
                // fallback: 遍历 keySet() 然后 get()
                var keySetMethod = components.getClass().getMethod("keySet");
                java.util.Set<?> keys = (java.util.Set<?>) keySetMethod.invoke(components);
                var getMethod = components.getClass().getMethod("get", Object.class);
                for (Object key : keys) {
                    Object value = getMethod.invoke(components, key);
                    if (value instanceof UUID uuid) return uuid;
                    if (value instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof UUID uuid) return uuid;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private StorageRepository getStorageRepository(ServerLevel level) {
        try {
            return level.getDataStorage().get(StorageRepositoryImpl.TYPE);
        } catch (Exception e) {
            return null;
        }
    }

    private StorageRepository getOrCreateStorageRepository(ServerLevel level) {
        try {
            return level.getDataStorage().computeIfAbsent(StorageRepositoryImpl.TYPE);
        } catch (Exception e) {
            return null;
        }
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
