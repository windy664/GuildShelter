package org.windy.guildshelter.adapter.bungee;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Velocity PluginMessage 通道：用 "velocity:modern" 通道跨服传送。
 *
 * <p>协议（Velocity modern forwarding）：
 * <pre>
 *   channel = "velocity:modern"
 *   data:
 *     - action (byte) = 0x01 (ForwardToServer)
 *     - serverName (UTF-8 string)
 * </pre>
 *
 * <p>注意：Velocity 的 PluginMessage 协议与 BungeeCord 不同。
 * BungeeCord 用 "Connect" 子命令，Velocity 用 action byte。
 * 详见 <a href="https://velocitypowered.com/wiki/developers/player-information-forwarding/">Velocity Wiki</a>。
 */
public final class VelocityChannel implements ProxyChannel {

    private static final String CHANNEL = "velocity:modern";
    private final Plugin plugin;
    private volatile boolean registered = false;

    public VelocityChannel(Plugin plugin) {
        this.plugin = plugin;
        try {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
            registered = true;
            plugin.getLogger().info("[GuildShelter] Velocity PluginMessage 通道已注册。");
        } catch (Exception e) {
            plugin.getLogger().warning("[GuildShelter] 注册 Velocity 通道失败: " + e.getMessage());
        }
    }

    @Override
    public void sendToServer(Player player, String serverName) {
        if (!registered) return;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF(serverName);
            player.sendPluginMessage(plugin, CHANNEL, baos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[GuildShelter] Velocity 跨服传送失败: " + e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        return registered;
    }
}
