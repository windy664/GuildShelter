package org.windy.guildshelter.adapter.bukkit.world.iris;

import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.math.Position2;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 用 Iris 自己的<b>异步多线程预生成器</b>（{@link IrisToolbelt#pregenerate}）把一块区域先生成好，
 * 再回主线程跑整地/铺路——这样整地/铺路只在<b>已生成</b>的区块上写方块，绝不在主线程同步触发 Iris 重型生成。
 *
 * <p><b>这正是"用上 Iris 黑科技"的关键</b>：建会后卡顿的根因是 {@code level.getChunk(..)} /
 * {@code world.loadChunk(.., true)} 把 Iris 的重型生成拽到<b>主线程</b>一块一块地跑（铺路/围墙常延伸到
 * 未生成区块），把 Iris 的异步多线程生成池整个废掉了。先 pregen、后写方块即可彻底避开。
 *
 * <p><b>串行队列</b>：Iris 的 {@code PregeneratorJob} 是<b>全局单实例</b>——再建一个会 {@code close} 掉正在跑的那个。
 * 所以多公会的预生成必须排队，一个跑完（{@code whenDone}）再起下一个，避免互相中止留下半生成区域。
 * 重叠区域的后续任务会命中"已存在区块"快速跳过，几乎零成本。
 *
 * <p><b>惰性隔离</b>：直接引用 {@code com.volmit.iris.*}（compileOnly，运行期由 Iris 插件提供）。本类仅在
 * Iris 在场时由 {@link org.windy.guildshelter.adapter.bukkit.world.IrisPregenTerrainPreparer} 触达
 * （JVM 惰性解析：Iris 不在则本类永不加载）。所有方法须在<b>主线程</b>调用（队列/状态无锁）。
 */
public final class IrisPregen {

    private IrisPregen() {
    }

    /** 预生成区域边缘外扩(格)：覆盖架桥护栏读取的外侧 1 格 / 围墙外侧判定所在区块，免边界列仍触发同步生成。 */
    private static final int MARGIN_BLOCKS = 16;

    /** 一次预生成请求：世界 + 已含外扩 margin 的方块边界 + 生成完毕后（主线程）要跑的整地动作。 */
    private record Request(World world, int minX, int minZ, int maxX, int maxZ, Runnable whenReady) {
    }

    private static final Deque<Request> QUEUE = new ArrayDeque<>();
    private static boolean running = false;

    /**
     * 确保 {@code [minX,minZ]~[maxX,maxZ]}（世界<b>方块</b>坐标）所在区块已由 Iris 异步生成，完成后在主线程跑
     * {@code whenReady}。非 Iris 世界 / 世界为 null → 立即跑 {@code whenReady}（行为同改造前）。须在主线程调用。
     */
    public static void ensureGenerated(Plugin plugin, World world,
                                       int minX, int minZ, int maxX, int maxZ, Runnable whenReady) {
        if (world == null || !IrisToolbelt.isIrisWorld(world)) {
            whenReady.run();
            return;
        }
        QUEUE.add(new Request(world, minX - MARGIN_BLOCKS, minZ - MARGIN_BLOCKS,
                maxX + MARGIN_BLOCKS, maxZ + MARGIN_BLOCKS, whenReady));
        pump(plugin);
    }

    /** 若空闲则取队首起一个预生成任务；{@code whenDone} 回主线程跑整地并接力下一个。 */
    private static void pump(Plugin plugin) {
        if (running) {
            return;
        }
        Request req = QUEUE.poll();
        if (req == null) {
            return;
        }
        running = true;
        int centerX = (req.minX + req.maxX) / 2;
        int centerZ = (req.minZ + req.maxZ) / 2;
        int radiusX = Math.max(1, (req.maxX - req.minX + 1) / 2);
        int radiusZ = Math.max(1, (req.maxZ - req.minZ + 1) / 2);
        try {
            PregenTask task = PregenTask.builder()
                    .center(new Position2(centerX, centerZ)) // 方块坐标，Iris 内部 >>9 换算到 region
                    .radiusX(radiusX)
                    .radiusZ(radiusZ)
                    .gui(false) // 不弹 Swing 预览窗
                    .build();
            IrisToolbelt.pregenerate(task, req.world).whenDone(() ->
                    // whenDone 在 Iris 线程回调 → 回主线程跑整地，并 finally 接力下一个排队任务。
                    Bukkit.getScheduler().runTask(plugin, () -> finish(plugin, req)));
        } catch (Throwable t) {
            plugin.getLogger().warning("[GuildShelter] Iris 预生成失败，回退直接整地("
                    + req.world.getName() + "): " + t);
            finish(plugin, req); // 已在主线程：直接跑整地（仍可能同步生成，但不卡死流程）并接力队列。
        }
    }

    /** 跑该请求的整地动作，无论成败都释放 running 并泵下一个（绝不让队列卡死）。 */
    private static void finish(Plugin plugin, Request req) {
        try {
            req.whenReady.run();
        } catch (Throwable t) {
            plugin.getLogger().warning("[GuildShelter] Iris 预生成后整地动作异常(" + req.world.getName() + "): " + t);
        } finally {
            running = false;
            pump(plugin);
        }
    }
}
