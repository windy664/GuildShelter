package org.windy.guildshelter.domain.port.ui;

/**
 * UI 渲染后端端口：把一个 {@link UiView} 呈现给玩家，并回收点击。
 *
 * <p>运行时按载体选择具体实现（{@code config: ui.backend = auto|vanilla|mod}）：
 * <ul>
 *   <li><b>原版 Inventory</b>（{@code BukkitInventoryUi}）—— 兜底实现。易变的 Bukkit
 *       {@code Inventory}/{@code ItemStack}/{@code InventoryView} API 全部被关在这一个类里，
 *       服务端业务零感知；MC/Paper 改这套 API 时只动它一个文件。</li>
 *   <li><b>模组联动</b>（{@code ModChannelUi}，当前为桩）—— 把 {@link UiView} 序列化经
 *       插件↔模组通道发给模组侧自定义 Screen 渲染，点击经通道回传。</li>
 * </ul>
 *
 * <p>实现须保证：渲染只读 {@link UiView} 的纯数据字段；点击落到 {@link UiActionRouter}，
 * 由命令层注册的处理器在服务端解析 {@link UiView#context}。
 */
public interface UiBackend {

    /** 给玩家打开菜单。 */
    void open(UiViewer viewer, UiView view);

    /** 关闭玩家当前菜单。 */
    void close(UiViewer viewer);

    /** 后端是否就绪可用（如模组未加载/通道未握手则 false，供 auto 选择跳过）。 */
    boolean available();
}
