package org.windy.guildshelter.neoforge;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * NeoForge 侧<b>无生成</b>群系采样：直接问种子的 {@link BiomeSource} 某列是不是水域群系（海/深海/河），
 * <b>不加载、不生成区块、不跑"地物装饰(applyBiomeDecoration)"阶段</b>。
 *
 * <p>为什么需要：混合端 + 大量 mod 时，强制生成远处区块会在地物装饰阶段抛 {@code IndexOutOfBoundsException: -1}
 * （某群系特征列表索引对不上，原版/Youer 的 bug，插件无法根治）。原先 {@code WorldManager} 为测水占比/锚陆地
 * 而 {@code loadChunk(...,true)} 强制生成，必然踩中 → 建世界即崩。这里改用 {@code BiomeSource.getNoiseBiome}
 * 纯气候采样（与 {@code /locate biome} 同源），零生成，绕开该 bug。
 *
 * <p>仅在 NeoForge 在场时由 {@code WorldManager} 经 try/catch（链接失败即回退）调用；纯 Bukkit 环境不加载本类。
 */
public final class NeoForgeBiomeSampler {

    private NeoForgeBiomeSampler() {
    }

    /**
     * footprint 矩形（方块坐标，闭区间）上 {@code gridN×gridN} 个均布探点中<b>水域群系</b>占比。
     *
     * @return 0~1 的占比；找不到该世界返回 -1（调用方据此回退）。
     */
    public static double waterBiomeRatio(String worldName, int minX, int maxX, int minZ, int maxZ, int gridN, int sampleBlockY) {
        ServerLevel level = resolve(worldName);
        if (level == null) {
            return -1;
        }
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        int qy = QuartPos.fromBlock(sampleBlockY);
        int n = Math.max(2, gridN);
        int water = 0, total = 0;
        for (int i = 0; i < n; i++) {
            int x = minX + (int) ((long) (maxX - minX) * i / (n - 1));
            int qx = QuartPos.fromBlock(x);
            for (int j = 0; j < n; j++) {
                int z = minZ + (int) ((long) (maxZ - minZ) * j / (n - 1));
                Holder<Biome> biome = source.getNoiseBiome(qx, qy, QuartPos.fromBlock(z), sampler);
                if (isWater(biome)) {
                    water++;
                }
                total++;
            }
        }
        return total == 0 ? 0.0 : (double) water / total;
    }

    /** 某一列是不是水域群系（海/深海/河）；找不到世界返回 false（调用方回退到块判定）。 */
    public static boolean isWaterColumn(String worldName, int blockX, int blockZ, int sampleBlockY) {
        ServerLevel level = resolve(worldName);
        if (level == null) {
            return false;
        }
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        Holder<Biome> biome = source.getNoiseBiome(
                QuartPos.fromBlock(blockX), QuartPos.fromBlock(sampleBlockY), QuartPos.fromBlock(blockZ), sampler);
        return isWater(biome);
    }

    /** 该世界是否能被解析（NeoForge 在场且世界已加载）；WorldManager 用它决定走群系采样还是块兜底。 */
    public static boolean canSample(String worldName) {
        return resolve(worldName) != null;
    }

    private static boolean isWater(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER);
    }

    private static ServerLevel resolve(String worldName) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Identifier id = level.dimension().identifier(); // MC26: ResourceLocation→Identifier, location()→identifier()
            if (id.getPath().equalsIgnoreCase(worldName) || id.toString().equalsIgnoreCase(worldName)) {
                return level;
            }
        }
        return null;
    }
}
