package org.windy.guildshelter.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.windy.guildshelter.events.GuildShelterLeaveEvent;

public class GuildShelterLeaveListener implements Listener {

    @EventHandler
    public void onGuildShelterLeave(GuildShelterLeaveEvent event) {
        // 获取离开事件中的玩家、联盟名和消息
        String message = event.getMessage();
        event.getPlayer().sendMessage("You have left the Guild Shelter: " + event.getGuildName());
        event.getPlayer().sendMessage("Message: " + message);  // 输出消息
    }
}
