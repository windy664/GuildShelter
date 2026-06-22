package org.windy.guildshelter.adapter.fawe;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.world.World;
import org.bukkit.Material;
import org.windy.guildshelter.adapter.bukkit.world.BukkitTerrainPreparer;

/**
 * 整地/铺路的 <b>FAWE/WorldEdit 写方块后端</b>（纯 Bukkit 载体）。
 *
 * <p>为什么存在：把整地/铺路的写方块统一走 FAWE 的 {@link EditSession}——批量提交、自带重光照与客户端重发，
 * 性能远高于逐方块 {@code setType}，且与 NeoForge 侧 {@code WeTerrainSink} 同一套语义（两端一致）。
 *
 * <p><b>直接引用 WE 类，不反射</b>：{@code worldedit-bukkit} 已在 bukkit-common 的 compileOnly classpath
 * （不打包、不进运行时，由服务器上的 FAWE/WE 插件提供）。本类仅在检测到 FAWE/WE 在场时由
 * {@link BukkitTerrainPreparer} 实例化（JVM 惰性解析：不在场则本类永不加载，{@code NativeSink} 兜底不受影响）。
 *
 * <p>SideEffect 只开 {@link SideEffect#NETWORK}(重发客户端) + {@link SideEffect#LIGHTING}(重光照)，
 * 关物理——等价原生 {@code applyPhysics=false}，避免批量整地引发连锁水流/掉落（栅栏/墙因此不自动相连，
 * 与 NeoForge 侧同一取舍）。所有调用须在服务器主线程。
 */
public final class FaweTerrainSink implements BukkitTerrainPreparer.BlockSink {

    private static final SideEffectSet SIDE_EFFECTS = SideEffectSet.none()
            .with(SideEffect.NETWORK, SideEffect.State.ON)
            .with(SideEffect.LIGHTING, SideEffect.State.ON);

    private final World weWorld;
    private EditSession session; // 惰性创建，每个 flush 周期一个

    public FaweTerrainSink(org.bukkit.World world) {
        this.weWorld = BukkitAdapter.adapt(world);
    }

    @Override
    public void set(int x, int y, int z, Material material, boolean physics) {
        // physics 参数对 FAWE 无意义（侧效已批量统一为 NETWORK+LIGHTING）；保留以对齐 BlockSink 接口。
        if (session == null) {
            session = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).maxBlocks(-1).build();
            session.setSideEffectApplier(SIDE_EFFECTS);
        }
        try {
            session.setBlock(BlockVector3.at(x, y, z), BukkitAdapter.adapt(material.createBlockData()));
        } catch (com.sk89q.worldedit.WorldEditException ignored) {
            // maxBlocks=-1 不会触发 MaxChangedBlocks；其余异常吞掉，别打断整批整地
        }
    }

    @Override
    public void flush() {
        if (session != null) {
            session.close(); // 提交本批：重算光照 + 整块重发给客户端
            session = null;
        }
    }
}
