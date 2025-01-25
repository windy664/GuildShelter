package org.windy.guildshelter.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.windy.guildshelter.events.GuildShelterEnterEvent;

public class GuildShelterEnterListener implements Listener {

    @EventHandler
    public void onGuildShelterEnter(GuildShelterEnterEvent event) {
        // 获取进入事件中的玩家、联盟名和消息
        String message = event.getMessage();
        event.getPlayer().sendMessage("Welcome to the Guild Shelter: " + event.getGuildName());
        event.getPlayer().sendMessage("Message: " + message);  // 输出消息
    }
}
