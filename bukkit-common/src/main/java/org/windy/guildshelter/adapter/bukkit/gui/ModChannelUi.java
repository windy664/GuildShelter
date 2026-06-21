package org.windy.guildshelter.adapter.bukkit.gui;

import org.windy.guildshelter.domain.port.ui.UiBackend;
import org.windy.guildshelter.domain.port.ui.UiView;
import org.windy.guildshelter.domain.port.ui.UiViewer;

import java.util.logging.Logger;

/**
 * 模组联动 UI 后端 —— <b>预留桩，尚未实现</b>。
 *
 * <p>设计意图：摆脱原版 Inventory 的格子/物品栏限制，由配套 NeoForge 模组在客户端渲染自定义 Screen
 * （自由布局、富文本、滚动、表单输入等），插件侧只负责下发「视图数据」+ 接收「点击/输入结果」。
 *
 * <h2>计划中的实现路线</h2>
 * <ol>
 *   <li><b>传输：</b>复用已有的 {@code adapter.bungee.ProxyChannel} 或 Bukkit
 *       {@code PluginMessage}（混合端可直接走自定义网络包），频道如 {@code "guildshelter:ui"}。</li>
 *   <li><b>下行（open）：</b>把 {@link UiView} 的<b>纯数据</b>字段（id/title/items：icon.key/name/lore/actionId）
 *       序列化为 JSON 或字节流发给模组。<b>{@link UiView#context} 不序列化</b>——它是服务端活对象，
 *       留在服务端供动作处理器用。</li>
 *   <li><b>上行（click）：</b>模组回传 {@code (menuId, actionId, 玩家UUID)}；插件侧据此构造
 *       {@link UiViewer}，按玩家当前位置/会话重建 context，交给
 *       {@link org.windy.guildshelter.domain.port.ui.UiActionRouter#handle} 路由——
 *       与原版后端走<b>完全相同</b>的处理器，业务零分叉。</li>
 *   <li><b>握手/降级：</b>{@link #available()} 在模组握手成功前返回 false，使 {@code ui.backend=auto}
 *       自动回退到 {@link BukkitInventoryUi}。</li>
 * </ol>
 *
 * <p>当前为占位：{@link #available()} 恒为 false，{@link #open}/{@link #close} 仅记一条告警，
 * 因此即便 {@code ui.backend=mod} 也不会真正打开（且暂无命令会调用 open）。
 */
public final class ModChannelUi implements UiBackend {

    private final Logger logger;

    public ModChannelUi(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void open(UiViewer viewer, UiView view) {
        logger.warning("[GuildShelter] 模组 UI 后端尚未实现（ModChannelUi），无法打开菜单 "
                + view.id() + "。请将 config 的 ui.backend 设为 vanilla 或 auto。");
    }

    @Override
    public void close(UiViewer viewer) {
        // 桩：模组侧 Screen 关闭由客户端自理，无需服务端动作。
    }

    @Override
    public boolean available() {
        // TODO(模组联动): 模组握手成功后返回 true。
        return false;
    }
}
