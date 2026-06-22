package org.windy.guildshelter.adapter.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import org.bukkit.plugin.Plugin;
import org.windy.guildshelter.domain.port.SchematicStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Bukkit 平台的 {@link SchematicStore} 实现：<b>直接调 WorldEdit API，不反射</b>
 * （{@code worldedit-bukkit + worldedit-core} 在 bukkit-common compileOnly classpath，运行期由 FAWE/WE 插件提供）。
 *
 * <p>上游 WE API 对 <b>FAWE 与普通 WorldEdit 通用</b>，故合并原 {@code FaweSchematicStore}/{@code WeSchematicStore}
 * 两份反射实现为一份。含模组方块的 .schem 仍需 NeoForge 模组版 adapter 才能正确粘贴（见
 * {@code SchematicStores.autoDetect} 的优先级）。
 *
 * <p><b>惰性隔离</b>：本类直接引用 WE 类，故仅在检测到 FAWE/WE 在场时由工厂经 {@code Class.forName} 实例化
 * （WE 不在则本类永不加载）。所有操作在服务器主线程。
 */
public final class WorldEditSchematicStore implements SchematicStore {

    private final Path dir;
    private final Plugin plugin;

    public WorldEditSchematicStore(Path dir, Plugin plugin) {
        this.dir = dir;
        this.plugin = plugin;
        dir.toFile().mkdirs();
    }

    @Override
    public Path save(String worldName, String name, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        org.bukkit.World bukkitWorld = plugin.getServer().getWorld(worldName);
        if (bukkitWorld == null) {
            return null;
        }
        File file = dir.resolve(name + ".schem").toFile();
        World weWorld = BukkitAdapter.adapt(bukkitWorld);
        CuboidRegion region = new CuboidRegion(weWorld,
                BlockVector3.at(minX, minY, minZ), BlockVector3.at(maxX, maxY, maxZ));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()) {
            ForwardExtentCopy copy = new ForwardExtentCopy(
                    editSession, region, clipboard, region.getMinimumPoint());
            copy.setCopyingEntities(true);
            Operations.complete(copy);
        } catch (Exception e) {
            plugin.getLogger().warning("[GuildShelter] WorldEdit 复制区域失败: " + e.getMessage());
            return null;
        }
        // 写盘格式按别名取（版本无关，避免硬绑 BuiltInClipboardFormat 枚举常量名随版本变）。
        ClipboardFormat format = ClipboardFormats.findByAlias("sponge");
        if (format == null) {
            plugin.getLogger().warning("[GuildShelter] WorldEdit 找不到 sponge schematic 格式。");
            return null;
        }
        try (ClipboardWriter writer = format.getWriter(new FileOutputStream(file))) {
            writer.write(clipboard);
        } catch (Exception e) {
            plugin.getLogger().warning("[GuildShelter] WorldEdit 保存 schematic 失败: " + e.getMessage());
            return null;
        }
        return file.toPath();
    }

    @Override
    public void paste(String worldName, String name, int x, int y, int z, boolean async) {
        File file = dir.resolve(name + ".schem").toFile();
        org.bukkit.World bukkitWorld = plugin.getServer().getWorld(worldName);
        if (!file.exists() || bukkitWorld == null) {
            return;
        }
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            plugin.getLogger().warning("[GuildShelter] 无法识别 schematic 格式: " + file.getName());
            return;
        }
        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            clipboard = reader.read();
        } catch (Exception e) {
            plugin.getLogger().warning("[GuildShelter] WorldEdit 读取 schematic 失败: " + e.getMessage());
            return;
        }
        World weWorld = BukkitAdapter.adapt(bukkitWorld);
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()) {
            Operation op = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BlockVector3.at(x, y, z))
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(op);
        } catch (Exception e) {
            plugin.getLogger().warning("[GuildShelter] WorldEdit 粘贴 schematic 失败: " + e.getMessage());
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
    public Path schematicsDir() {
        return dir;
    }
}
