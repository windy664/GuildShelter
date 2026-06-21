package org.windy.guildshelter.domain.port.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 菜单动作路由表：命令层注册「动作 id → 处理器」和「菜单 id → 构造器」，后端渲染后把点击回调到这里。
 *
 * <p>平台中立——处理器签名是 {@code (UiViewer, UiView)}，与具体 UI 后端无关，原版 Inventory 和模组
 * 通道共用同一套路由。处理器从 {@link UiView#context}（服务端活对象）取数据执行业务。
 *
 * <p><b>预留说明：</b>当前还没有任何命令注册菜单/处理器（GUI 入口未接）。本类是接线点：未来
 * {@code /gs gui} 命令在 onEnable 期间 {@link #onBuild}/{@link #onAction} 注册各面板即可，
 * 无需改动后端。
 */
public final class UiActionRouter {

    /** 动作 id → 处理器。 */
    private final Map<String, BiConsumer<UiViewer, UiView>> handlers = new HashMap<>();

    /** 菜单 id → 构造器（用于刷新/重开；value 为上下文）。 */
    private final Map<String, BiConsumer<UiViewer, Map<String, Object>>> builders = new HashMap<>();

    /** 注册动作处理器。 */
    public void onAction(String actionId, BiConsumer<UiViewer, UiView> handler) {
        handlers.put(actionId, handler);
    }

    /** 注册菜单构造器。 */
    public void onBuild(String menuId, BiConsumer<UiViewer, Map<String, Object>> builder) {
        builders.put(menuId, builder);
    }

    /** 触发菜单构造（如重开某菜单）。返回是否有对应构造器。 */
    public boolean build(String menuId, UiViewer viewer, Map<String, Object> context) {
        BiConsumer<UiViewer, Map<String, Object>> builder = builders.get(menuId);
        if (builder == null) return false;
        builder.accept(viewer, context);
        return true;
    }

    /**
     * 路由一次点击动作。先精确匹配，再前缀匹配（如 {@code "flag.toggle."} 命中 {@code "flag.toggle.pvp"}）。
     *
     * @return 是否有处理器接管。
     */
    public boolean handle(UiViewer viewer, String actionId, UiView view) {
        if (actionId == null || actionId.isEmpty()) return false;
        BiConsumer<UiViewer, UiView> exact = handlers.get(actionId);
        if (exact != null) {
            exact.accept(viewer, view);
            return true;
        }
        for (Map.Entry<String, BiConsumer<UiViewer, UiView>> entry : handlers.entrySet()) {
            if (actionId.startsWith(entry.getKey())) {
                entry.getValue().accept(viewer, view);
                return true;
            }
        }
        return false;
    }
}
