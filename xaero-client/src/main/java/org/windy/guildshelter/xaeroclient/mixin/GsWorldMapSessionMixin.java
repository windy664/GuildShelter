package org.windy.guildshelter.xaeroclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.windy.guildshelter.xaeroclient.GuildShelterXaeroClient;
import xaero.map.WorldMapSession;
import xaero.map.highlight.HighlighterRegistry;

/**
 * 在 {@code WorldMapSession.init} 里把 {@code HighlighterRegistry.end()} 那次调用重定向：<b>先注册本 mod 的
 * highlighter，再 end()</b>。这样在 Xaero 把列表锁成不可变之前注册（Xaero 不提供第三方注册事件，OPAC 也是
 * 被 Xaero 硬编码在此处注册的）。做法参考 SathLabs/FTB-Xaero-Compat。{@code remap=false}：目标是 Xaero 类，不参与 MC 映射。
 */
@Mixin(value = WorldMapSession.class, remap = false)
public class GsWorldMapSessionMixin {

    @Redirect(
            method = "init",
            at = @At(value = "INVOKE", target = "Lxaero/map/highlight/HighlighterRegistry;end()V"),
            remap = false
    )
    private void gsmap$registerBeforeEnd(HighlighterRegistry registry) {
        GuildShelterXaeroClient.registerHighlighters(registry);
        registry.end();
    }
}
