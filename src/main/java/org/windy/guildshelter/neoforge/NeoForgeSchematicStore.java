package org.windy.guildshelter.neoforge;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.bukkit.plugin.Plugin;
import org.windy.guildshelter.domain.port.SchematicStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge 侧 WorldEdit 实现：直接用 ServerLevel，不走 Bukkit 适配层。
 * 全部通过反射调用 WE API（软依赖）。混合端必须用此实现。
 */
public final class NeoForgeSchematicStore implements SchematicStore {

    private final Path dir;
    private final Plugin plugin;

    public NeoForgeSchematicStore(Path dir, Plugin plugin) {
        this.dir = dir;
        this.plugin = plugin;
        dir.toFile().mkdirs();
    }

    @Override
    public Path save(String worldName, String name, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        File file = dir.resolve(name + ".schem").toFile();
        ServerLevel level = resolveLevel(worldName);
        if (level == null) return null;
        try {
            // NeoForgeAdapter.adapt(level) → World（模组版，原生认模组方块）
            Object weWorld = adaptWorld(level);
            if (weWorld == null) return null;

            Class<?> weClass = Class.forName("com.sk89q.worldedit.WorldEdit");
            Object we = weClass.getMethod("getInstance").invoke(null);
            Object session = weClass.getMethod("newEditSession", Class.forName("com.sk89q.worldedit.world.World"))
                    .invoke(we, weWorld);

            Class<?> bvClass = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Object min = bvClass.getMethod("at", int.class, int.class, int.class).invoke(null, minX, minY, minZ);
            Object max = bvClass.getMethod("at", int.class, int.class, int.class).invoke(null, maxX, maxY, maxZ);

            Class<?> regionClass = Class.forName("com.sk89q.worldedit.regions.CuboidRegion");
            Object region = regionClass.getConstructor(bvClass, bvClass).newInstance(min, max);

            Object clipboard = session.getClass().getMethod("createClipboard", regionClass).invoke(session, region);

            Class<?> cfClass = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats");
            Object format = cfClass.getMethod("findByFile", File.class).invoke(null, file);
            if (format == null) format = cfClass.getField("SCHEMATIC").get(null);

            Class<?> fmtClass = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat");
            Object writer = fmtClass.getMethod("getWriter", java.io.OutputStream.class)
                    .invoke(format, new FileOutputStream(file));
            writer.getClass().getMethod("write", Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard"))
                    .invoke(writer, clipboard);
            writer.getClass().getMethod("close").invoke(writer);
            session.getClass().getMethod("close").invoke(session);
        } catch (Exception e) {
            plugin.getLogger().warning("[GuildShelter] NeoForge 保存 schematic 失败: " + e.getMessage());
            return null;
        }
        return file.toPath();
    }

    @Override
    public void paste(String worldName, String name, int x, int y, int z, boolean async) {
        File file = dir.resolve(name + ".schem").toFile();
        if (!file.exists()) return;
        ServerLevel level = resolveLevel(worldName);
        if (level == null) return;
        Runnable task = () -> {
            try {
                Class<?> cfClass = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats");
                Object format = cfClass.getMethod("findByFile", File.class).invoke(null, file);
                if (format == null) return;

                Class<?> fmtClass = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat");
                Object reader = fmtClass.getMethod("getReader", java.io.InputStream.class)
                        .invoke(format, new FileInputStream(file));
                Object clipboard = reader.getClass().getMethod("read").invoke(reader);
                reader.getClass().getMethod("close").invoke(reader);

                Object weWorld = adaptWorld(level);
                if (weWorld == null) return;

                Class<?> weClass = Class.forName("com.sk89q.worldedit.WorldEdit");
                Object we = weClass.getMethod("getInstance").invoke(null);
                Object session = weClass.getMethod("newEditSession", Class.forName("com.sk89q.worldedit.world.World"))
                        .invoke(we, weWorld);

                Class<?> holderClass = Class.forName("com.sk89q.worldedit.session.ClipboardHolder");
                Object holder = holderClass.getConstructor(Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard"))
                        .newInstance(clipboard);
                Object pasteBuilder = holderClass.getMethod("createPaste", Class.forName("com.sk89q.worldedit.EditSession"))
                        .invoke(holder, session);

                Class<?> bvClass = Class.forName("com.sk89q.worldedit.math.BlockVector3");
                Object to = bvClass.getMethod("at", int.class, int.class, int.class).invoke(null, x, y, z);
                pasteBuilder = pasteBuilder.getClass().getMethod("to", bvClass).invoke(pasteBuilder, to);
                pasteBuilder = pasteBuilder.getClass().getMethod("ignoreAirBlocks", boolean.class).invoke(pasteBuilder, false);
                Object op = pasteBuilder.getClass().getMethod("build").invoke(pasteBuilder);

                Class.forName("com.sk89q.worldedit.function.operation.Operations")
                        .getMethod("complete", Class.forName("com.sk89q.worldedit.function.operation.Operation"))
                        .invoke(null, op);
                session.getClass().getMethod("close").invoke(session);
            } catch (Exception e) {
                plugin.getLogger().warning("[GuildShelter] NeoForge 粘贴 schematic 失败: " + e.getMessage());
            }
        };
        if (async) {
            net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().submit(task);
        } else {
            task.run();
        }
    }

    @Override
    public boolean delete(String name) {
        return dir.resolve(name + ".schem").toFile().delete();
    }

    @Override
    public List<String> list() {
        List<String> result = new ArrayList<>();
        File[] files = dir.toFile().listFiles((d, n) -> n.endsWith(".schem") || n.endsWith(".schematic"));
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                result.add(n.substring(0, n.lastIndexOf('.')));
            }
        }
        return result;
    }

    @Override
    public Path schematicsDir() { return dir; }

    /**
     * 反射调 WorldEdit <b>模组版</b> {@code NeoForgeAdapter.get().fromNativeWorld(level)} → WorldEdit World。
     *
     * <p>必须用模组版(worldedit-mod / NeoForge 平台)的 adapter：它直接操作原生 {@code BlockState}
     * 注册表，<b>原生认模组方块</b>。<b>绝不</b>降级到 {@code BukkitAdapter}——后者经 Bukkit Material
     * 映射，模组方块会丢成空气。WE 模组版不在场时这里抛异常，由调用方记录失败（而非静默降级）。
     *
     * <p>WE 7.4.4：adapt(ServerLevel) 静态方法已移除，改为 {@code NeoForgeAdapter.get()} 单例的实例方法
     * {@code fromNativeWorld(net.minecraft.world.level.Level)}（声明在父类 CoreMcAdapter）。
     */
    private Object adaptWorld(ServerLevel level) throws Exception {
        Class<?> adapterClass = Class.forName("com.sk89q.worldedit.neoforge.NeoForgeAdapter");
        Object adapter = adapterClass.getMethod("get").invoke(null); // 单例
        return adapterClass.getMethod("fromNativeWorld", net.minecraft.world.level.Level.class)
                .invoke(adapter, level);
    }

    /** 从世界名解析 ServerLevel。 */
    private ServerLevel resolveLevel(String worldName) {
        for (ServerLevel level : ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
            if (dimToString(level).equals(worldName)) {
                return level;
            }
        }
        // fallback: Bukkit 桥接
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

    /** 从 ServerLevel 获取维度名（兼容 NeoForge 26 不同版本的方法名）。 */
    private static String dimToString(ServerLevel level) {
        // NeoForge 26: ResourceKey 可能用 registryName() 或 location()
        var key = level.dimension();
        try {
            // 尝试 location() (部分版本)
            Object loc = key.getClass().getMethod("location").invoke(key);
            return loc.toString().replace(":", "_");
        } catch (Exception ignored) {}
        try {
            // 尝试 registryName()
            Object loc = key.getClass().getMethod("registryName").invoke(key);
            return loc.toString().replace(":", "_");
        } catch (Exception ignored) {}
        // 最终 fallback: key.toString() 格式如 "ResourceKey[minecraft:dimension / minecraft:overworld]"
        return key.toString().replaceAll(".* / ", "").replace("]", "").replace(":", "_");
    }
}
