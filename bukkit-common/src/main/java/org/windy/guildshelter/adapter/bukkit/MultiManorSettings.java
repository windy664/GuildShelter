package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 多庄园设置 + 每玩家庄园上限解析（权限节点 + config 默认值兜底）。
 *
 * <p>上限来源：
 * <ul>
 *   <li>权限 {@code guildshelter.manors.unlimited} → 无限（{@link Integer#MAX_VALUE}）；</li>
 *   <li>否则从 {@code max-probe} 向下探测 {@code guildshelter.manors.<N>}，命中最大的 N 即节点上限；</li>
 *   <li>无任何节点 → config {@code multi-manor.default-limit}。</li>
 * </ul>
 * 最终取 <b>节点值与 default-limit 的较大者</b>（节点是"提升"语义，不会因没配节点而低于默认）。
 *
 * <p>{@code enabled=false}（默认）时整套多庄园行为关闭，命令层维持"一人一庄园"旧逻辑。
 */
public final class MultiManorSettings {

    private static final String NODE_PREFIX = "guildshelter.manors.";

    private final boolean enabled;
    private final int defaultLimit;
    private final int maxProbe;
    private final double claimCost;

    public MultiManorSettings(boolean enabled, int defaultLimit, int maxProbe, double claimCost) {
        this.enabled = enabled;
        this.defaultLimit = Math.max(1, defaultLimit);
        this.maxProbe = Math.max(1, maxProbe);
        this.claimCost = Math.max(0, claimCost);
    }

    public static MultiManorSettings fromConfig(Plugin plugin) {
        var cfg = plugin.getConfig();
        return new MultiManorSettings(
                cfg.getBoolean("multi-manor.enabled", false),
                cfg.getInt("multi-manor.default-limit", 1),
                cfg.getInt("multi-manor.max-probe", 64),
                cfg.getDouble("multi-manor.claim-cost", 0));
    }

    /** 多庄园是否启用。关闭时命令层走单庄园旧路径。 */
    public boolean enabled() {
        return enabled;
    }

    /** 自助认领花费（需 Vault，0=免费）。 */
    public double claimCost() {
        return claimCost;
    }

    /** 该玩家可拥有的庄园数上限（按公会计）。需在线 Player（权限按玩家求值）。 */
    public int limitFor(Player player) {
        if (player.hasPermission(NODE_PREFIX + "unlimited")) {
            return Integer.MAX_VALUE;
        }
        int node = 0;
        for (int n = maxProbe; n >= 1; n--) {
            if (player.hasPermission(NODE_PREFIX + n)) {
                node = n;
                break;
            }
        }
        return Math.max(node, defaultLimit);
    }
}
