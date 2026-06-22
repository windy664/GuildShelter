package org.windy.guildshelter.domain.port;

import org.windy.guildshelter.domain.model.GuildId;

/**
 * 惰性生成世界（如 Iris）的<b>延迟整地</b>接缝：建会时若世界惰性生成（{@link WorldControl#lazilyGenerated}），
 * 不立刻铺主城路/围墙（会强制同步生成未生成区块），而是登记为"待补"，由平台侧在<b>玩家首次进入该世界</b>、
 * 区块自然加载后再回调 {@link org.windy.guildshelter.service.GuildService} 补铺。
 *
 * <p>默认实现 {@link #NONE} 为空操作（非惰性世界 / 未接平台监听时立即铺，走原路径）。
 */
@FunctionalInterface
public interface DeferredPrep {

    /** 空实现：不登记延迟（调用方据此走"立即铺"分支）。 */
    DeferredPrep NONE = (guild, worldName) -> { };

    /** 登记某公会世界待补主城路/围墙（玩家首次进入时由平台侧触发补铺）。 */
    void markGuildPending(GuildId guild, String worldName);
}
