package org.windy.guildshelter.domain.port;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;

/**
 * 公会营地的创建 / 加载 / 卸载 / 边界控制——平台无关端口。
 *
 * <p><b>为什么是端口（留的坑）</b>：实测 Youer(Spigot 系混合端) <b>不honor Bukkit 自定义
 * {@code ChunkGenerator}</b>，世界 gen 由 NeoForge/原版引擎驱动，Bukkit 那层的生成器桥接被忽略，
 * 于是平整庄园生不出来。因此世界创建/生成将改由 <b>NeoForge 侧动态维度 + 自定义 ChunkGenerator</b>
 * 实现（复用 {@code LayoutCalculator} 的 classify→材料逻辑）。本端口就是那个换实现的接缝：
 * <ul>
 *   <li>当前：{@code WorldManager}（Bukkit 实现，在 Youer 上<b>生成无效</b>，仅占位）</li>
 *   <li>将来：NeoForge 实现 ensureWorld（动态维度）</li>
 * </ul>
 */
public interface WorldControl {

    /** 公会营地名（如 {@code guild_<id>}），平台无关，两侧需一致。 */
    String worldName(GuildId guild);

    /**
     * 创建（或加载已存在的）公会营地，并同步出生点与边界。须在主线程调用。
     *
     * <p>首次创建时会随机种子、并把网格原点锚定到陆地（避免出生在海里），这些信息写回返回的
     * {@link GuildWorld}，调用方需持久化返回值。世界已存在时原样返回。
     */
    GuildWorld ensureWorld(GuildWorld world);

    /** 按当前已分配 slot / 公会等级同步世界边界。 */
    void applyBorder(GuildWorld world);

    /** 卸载公会世界（用于<b>解散/删除</b>等丢弃场景，实现可不存盘以省同步刷盘开销）。世界级惰性卸载另有它途。 */
    boolean unloadGuild(GuildId guild);

    /**
     * 异步版 {@link #ensureWorld}：对<b>禁止主线程创建</b>的引擎（如 Iris：{@code create()} 必须在异步线程，
     * 否则抛 "cannot invoke create() on the main thread"）在异步线程建世界、建好后<b>回主线程</b>调 {@code onReady}。
     * 不阻塞调用线程（Iris 建世界内部会 submit 回主线程，调用线程若阻塞等待会死锁）。
     *
     * <p>默认实现同步：直接 {@code onReady.accept(ensureWorld(world))}（非 Iris / 已加载世界走这条，行为不变）。
     * 调用方的 {@code onReady} 内做建会后续（存库/铺路登记/注册），<b>保证在主线程执行</b>。
     */
    default void ensureWorldAsync(GuildWorld world, java.util.function.Consumer<GuildWorld> onReady) {
        ensureWorldAsync(world, onReady, () -> {});
    }

    /**
     * 带<b>失败回调</b>的异步建世界：成功回主线程调 {@code onReady}，<b>失败回主线程调 {@code onError}</b>
     * （如 Iris 引擎建世界抛异常）。调用方据此清理在途状态（解除"建造中"标记，允许后续重试），
     * 避免一次失败把公会永久卡在建造中。{@code onError} 同 {@code onReady} <b>保证在主线程执行</b>。
     *
     * <p>默认实现同步：{@code ensureWorld} 抛异常时调 {@code onError} 后<b>重抛</b>（保留原有上层日志），
     * 否则调 {@code onReady}。Iris 等异步实现须重写本方法，在异步失败路径回主线程调 {@code onError}。
     */
    default void ensureWorldAsync(GuildWorld world, java.util.function.Consumer<GuildWorld> onReady,
                                  Runnable onError) {
        GuildWorld ready;
        try {
            ready = ensureWorld(world);
        } catch (RuntimeException e) {
            onError.run();
            throw e;
        }
        onReady.accept(ready);
    }

    /**
     * 该世界的地形是否<b>惰性生成</b>（如 Iris：异步、不预生成区块，玩家走到哪生成到哪）。
     *
     * <p>为 {@code true} 时，建会后<b>不应</b>立刻读地表高度铺路/锚地（会强制同步生成未生成区块，
     * 抵消惰性生成的意义、可能卡服）——主城路/围墙应延迟到玩家首次进入该世界、区块自然加载后再补。
     */
    default boolean lazilyGenerated(String worldName) {
        return false;
    }
}
