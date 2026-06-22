package org.windy.guildshelter.adapter.bukkit.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.windy.guildshelter.domain.layout.RoadMask;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.TerrainPrepMode;
import org.windy.guildshelter.domain.port.TerrainPreparer;

/**
 * {@link TerrainPreparer} 装饰器：对 <b>Iris 世界</b>先用 Iris 异步预生成器把目标区域生成好，再委托真正的整地器
 * （Bukkit/NeoForge）在<b>已生成</b>的区块上写方块。
 *
 * <p>修复"建会后铺路/围墙/整地在主线程<b>同步</b>生成 Iris 区块"导致的卡顿——这正是
 * {@code /iris create} 不卡（Iris 异步多线程生成）而我们建会卡（被拽回主线程逐块生成）的差别。
 * 根因与机制见 {@link org.windy.guildshelter.adapter.bukkit.world.iris.IrisPregen}。
 *
 * <p>非 Iris 世界 / Iris 不在场 → 直接委托，零开销、行为不变。同步整地（claim，玩家就在现场、区块已加载）
 * 也直接委托，不打断其同步语义。
 *
 * <p><b>惰性隔离</b>：本类<b>不静态引用</b> {@code com.volmit.iris.*}；仅当 {@link #wrap} 判定 Iris 在场才
 * 实例化本类，且只有此时 {@link #gate} 才会触达 {@code IrisPregen}（它持有 Iris 引用）。Iris 不在场时
 * {@code wrap} 原样返回 delegate，本类不被实例化、{@code IrisPregen} 永不加载。
 */
public final class IrisPregenTerrainPreparer implements TerrainPreparer {

    private final Plugin plugin;
    private final TerrainPreparer delegate;

    private IrisPregenTerrainPreparer(Plugin plugin, TerrainPreparer delegate) {
        this.plugin = plugin;
        this.delegate = delegate;
    }

    /**
     * 包装：仅当 config 启用 Iris 且 Iris 插件在场时才套预生成层，否则原样返回 {@code delegate}
     * （不引入任何 Iris 引用）。
     */
    public static TerrainPreparer wrap(Plugin plugin, TerrainPreparer delegate, boolean irisEnabled) {
        boolean present = irisEnabled && Bukkit.getPluginManager().getPlugin("Iris") != null;
        if (!present) {
            return delegate;
        }
        plugin.getLogger().info("[GuildShelter] 整地/铺路已接入 Iris 异步预生成（建会后不再主线程同步生成区块）。");
        return new IrisPregenTerrainPreparer(plugin, delegate);
    }

    @Override
    public void prepare(String worldName, ChunkRegion region, TerrainPrepMode mode) {
        prepare(worldName, region, mode, false);
    }

    @Override
    public void prepare(String worldName, ChunkRegion region, TerrainPrepMode mode, boolean sync) {
        if (sync) {
            // 同步整地(claim)：玩家就在现场、区块已加载，同步 getChunk 命中已生成区块不卡；保持同步语义直接委托。
            delegate.prepare(worldName, region, mode, true);
            return;
        }
        gate(worldName, region, () -> delegate.prepare(worldName, region, mode, false));
    }

    @Override
    public void surfaceRoad(String worldName, ChunkRegion region, RoadMask roadMask) {
        gate(worldName, region, () -> delegate.surfaceRoad(worldName, region, roadMask));
    }

    @Override
    public void encloseMainCity(String worldName, ChunkRegion region, RoadMask roadMask) {
        gate(worldName, region, () -> delegate.encloseMainCity(worldName, region, roadMask));
    }

    /** Iris 世界 → 先异步预生成 region 再跑整地动作；非 Iris / 世界未加载 → 立即跑（IrisPregen 内部再判一次）。 */
    private void gate(String worldName, ChunkRegion region, Runnable op) {
        World world = Bukkit.getWorld(worldName);
        org.windy.guildshelter.adapter.bukkit.world.iris.IrisPregen.ensureGenerated(
                plugin, world,
                region.minBlockX(), region.minBlockZ(), region.maxBlockX(), region.maxBlockZ(),
                op);
    }
}
