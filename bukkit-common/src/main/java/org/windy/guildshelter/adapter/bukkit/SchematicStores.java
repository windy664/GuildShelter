package org.windy.guildshelter.adapter.bukkit;

import org.windy.guildshelter.domain.port.SchematicStore;

import java.nio.file.Path;

/**
 * {@link SchematicStore} 的按环境选实现工厂（从 core 的接口里下沉到此，因涉及 org.bukkit 插件查询与
 * NeoForge/WE 类探测）。优先 NeoForge 原生 → FAWE → 普通 WE → null。全程 {@code Class.forName} 反射，
 * 纯 Bukkit 端不会加载到 NeoForge 类；含模组方块的 .schem 只有 NeoForge 模组版 adapter 能正确粘贴。
 */
public final class SchematicStores {

    private SchematicStores() {}

    /** 按环境自动选择实现：NeoForge → FAWE → 普通 WE → null。 */
    public static SchematicStore autoDetect(Path dataDir, org.bukkit.plugin.Plugin plugin) {
        // 1. 混合端 + WorldEdit 模组版(worldedit-mod) → 走 NeoForge 原生 store。
        //    必须确认 WE 模组版类在场才用：模组版 adapter 原生认模组方块；只有它能正确粘贴含模组方块的
        //    .schem。注意 WE 模组版从 mods/ 加载、不是 Bukkit 插件，故下面 getPlugin("WorldEdit") 查不到它，
        //    只能靠类探测。模组版不在(只装了 Bukkit 版/FAWE)则落到 2/3，按 Bukkit 平台处理（模组方块会丢）。
        try {
            Class.forName("net.neoforged.fml.loading.FMLLoader");
            // 探测 NeoForgeSchematicStore 真正依赖的 NeoForgeAdapter。注意 WE 7.4.4 把平台主类挪进了
            // .internal 子包（com.sk89q.worldedit.neoforge.internal.NeoForgeWorldEdit），旧探测
            // com.sk89q.worldedit.neoforge.NeoForgeWorldEdit 已不存在 → 明明装了 WE 模组版却落到 Bukkit
            // 平台，含模组方块的 .schem 被丢成空气。NeoForgeAdapter 仍在 neoforge 顶层包，探它最稳。
            Class.forName("com.sk89q.worldedit.neoforge.NeoForgeAdapter");
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

    /**
     * NeoForge 侧 SchematicStore 的占位适配器。实际实现通过反射委托给 neoforge_26_2 模块的
     * {@code NeoForgeSchematicStore}（避免纯 Bukkit 端加载 NeoForge 类）。
     */
    static final class NeoForgeSchematicStoreAdapter implements SchematicStore {
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
}
