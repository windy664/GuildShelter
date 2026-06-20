package org.windy.guildshelter.adapter.bukkit.listener;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.windy.guildshelter.adapter.bukkit.GuildWorldRegistry;
import org.windy.guildshelter.adapter.bukkit.WorldCache;
import org.windy.guildshelter.domain.layout.Classification;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.rule.LevelRules;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家在公会营地里移动时，按 {@link LayoutCalculator} 判断脚下属于主城 / 庄园#N / 道路，
 * 并在区域变化时弹 title。庄园会进一步标注：有没有被认领、主人、庄园等级、是否在已解锁的实占范围内。
 *
 * <p>仅在跨 chunk 时计算，且只在归类变化时发 title，避免刷屏。
 */
public final class RegionTitleListener implements Listener {

    private final GuildWorldRegistry registry;
    private final WorldCache cache;
    private final LevelRules levels;
    private final org.windy.guildshelter.adapter.bukkit.CityTrustCache cityTrust;
    private final org.windy.guildshelter.domain.port.GuildProvider guildProvider;

    /** 每个玩家上一次所在 chunk 与归类签名，用于去重。 */
    private final Map<UUID, long[]> lastChunk = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastKey = new ConcurrentHashMap<>();

    public RegionTitleListener(GuildWorldRegistry registry, WorldCache cache, LevelRules levels,
                               org.windy.guildshelter.adapter.bukkit.CityTrustCache cityTrust,
                               org.windy.guildshelter.domain.port.GuildProvider guildProvider) {
        this.registry = registry;
        this.cache = cache;
        this.levels = levels;
        this.cityTrust = cityTrust;
        this.guildProvider = guildProvider != null
                ? guildProvider : org.windy.guildshelter.domain.port.GuildProvider.NONE;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (!org.windy.guildshelter.adapter.bukkit.FakePlayerFilter.isRealPlayer(event.getPlayer())) return;
        int cx = event.getTo().getBlockX() >> 4;
        int cz = event.getTo().getBlockZ() >> 4;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        long[] prev = lastChunk.get(id);
        if (prev != null && prev[0] == cx && prev[1] == cz) {
            return; // 同一 chunk，不重复计算
        }
        lastChunk.put(id, new long[]{cx, cz});

        GuildWorld gw = registry.get(player.getWorld().getName());
        if (gw == null) {
            lastKey.remove(id);
            return;
        }

        // 世界 chunk → layout chunk（减去 origin 偏移）；用该世界冻结的布局
        LayoutCalculator layout = new LayoutCalculator(gw.layout());
        int lx = cx - gw.originChunkX();
        int lz = cz - gw.originChunkZ();
        Classification c = layout.classify(lx, lz);

        // 庄园额外算"已解锁/预留"状态并并入去重 key：让玩家在【自己庄园内】跨越解锁边界也能收到提示。
        Manor manor = c.isPlot() ? cache.manorAt(gw, c.slot()) : null; // 2 秒 TTL 缓存，不查库
        boolean plotActive = false;
        if (manor != null) {
            var plot = layout.plotRegion(c.slot());
            plotActive = manor.isUnlocked(lx - plot.minChunkX(), lz - plot.minChunkZ()); // 这一格是否已解锁
        }

        // 主城同理算"已解锁"状态(主城锚 cell0 原点，偏移即 lx/lz)并入 key，使会长跨越主城解锁边界也重弹。
        boolean cityUnlocked = c.isMainCity() && gw.isCityUnlocked(lx, lz);
        String key = c.type() + ":" + c.slot()
                + (c.isPlot() ? ":" + plotActive : "")
                + (c.isMainCity() ? ":" + cityUnlocked : "");
        if (key.equals(lastKey.get(id))) {
            return; // 区域+解锁状态都没变，不重复弹 title
        }
        lastKey.put(id, key);

        switch (c.type()) {
            case MAIN_CITY -> showCityTitle(player, gw, cityUnlocked);
            case ROAD -> player.sendTitle("§7§l道路", "§8公共土径", 5, 20, 10);
            case PLOT -> showPlotTitle(player, gw, manor, plotActive, c.slot());
        }
    }

    private void showPlotTitle(Player player, GuildWorld gw, Manor manor, boolean active, int slot) {
        if (manor != null) {
            if (!active) {
                // 走进了某块庄园的未解锁区。是自己的庄园 → 标题提示 + 聊天栏给可点击的【解锁】按钮（扣额度）。
                boolean own = manor.owner().uuid().equals(player.getUniqueId());
                if (own) {
                    int remain = Math.max(0, manor.quotaCap(gw.layout(), levels.manorMaxLevel()) - manor.unlockedChunks().size());
                    player.sendTitle("§e§l⚠ 未解锁区", "§7剩余额度 §f" + remain
                            + (remain > 0 ? " §7· 看聊天栏点击解锁" : " §7· §c额度已用尽"), 5, 40, 10);
                    sendUnlockButton(player, remain);
                } else {
                    player.sendTitle("§e§l⚠ 未解锁区", "§7庄园 #" + slot + " 的预留区 · 主人解锁后启用", 5, 40, 10);
                }
                return;
            }
            player.sendTitle("§a§l庄园 #" + slot,
                    "§7主人:§f " + ownerName(manor.owner().uuid())
                            + " §7· 庄园 Lv" + manor.level(),
                    5, 40, 10);
            return;
        }
        int capacity = levels.maxMembers(gw.guildLevel());
        if (slot < capacity) {
            player.sendTitle("§e§l空闲庄园 #" + slot, "§7未认领 · 可分配", 5, 40, 10);
        } else {
            player.sendTitle("§8§l预留地块 #" + slot, "§8超出当前容量 · 需升级公会", 5, 40, 10);
        }
    }

    /**
     * 主城标题（与庄园 {@link #showPlotTitle} 同款视觉）：
     * <ul>
     *   <li>已解锁区 → §6§l主城 / 公会中心；</li>
     *   <li>未解锁区 + 会长/副会长 → §e§l⚠ 主城未解锁区 / 剩余额度 N（同庄园样式）+ 聊天可点击解锁；</li>
     *   <li>未解锁区 + 普通成员 → 仍按"主城"显示，不打扰。</li>
     * </ul>
     * 任一路径都<b>必发一个 title</b>（保证一定有提示）；解锁判断包 try-catch，宿主 API 异常不影响基础标题。
     */
    private void showCityTitle(Player player, GuildWorld gw, boolean unlocked) {
        if (!unlocked) {
            try {
                var ref = org.windy.guildshelter.domain.model.PlayerRef.of(player.getUniqueId());
                if (guildProvider.isGuildAdmin(ref, gw.guild()) || player.isOp()) {
                    int remain = Math.max(0, gw.cityQuotaCap(levels.maxGuildLevel())
                            - gw.cityUnlockedChunks().size());
                    player.sendTitle("§e§l⚠ 主城未解锁区", "§7剩余额度 §f" + remain
                            + (remain > 0 ? " §7· 看聊天栏点击解锁" : " §7· §c额度已用尽"), 5, 40, 10);
                    sendCityUnlockButton(player, remain);
                    return;
                }
            } catch (Throwable ignored) {
                // 解锁判断/宿主 API 异常 → 落到下面的基础"主城"标题，保证仍有提示。
            }
        }
        // 已解锁区 / 普通成员 / 异常兜底：基础"主城"标题。
        player.sendTitle("§6§l主城", "§e公会中心 · 公会 Lv" + gw.guildLevel(), 5, 30, 10);
    }

    /** 聊天栏可点击【解锁主城】按钮：点击执行 /gs cityunlock。 */
    private void sendCityUnlockButton(Player player, int remaining) {
        net.md_5.bungee.api.chat.TextComponent line =
                new net.md_5.bungee.api.chat.TextComponent("§6◆ 当前主城区块未解锁 · 剩余额度 §f" + remaining + " §6");
        net.md_5.bungee.api.chat.TextComponent btn;
        if (remaining > 0) {
            btn = new net.md_5.bungee.api.chat.TextComponent("§a§l[ 点击解锁脚下主城区块 ]");
            btn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/gs cityunlock"));
            btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.ComponentBuilder("§7解锁脚下这格主城（消耗 1 主城额度）").create()));
        } else {
            btn = new net.md_5.bungee.api.chat.TextComponent("§c§l[ 主城额度已用尽 · 升级公会等级 ]");
        }
        line.addExtra(btn);
        player.spigot().sendMessage(line);
    }

    /** 在聊天栏发一条带可点击【解锁】按钮的消息：点击执行 /gs unlock 解锁脚下区块。额度为 0 时改提示升级。 */
    private void sendUnlockButton(Player player, int remaining) {
        net.md_5.bungee.api.chat.TextComponent line =
                new net.md_5.bungee.api.chat.TextComponent("§e◆ 当前区块未解锁 · 剩余额度 §f" + remaining + " §e");
        net.md_5.bungee.api.chat.TextComponent btn;
        if (remaining > 0) {
            btn = new net.md_5.bungee.api.chat.TextComponent("§a§l[ 点击解锁脚下区块 ]");
            btn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/gs unlock"));
            btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.ComponentBuilder("§7解锁你正站着的这一格（消耗 1 额度）").create()));
        } else {
            // 额度用尽：庄园升级由管理员授予，玩家不能自助升级（不可点击，仅提示）。
            btn = new net.md_5.bungee.api.chat.TextComponent("§c§l[ 额度已用尽 · 等管理员提升庄园等级 ]");
        }
        line.addExtra(btn);
        player.spigot().sendMessage(line);
    }

    private String ownerName(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String name = op.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }
}
