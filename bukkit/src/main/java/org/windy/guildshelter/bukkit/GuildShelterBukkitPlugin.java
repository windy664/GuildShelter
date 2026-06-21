package org.windy.guildshelter.bukkit;

import org.windy.guildshelter.GuildShelterPlugin;
import org.windy.guildshelter.platform.BukkitPlatformBindings;
import org.windy.guildshelter.platform.PlatformBindings;

/**
 * 普通版（纯 Bukkit）插件入口。plugin.yml {@code main} 指向本类。
 * 仅覆盖载体接缝工厂；全部装配逻辑在抽象基类 {@link GuildShelterPlugin}。
 */
public final class GuildShelterBukkitPlugin extends GuildShelterPlugin {

    @Override
    protected PlatformBindings createBindings() {
        return new BukkitPlatformBindings();
    }
}
