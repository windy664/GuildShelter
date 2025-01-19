package org.windy.guildshelter.listener.neoforge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;

public class EntityInteractByEntityListener {
    @SubscribeEvent
    public void onEntityInteractByEntity(EntityMobGriefingEvent event) {
        // 处理实体与实体交互的事件
        // 可以根据需要进行自定义处理
    }
}
