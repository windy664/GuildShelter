package org.windy.guildshelter.listener.neoforge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

public class CreatureSpawnListener {
    @SubscribeEvent
    public void onCreatureSpawn(FinalizeSpawnEvent event) {
        String name = String.valueOf(event.getEntity().getName());

    }
}
