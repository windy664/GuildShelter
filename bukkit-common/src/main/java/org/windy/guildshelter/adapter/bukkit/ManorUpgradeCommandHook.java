package org.windy.guildshelter.adapter.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.port.ManorUpgradeHook;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link ManorUpgradeHook} 的 config 驱动实现：庄园升到<b>对应等级</b>时，由控制台执行该等级配的<b>对应命令</b>。
 *
 * <p>只执行<b>升到的那一级</b>的命令（不累加更低等级）。命令以控制台身份分派，故 give/cmi/lp 等任意插件命令均可挂接。
 * 配置见 {@code config.yml} 的 {@code manor-upgrade-commands.levels}（键=目标等级，值=命令列表，命令不带前导 {@code /}）。
 *
 * <p>占位符：{@code %player%}(庄主名) {@code %uuid%} {@code %guild%}(公会 ID) {@code %level%}(新等级)
 * {@code %old_level%}(旧等级) {@code %slot%}。
 */
public final class ManorUpgradeCommandHook implements ManorUpgradeHook {

    private final JavaPlugin plugin;
    /** 目标等级 → 命令模板列表。 */
    private final Map<Integer, List<String>> byLevel;

    private ManorUpgradeCommandHook(JavaPlugin plugin, Map<Integer, List<String>> byLevel) {
        this.plugin = plugin;
        this.byLevel = byLevel;
    }

    /**
     * 从 config 构建；未启用或没有任何等级配命令时返回 {@code null}（调用方据此跳过注入）。
     */
    public static ManorUpgradeCommandHook fromConfig(JavaPlugin plugin) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("manor-upgrade-commands");
        if (root == null || !root.getBoolean("enabled", false)) {
            return null;
        }
        ConfigurationSection levels = root.getConfigurationSection("levels");
        if (levels == null) {
            return null;
        }
        Map<Integer, List<String>> byLevel = new HashMap<>();
        for (String key : levels.getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(key.trim());
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("manor-upgrade-commands.levels 含非法等级键 '" + key + "'，已跳过。");
                continue;
            }
            List<String> cmds = levels.getStringList(key);
            if (!cmds.isEmpty()) {
                byLevel.put(level, List.copyOf(cmds));
            }
        }
        if (byLevel.isEmpty()) {
            return null;
        }
        return new ManorUpgradeCommandHook(plugin, byLevel);
    }

    @Override
    public void onUpgrade(Manor manor, int oldLevel) {
        List<String> templates = byLevel.get(manor.level());
        if (templates == null || templates.isEmpty()) {
            return;
        }
        UUID uuid = manor.owner().uuid();
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        String name = off.getName() != null ? off.getName() : uuid.toString();
        String guild = manor.guild().value();
        int newLevel = manor.level();
        int slot = manor.slot();

        // 调度到主线程执行（dispatchCommand 须在主线程；upgradeManor 多由命令触发本就在主线程，这里仍兜底）。
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (String template : templates) {
                String cmd = template
                        .replace("%player%", name)
                        .replace("%uuid%", uuid.toString())
                        .replace("%guild%", guild)
                        .replace("%level%", Integer.toString(newLevel))
                        .replace("%old_level%", Integer.toString(oldLevel))
                        .replace("%slot%", Integer.toString(slot))
                        .trim();
                if (cmd.isEmpty()) {
                    continue;
                }
                if (cmd.startsWith("/")) {
                    cmd = cmd.substring(1); // 容错：配置误带前导 /
                }
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                } catch (Exception e) {
                    plugin.getLogger().warning("庄园升级命令执行失败 [lv" + newLevel + "] '" + cmd + "': " + e.getMessage());
                }
            }
        });
    }
}
