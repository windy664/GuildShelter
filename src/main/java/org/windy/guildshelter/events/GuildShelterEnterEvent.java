package org.windy.guildshelter.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class GuildShelterEnterEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final String guildName;
    private final String message;

    public GuildShelterEnterEvent(Player player, String guildName, String message) {
        this.player = player;
        this.guildName = guildName;
        this.message = message;
    }

    public Player getPlayer() {
        return player;
    }

    public String getGuildName() {
        return guildName;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
