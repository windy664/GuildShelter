package org.windy.guildshelter.adapter.bungee;

import org.bukkit.entity.Player;

/** 无代理模式：跨服传送不可用。 */
public final class NoOpChannel implements ProxyChannel {

    @Override
    public void sendToServer(Player player, String serverName) {
        // 无代理，不支持跨服
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
