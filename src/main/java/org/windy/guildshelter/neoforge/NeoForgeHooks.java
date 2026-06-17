package org.windy.guildshelter.neoforge;

import net.neoforged.neoforge.common.NeoForge;

/**
 * 从 <b>Bukkit 插件侧</b> 把 NeoForge 事件处理器挂到游戏总线。
 *
 * <p>本 jar 是 Bukkit 插件(由 plugin.yml 加载),FML 不会加载其 {@code @Mod} 入口,
 * 所以 NeoForge 监听必须从 {@code GuildShelterPlugin} 这边注册。本类只被插件在
 * "检测到 NeoForge" 的分支里调用一次 → 纯 Bukkit 端永远不会加载到它(及其 NeoForge 引用)。
 */
public final class NeoForgeHooks {

    private NeoForgeHooks() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.register(new NeoForgeProtection());
        NeoForge.EVENT_BUS.register(new NeoForgeFlags());
    }
}
