package org.windy.guildshelter.adapter.bukkit;

import org.windy.guildshelter.domain.model.GuildWorld;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存注册表：世界名 → {@link GuildWorld}（含 origin 偏移）。
 * 供监听器在玩家移动时快速判断"这是不是公会世界、网格原点在哪"，避免每次查库。
 * 世界创建/加载时登记。
 */
public final class GuildWorldRegistry {

    private final Map<String, GuildWorld> byWorldName = new ConcurrentHashMap<>();

    public void register(GuildWorld world) {
        byWorldName.put(world.worldName(), world);
    }

    public void unregister(String worldName) {
        byWorldName.remove(worldName);
    }

    public GuildWorld get(String worldName) {
        return byWorldName.get(worldName);
    }

    public boolean isGuildWorld(String worldName) {
        return byWorldName.containsKey(worldName);
    }
}
