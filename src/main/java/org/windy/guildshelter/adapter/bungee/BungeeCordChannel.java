package org.windy.guildshelter.adapter.bungee;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * BungeeCord PluginMessage 通道：用 "BungeeCord" 通道 + "Connect" 子命令跨服传送。
 *
 * <p>协议：
 * <pre>
 *   channel = "BungeeCord"
 *   sub-channel = "Connect"
 *   data = serverName (UTF-8 string)
 * </pre>
 */
public final class BungeeCordChannel implements ProxyChannel, PluginMessageListener {

    private static final String CHANNEL = "BungeeCord";
    private final Plugin plugin;
    private volatile boolean registered = false;

    public BungeeCordChannel(Plugin plugin) {
        this.plugin = plugin;
        try {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
            registered = true;
            plugin.getLogger().info("[GuildShelter] BungeeCord PluginMessage 通道已注册。");
        } catch (Exception e) {
            plugin.getLogger().warning("[GuildShelter] 注册 BungeeCord 通道失败: " + e.getMessage());
        }
    }

    @Override
    public void sendToServer(Player player, String serverName) {
        if (!registered) return;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(plugin, CHANNEL, baos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[GuildShelter] BungeeCord 跨服传送失败: " + e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        return registered;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        // 收到代理端消息（一般不需要处理，留作扩展）
    }
}
