package org.windy.guildshelter.domain.port;

import java.nio.file.Path;
import java.util.List;

/**
 * Schematic 存储端口：保存/加载/粘贴建筑模板。
 * 三个实现：普通 WorldEdit、FAWE、NeoForge 侧 WorldEdit。
 * 运行时按环境自动选择（优先 NeoForge → FAWE → 普通 WE）。
 */
public interface SchematicStore {

    /**
     * 把世界中一块区域保存为 schematic 文件。
     *
     * @param worldName 世界名
     * @param name      模板名（不含扩展名）
     * @param minX/minY/minZ 区域起点（方块坐标，含）
     * @param maxX/maxY/maxZ 区域终点（方块坐标，含）
     * @return 保存的文件路径
     */
    Path save(String worldName, String name, int minX, int minY, int minZ, int maxX, int maxY, int maxZ);

    /**
     * 把 schematic 粘贴到世界中。
     *
     * @param worldName 世界名
     * @param name      模板名
     * @param x/y/z     粘贴起点（方块坐标）
     * @param async     是否异步粘贴（FAWE 支持，普通 WE 忽略）
     */
    void paste(String worldName, String name, int x, int y, int z, boolean async);

    /** 删除一个模板文件。 */
    boolean delete(String name);

    /** 列出所有已保存的模板名。 */
    List<String> list();

    /** 获取模板文件目录。 */
    Path schematicsDir();

    /**
     * 按环境自动选择实现：NeoForge → FAWE → 普通 WE → null。
     */
    static SchematicStore autoDetect(Path dataDir, org.bukkit.plugin.Plugin plugin) {
        // 1. 混合端 + WorldEdit 模组版(worldedit-mod) → 走 NeoForge 原生 store。
        //    必须确认 WE 模组版类在场才用：模组版 adapter 原生认模组方块；只有它能正确粘贴含模组方块的
        //    .schem。注意 WE 模组版从 mods/ 加载、不是 Bukkit 插件，故下面 getPlugin("WorldEdit") 查不到它，
        //    只能靠类探测。模组版不在(只装了 Bukkit 版/FAWE)则落到 2/3，按 Bukkit 平台处理（模组方块会丢）。
        try {
            Class.forName("net.neoforged.fml.loading.FMLLoader");
            Class.forName("com.sk89q.worldedit.neoforge.NeoForgeWorldEdit");
            // 用反射创建 NeoForge 实现（避免纯 Bukkit 端加载到 NeoForge 类）
            return new NeoForgeSchematicStoreAdapter(dataDir, plugin);
        } catch (ClassNotFoundException ignored) {}
        // 2. FAWE
        if (plugin.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null) {
            try {
                return (SchematicStore) Class.forName("org.windy.guildshelter.adapter.fawe.FaweSchematicStore")
                        .getConstructor(Path.class, org.bukkit.plugin.Plugin.class)
                        .newInstance(dataDir.resolve("schematics"), plugin);
            } catch (Exception e) {
                plugin.getLogger().warning("[GuildShelter] FAWE 加载失败: " + e.getMessage());
            }
        }
        // 3. 普通 WorldEdit
        if (plugin.getServer().getPluginManager().getPlugin("WorldEdit") != null) {
            try {
                return (SchematicStore) Class.forName("org.windy.guildshelter.adapter.worldedit.WeSchematicStore")
                        .getConstructor(Path.class, org.bukkit.plugin.Plugin.class)
                        .newInstance(dataDir.resolve("schematics"), plugin);
            } catch (Exception e) {
                plugin.getLogger().warning("[GuildShelter] WorldEdit 加载失败: " + e.getMessage());
            }
        }
        return null; // 无 WorldEdit 插件
    }
}

/**
 * NeoForge 侧 SchematicStore 的占位适配器。
 * 实际实现通过反射委托给 NeoForge 模块的类（避免纯 Bukkit 端加载 NeoForge 类）。
 */
class NeoForgeSchematicStoreAdapter implements SchematicStore {
    private final Path dir;
    private final org.bukkit.plugin.Plugin plugin;
    private SchematicStore delegate; // 惰性初始化

    NeoForgeSchematicStoreAdapter(Path dataDir, org.bukkit.plugin.Plugin plugin) {
        this.dir = dataDir.resolve("schematics");
        this.plugin = plugin;
        try {
            dir.toFile().mkdirs();
        } catch (Exception ignored) {}
    }

    private SchematicStore delegate() {
        if (delegate == null) {
            try {
                // 反射加载 NeoForge 实现（类名需与 neoforge 包下实际类一致）
                delegate = (SchematicStore) Class.forName("org.windy.guildshelter.neoforge.NeoForgeSchematicStore")
                        .getConstructor(Path.class, org.bukkit.plugin.Plugin.class)
                        .newInstance(dir, plugin);
            } catch (Exception e) {
                plugin.getLogger().warning("[GuildShelter] NeoForge SchematicStore 加载失败: " + e.getMessage());
            }
        }
        return delegate;
    }

    @Override public Path save(String worldName, String name, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        SchematicStore d = delegate();
        return d != null ? d.save(worldName, name, minX, minY, minZ, maxX, maxY, maxZ) : null;
    }
    @Override public void paste(String worldName, String name, int x, int y, int z, boolean async) {
        SchematicStore d = delegate();
        if (d != null) d.paste(worldName, name, x, y, z, async);
    }
    @Override public boolean delete(String name) {
        SchematicStore d = delegate();
        return d != null && d.delete(name);
    }
    @Override public java.util.List<String> list() {
        SchematicStore d = delegate();
        return d != null ? d.list() : java.util.List.of();
    }
    @Override public Path schematicsDir() { return dir; }
}
