package org.windy.guildshelter.xaeroclient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端侧的公会领地高亮数据（从服务端 {@code guildshelter:map} 的 PLOTS 包填充，highlighter 查询）。
 *
 * <p>纯 Java，无 Minecraft API。{@code chunkKey = ((long)chunkX << 32) | (chunkZ & 0xffffffffL)}。
 * 线程安全（网络线程写、渲染线程读）。
 *
 * <p><b>不按维度精确匹配</b>：混合端（Youer/Bukkit）把公会世界塌缩成 {@code overworld} 维度，
 * highlighter 拿到的 {@code dim.identifier().getPath()} 是 {@code "overworld"}，跟服务端 PLOTS 里发的
 * {@code worldName}（{@code guild_xxx}）永远对不上。而客户端同一时刻只可能在<b>一个</b>公会世界，
 * 故只保留<b>单一活跃数据集</b>，查询忽略 dim（chunk 用绝对世界坐标，本就是该公会专属，不会误撞别的世界）。
 * 进世界 PLOTS 替换、离开世界 CLEAR 清空——靠服务端 enter/leave 推送来界定，而非客户端 dim 判定。
 */
public final class GsMapClaimData {

    /** 一个被高亮 chunk 的渲染信息。 */
    public record Entry(int argb, byte kind, String label) {}

    private static final GsMapClaimData INSTANCE = new GsMapClaimData();

    public static GsMapClaimData get() {
        return INSTANCE;
    }

    /** 当前活跃公会世界的高亮（chunkKey → Entry）；空 = 不在公会世界 / 已清空。 */
    private volatile Map<Long, Entry> active = new ConcurrentHashMap<>();
    /** 仅供日志/调试：活跃数据来自哪个服务端 worldName。 */
    private volatile String activeDim = null;
    private volatile int version;

    private GsMapClaimData() {}

    /** 数据版本：每次替换/清空 +1，highlighter 据此让 Xaero 重绘。 */
    public int version() {
        return version;
    }

    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    public String activeDim() {
        return activeDim;
    }

    /** 用一整张图替换当前活跃高亮（收到 PLOTS 时调）。dimId 仅记录来源，匹配不依赖它。 */
    public void replaceDimension(String dimId, Map<Long, Entry> entries) {
        active = new ConcurrentHashMap<>(entries);
        activeDim = dimId;
        version++;
    }

    /** 清空当前活跃高亮（收到 CLEAR / 离开世界时调）。dimId 仅作语义，无条件清空唯一活跃集。 */
    public void clearDimension(String dimId) {
        if (!active.isEmpty()) {
            active = new ConcurrentHashMap<>();
            activeDim = null;
            version++;
        }
    }

    /** 某 chunk 的高亮信息；无则 null。忽略 dimId（见类注释：混合端维度塌缩）。 */
    public Entry at(String dimId, int chunkX, int chunkZ) {
        return active.get(key(chunkX, chunkZ));
    }

    /** 是否有任何活跃高亮（highlighter 的 region 级快速判定用）。忽略 dimId。 */
    public boolean hasAny(String dimId) {
        return !active.isEmpty();
    }
}
