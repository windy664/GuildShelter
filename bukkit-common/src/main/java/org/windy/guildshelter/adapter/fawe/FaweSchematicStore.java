package org.windy.guildshelter.adapter.fawe;

import org.bukkit.plugin.Plugin;
import org.windy.guildshelter.domain.port.SchematicStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * FAWE 实现：全部通过反射调用 WorldEdit API（WE 不在编译 classpath，是软依赖）。
 */
public final class FaweSchematicStore implements SchematicStore {

    private final Path dir;
    private final Plugin plugin;

    public FaweSchematicStore(Path dir, Plugin plugin) {
        this.dir = dir;
        this.plugin = plugin;
        dir.toFile().mkdirs();
    }

    @Override
    public Path save(String worldName, String name, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        File file = dir.resolve(name + ".schem").toFile();
        try {
            // BukkitAdapter.adapt(world)
            Class<?> baClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Method adapt = baClass.getMethod("adapt", org.bukkit.World.class);
            Object weWorld = adapt.invoke(null, plugin.getServer().getWorld(worldName));
            if (weWorld == null) return null;

            // WorldEdit.getInstance().newEditSession(world)
            Class<?> weClass = Class.forName("com.sk89q.worldedit.WorldEdit");
            Object we = weClass.getMethod("getInstance").invoke(null);
            Object session = weClass.getMethod("newEditSession", Class.forName("com.sk89q.worldedit.world.World"))
                    .invoke(we, weWorld);

            // BlockVector3.at(x,y,z)
            Class<?> bvClass = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Method at = bvClass.getMethod("at", int.class, int.class, int.class);
            Object min = at.invoke(null, minX, minY, minZ);
            Object max = at.invoke(null, maxX, maxY, maxZ);

            // CuboidRegion(min, max)
            Class<?> regionClass = Class.forName("com.sk89q.worldedit.regions.CuboidRegion");
            Object region = regionClass.getConstructor(bvClass, bvClass).newInstance(min, max);

            // session.createClipboard(region) → Clipboard
            Method createClipboard = session.getClass().getMethod("createClipboard", regionClass);
            Object clipboard = createClipboard.invoke(session, region);

            // ClipboardFormats.findByFile(file)
            Class<?> cfClass = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats");
            Object format = cfClass.getMethod("findByFile", File.class).invoke(null, file);
            if (format == null) {
                // fallback: get SCHEMATIC constant
                format = cfClass.getField("SCHEMATIC").get(null);
            }

            // format.getWriter(new FileOutputStream(file))
            Class<?> fmtClass = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat");
            Object writer = fmtClass.getMethod("getWriter", java.io.OutputStream.class)
                    .invoke(format, new FileOutputStream(file));
            // writer.write(clipboard)
            writer.getClass().getMethod("write", Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard"))
                    .invoke(writer, clipboard);
            writer.getClass().getMethod("close").invoke(writer);

            // session.close()
            session.getClass().getMethod("close").invoke(session);
        } catch (Exception e) {
            plugin.getLogger().warning("[GuildShelter] FAWE 保存 schematic 失败: " + e.getMessage());
            return null;
        }
        return file.toPath();
    }

    @Override
    public void paste(String worldName, String name, int x, int y, int z, boolean async) {
        File file = dir.resolve(name + ".schem").toFile();
        if (!file.exists()) return;
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

                Class<?> baClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                Object weWorld = baClass.getMethod("adapt", org.bukkit.World.class)
                        .invoke(null, plugin.getServer().getWorld(worldName));
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

                Class<?> opsClass = Class.forName("com.sk89q.worldedit.function.operation.Operations");
                opsClass.getMethod("complete", Class.forName("com.sk89q.worldedit.function.operation.Operation"))
                        .invoke(null, op);
                session.getClass().getMethod("close").invoke(session);
            } catch (Exception e) {
                plugin.getLogger().warning("[GuildShelter] FAWE 粘贴 schematic 失败: " + e.getMessage());
            }
        };
        if (async) {
            try {
                Class.forName("com.fastasyncworldedit.core.util.TaskManager")
                        .getMethod("async", Runnable.class).invoke(null, task);
            } catch (Exception e) {
                task.run(); // fallback 同步
            }
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
}
