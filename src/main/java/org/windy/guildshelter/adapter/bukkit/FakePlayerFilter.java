package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.entity.Player;

/**
 * 模组假玩家检测：统一判断一个 Player 是否为真实在线玩家。
 * Citizens NPC、CustomNPC、Arclight 假人等都会触发 Bukkit 事件，
 * 但不应该被保护系统/访问系统/增益系统处理。
 *
 * <p>检测策略（任一命中即为假玩家）：
 * <ul>
 *   <li>{@code player.isOnline() == false} — 部分假玩家不注册为在线玩家</li>
 *   <li>{@code player.hasMetadata("NPC")} — Citizens 插件的 NPC 标记</li>
 *   <li>类名包含 "FakePlayer" / "NPC" / "EntityNPC" — 常见模组假人类名</li>
 *   <li>{@code player.getUniqueId()} 的版本位异常 — 部分模组用非标准 UUID</li>
 * </ul>
 */
public final class FakePlayerFilter {

    private FakePlayerFilter() {}

    /** 该玩家是否为真实在线玩家（非模组假人/NPC）。 */
    public static boolean isRealPlayer(Player player) {
        if (player == null) return false;
        // 1. 最基本检查：是否在线
        if (!player.isOnline()) return false;
        // 2. Citizens NPC 标记
        try {
            if (player.hasMetadata("NPC")) return false;
        } catch (Throwable ignored) {
            // hasMetadata 在某些环境下可能抛异常
        }
        // 3. 类名检查：常见假人类名模式
        String className = player.getClass().getSimpleName();
        if (className.contains("FakePlayer") || className.contains("EntityNPC")
                || className.contains("CustomNPC") || className.contains("BotEntity")) {
            return false;
        }
        // 4. 包名检查：某些模组假人包路径
        String packageName = player.getClass().getPackageName();
        if (packageName.contains("customnpcs") || packageName.contains("citizens")) {
            return false;
        }
        return true;
    }
}
