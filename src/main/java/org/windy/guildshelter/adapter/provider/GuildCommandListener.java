package org.windy.guildshelter.adapter.provider;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * Guild(com.guild)不发任何自定义事件，纯靠 {@link GuildPluginSyncTask} 30s 轮询会有最多一轮的延迟。
 * 这里补一个即时触发：监听玩家敲 /guild ，命令执行后延迟几 tick 主动踢一次同步，
 * 做到"建会 / 入会即时出世界"。轮询仍保留当安全网，覆盖 GUI 操作、其它插件改库等命令外的变更。
 *
 * <p>只认 base 指令 {@code /guild}（放过 guildchat/guildgui 等只读指令），不去解析子指令——
 * 子指令名是 Guild 插件内部约定、可能本地化，依赖它太脆；同步本身幂等且去抖，多触发一次只是廉价空转。
 * 命令预处理事件在命令真正执行<b>之前</b>触发，所以延迟到下一 tick 再读，名单已更新。
 */
public final class GuildCommandListener implements Listener {

    private final GuildPluginSyncTask task;
    private final Plugin plugin;
    private final long delayTicks;

    public GuildCommandListener(GuildPluginSyncTask task, Plugin plugin, long delayTicks) {
        this.task = task;
        this.plugin = plugin;
        this.delayTicks = delayTicks;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (msg.length() < 2 || msg.charAt(0) != '/') {
            return;
        }
        // 取出第一段(去掉前导 '/'、可能的命名空间前缀、参数)，小写比较。
        String head = msg.substring(1).trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int colon = head.indexOf(':'); // 形如 /guild:guild 的带命名空间写法
        if (colon >= 0) {
            head = head.substring(colon + 1);
        }
        if (head.equals("guild")) {
            task.requestImmediateSync(plugin, delayTicks);
        }
    }
}
