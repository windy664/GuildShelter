package org.windy.guildshelter.listener.neoforge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public class FarmProtectListener {
    @SubscribeEvent
    public void onFarmProtect(BlockEvent.FarmlandTrampleEvent event) {
        // 处理农场保护的事件
        // 可以根据需要进行自定义处理
    }
}
