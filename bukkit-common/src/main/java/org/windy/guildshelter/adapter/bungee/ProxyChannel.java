package org.windy.guildshelter.adapter.bungee;

import org.bukkit.entity.Player;

/**
 * 代理跨服传送抽象：BungeeCord 和 Velocity 共用此接口。
 * 通过 PluginMessage 通道让代理把玩家送到指定服务器。
 */
public interface ProxyChannel {

    /** 把玩家送到指定服务器。 */
    void sendToServer(Player player, String serverName);

    /** 是否可用（通道已注册）。 */
    boolean isAvailable();

    /**
     * 按代理类型创建实例。
     * @param proxyType "bungeecord" 或 "velocity"
     * @param plugin 插件实例（注册通道用）
     */
    static ProxyChannel create(String proxyType, org.bukkit.plugin.Plugin plugin) {
        return switch (proxyType.toLowerCase()) {
            case "bungeecord" -> new BungeeCordChannel(plugin);
            case "velocity" -> new VelocityChannel(plugin);
            default -> new NoOpChannel();
        };
    }
}
