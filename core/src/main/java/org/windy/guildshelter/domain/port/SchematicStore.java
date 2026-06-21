package org.windy.guildshelter.domain.port;

import java.nio.file.Path;
import java.util.List;

/**
 * Schematic 存储端口：保存/加载/粘贴建筑模板。
 * 三个实现：普通 WorldEdit、FAWE、NeoForge 侧 WorldEdit。
 *
 * <p><b>纯领域端口</b>（core 模块，禁触 org.bukkit/net.neoforged）。按环境选实现的工厂
 * {@code autoDetect} 涉及 Bukkit 插件与 NeoForge 类探测，已下沉到 bukkit-common 的
 * {@code org.windy.guildshelter.adapter.bukkit.SchematicStores}（优先 NeoForge → FAWE → 普通 WE）。
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
}
