package org.windy.guildshelter;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.windy.guildshelter.neoforge.NeoForgeFlags;
import org.windy.guildshelter.neoforge.NeoForgeProtection;

/**
 * NeoForge 端入口。
 *
 * <p>本插件是混合端（Bukkit 插件 + NeoForge mod 同进程）项目：业务装配在 Bukkit 侧的
 * {@link GuildShelterPlugin}，而保护类事件走 NeoForge {@code EVENT_BUS}，以免混合端上
 * mod 的破坏/交互绕过权限。NeoForge 保护监听器将在后续阶段注册到这里。
 */
@Mod(Guildshelter.MODID)
public class Guildshelter {

    public static final String MODID = "guildshelter";

    private static final Logger LOGGER = LogUtils.getLogger();

    public Guildshelter(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[GuildShelter] NeoForge 端已加载");
        // 混合端领地保护：注册到游戏事件总线（覆盖模组方块/交互）。判定复用 Bukkit 侧的 ClaimGuard。
        NeoForge.EVENT_BUS.register(new NeoForgeProtection());
        // 混合端地皮 flag 氛围类（pvp/怪物/爆炸/怪物破坏，覆盖模组实体）。判定复用 ManorLookup。
        NeoForge.EVENT_BUS.register(new NeoForgeFlags());
        LOGGER.info("[GuildShelter] 领地保护 + Flag 已注册到 NeoForge EVENT_BUS");
    }
}
