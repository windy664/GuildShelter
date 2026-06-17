package org.windy.guildshelter;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * NeoForge {@code @Mod} 入口——<b>注意:本 jar 是 Bukkit 插件(由 plugin.yml 加载),FML 不会加载它,
 * 故本类构造函数实际<b>不会运行</b></b>。保留只为满足 moddev/mods.toml 的元数据期望。
 *
 * <p>真正的 NeoForge 监听(保护/flag)由 {@link GuildShelterPlugin} 在检测到 NeoForge 时,
 * 通过 {@code NeoForgeHooks.register()} 从 Bukkit 侧挂到 {@code NeoForge.EVENT_BUS}——
 * 因为只有 Bukkit 入口会真正被加载,而 Youer 上又能直接跑 NeoForge 代码。
 */
@Mod(Guildshelter.MODID)
public class Guildshelter {

    public static final String MODID = "guildshelter";

    private static final Logger LOGGER = LogUtils.getLogger();

    public Guildshelter(IEventBus modEventBus, ModContainer modContainer) {
        // 不会被调用(见类注释)。NeoForge 监听改由 GuildShelterPlugin 侧注册。
        LOGGER.info("[GuildShelter] @Mod 入口被加载(通常不会发生)。");
    }
}
