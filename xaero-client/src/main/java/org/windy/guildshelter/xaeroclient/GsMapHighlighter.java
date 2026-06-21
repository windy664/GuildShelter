package org.windy.guildshelter.xaeroclient;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.highlight.ChunkHighlighter;

import java.util.List;

/**
 * 把 {@link GsMapClaimData}（从服务端收的公会领地）画到 Xaero 世界地图上。
 *
 * <p>继承 Xaero 的 {@link ChunkHighlighter}（chunk 粒度），方法签名严格对齐 jar 反编译：
 * {@code getColors} 返回 {@code int[3]}（每个 0xAARRGGBB，null=该 chunk 不高亮），父类据此做边缘混合上色。
 * 维度键用 {@code dim.identifier().getPath()}（MC26 命名，与本项目 NeoForgeProtection 一致 = 服务端 worldName "guild_xxx"）。
 */
public final class GsMapHighlighter extends ChunkHighlighter {

    private boolean loggedQuery; // 一次性日志：确认 Xaero 真的在查询本 highlighter

    public GsMapHighlighter() {
        super(false); // coveringOutsideDiscovered=false：只在已探索区域显示
    }

    private String dimKey(ResourceKey<Level> dim) {
        if (!loggedQuery) {
            loggedQuery = true;
            GuildShelterXaeroClient.LOGGER.info("[gsmap] highlighter queried by Xaero, dim={}, dataHasAny={}",
                    dim.identifier().getPath(), GsMapClaimData.get().hasAny(dim.identifier().getPath()));
        }
        return dim.identifier().getPath();
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        GsMapClaimData.Entry e = GsMapClaimData.get().at(dimKey(dim), chunkX, chunkZ);
        if (e == null) {
            return null;
        }
        int c = e.argb();
        return new int[]{c, c, c}; // 三色给同值 = 纯色填充（父类用它做边缘混合）
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        return GsMapClaimData.get().at(dimKey(dim), chunkX, chunkZ) != null;
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dim, int regionX, int regionZ) {
        return GsMapClaimData.get().hasAny(dimKey(dim)); // 过近似：维度有高亮即让 Xaero 逐 chunk 复查
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dim, int regionX, int regionZ) {
        // 数据版本变了就让所有 region 的 hash 变 → Xaero 重绘。规模小，全量重绘可接受。
        return GsMapClaimData.get().version();
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        GsMapClaimData.Entry e = GsMapClaimData.get().at(dimKey(dim), chunkX, chunkZ);
        return e == null ? null : Component.literal(e.label());
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        GsMapClaimData.Entry e = GsMapClaimData.get().at(dimKey(dim), chunkX, chunkZ);
        return e == null ? null : Component.literal(e.label());
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dim,
                                                 int blockX, int blockZ, int unused) {
        GsMapClaimData.Entry e = GsMapClaimData.get().at(dimKey(dim), blockX >> 4, blockZ >> 4);
        if (e != null) {
            list.add(Component.literal(e.label()));
        }
    }
}
