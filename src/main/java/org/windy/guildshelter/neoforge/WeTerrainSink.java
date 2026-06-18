package org.windy.guildshelter.neoforge;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.world.World;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 整地/铺路的 <b>WorldEdit 写方块后端</b>（NeoForge 模组版，混合端启用）。
 *
 * <p>为什么存在：Youer 26.2 上 {@code level.setBlock(..., UPDATE_CLIENTS)} 的增量方块变更广播不可靠、
 * 且不重算光照——运行期改的地块在客户端表现成"幽灵方块"，需交互一次才同步。WE 的 {@link EditSession}
 * 在 {@link EditSession#close()} 时按 {@link SideEffectSet} 整块重发并重算光照，绕开那条不靠谱的增量管线。
 *
 * <p>SideEffect 只开 {@link SideEffect#NETWORK}(重发客户端) + {@link SideEffect#LIGHTING}(重光照)，
 * 关掉 {@link SideEffect#NEIGHBORS}/{@link SideEffect#UPDATE} 等物理——等价原生 {@code UPDATE_CLIENTS}
 * 的"只发客户端、不触发邻居物理"，避免批量整地引发连锁水流/掉落。
 *
 * <p>本类<b>直接引用 WE 类</b>，仅在 WE 模组版在场时由 {@link NeoForgeTerrainPreparer} 实例化
 * （JVM 惰性解析：WE 不在则本类永不加载，原生兜底不受影响）。所有调用须在服务器主线程。
 */
final class WeTerrainSink implements NeoForgeTerrainPreparer.BlockSink {

    /** 只开重发 + 重光照，关物理（对齐原生 UPDATE_CLIENTS 的无邻居更新语义）。 */
    private static final SideEffectSet SIDE_EFFECTS = SideEffectSet.none()
            .with(SideEffect.NETWORK, SideEffect.State.ON)
            .with(SideEffect.LIGHTING, SideEffect.State.ON);

    /** WE 7.4.4：adapt 方法搬到 CoreMcAdapter 实例上，经 NeoForgeAdapter.get() 单例调用。 */
    private static final NeoForgeAdapter ADAPTER = NeoForgeAdapter.get();

    private final World weWorld;
    private EditSession session; // 惰性创建，每个 flush 周期一个

    WeTerrainSink(ServerLevel level) {
        this.weWorld = ADAPTER.fromNativeWorld(level); // ServerLevel 是 Level 子类
    }

    @Override
    public void set(BlockPos pos, BlockState state) {
        if (session == null) {
            session = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).maxBlocks(-1).build();
            session.setSideEffectApplier(SIDE_EFFECTS);
        }
        try {
            session.setBlock(ADAPTER.adapt(pos), ADAPTER.fromNativeBlockState(state));
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
