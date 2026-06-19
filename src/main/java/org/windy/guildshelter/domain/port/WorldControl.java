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

    /** 卸载并存盘（世界级惰性加载）。 */
    boolean unloadGuild(GuildId guild);
}
