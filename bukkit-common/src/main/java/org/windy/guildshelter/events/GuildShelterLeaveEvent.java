package org.windy.guildshelter.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class GuildShelterLeaveEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final String guildName;
    private final String message;

    // 构造方法
    public GuildShelterLeaveEvent(Player player, String guildName, String message) {
        this.player = player;
        this.guildName = guildName;
        this.message = message;
    }

    // 获取玩家
    public Player getPlayer() {
        return player;
    }

    // 获取公会名称
    public String getGuildName() {
        return guildName;
    }

    // 获取消息
    public String getMessage() {
        return message;
    }

    // 事件的处理器列表
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    // 获取事件处理器列表
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
