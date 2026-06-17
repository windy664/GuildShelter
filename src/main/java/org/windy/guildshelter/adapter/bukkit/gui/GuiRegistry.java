package org.windy.guildshelter.adapter.bukkit.gui;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * GUI 注册表：管理菜单实例和动作路由。
 * 命令层注册菜单构造器和动作处理器，GuiProvider 渲染后回调到这里。
 */
public final class GuiRegistry {

    private static GuiRegistry instance;

    /** 动作 ID → 处理器（player, spec）。 */
    private final Map<String, BiConsumer<Player, GuiSpec>> handlers = new HashMap<>();

    /** 菜单 ID → 构造器（用于刷新/重开）。 */
    private final Map<String, BiConsumer<Player, Map<String, Object>>> builders = new HashMap<>();

    private final GuiProvider provider;

    public GuiRegistry(GuiProvider provider) {
        this.provider = provider;
        instance = this;
    }

    public static GuiRegistry getInstance() {
        return instance;
    }

    public GuiProvider provider() {
        return provider;
    }

    /** 注册动作处理器。 */
    public void onAction(String actionId, BiConsumer<Player, GuiSpec> handler) {
        handlers.put(actionId, handler);
    }

    /** 注册菜单构造器。 */
    public void onBuild(String menuId, BiConsumer<Player, Map<String, Object>> builder) {
        builders.put(menuId, builder);
    }

    /** 处理点击动作（由 VanillaGuiProvider 回调）。 */
    boolean handleAction(Player player, String actionId, GuiSpec spec) {
        BiConsumer<Player, GuiSpec> handler = handlers.get(actionId);
        if (handler != null) {
            handler.accept(player, spec);
            return true;
        }
        // 前缀匹配：如 "flag.toggle." 匹配 "flag.toggle.pvp"
        for (Map.Entry<String, BiConsumer<Player, GuiSpec>> entry : handlers.entrySet()) {
            if (actionId.startsWith(entry.getKey())) {
                entry.getValue().accept(player, spec);
                return true;
            }
        }
        return false;
    }

    /** 打开菜单。 */
    public void open(Player player, String menuId, Map<String, Object> context) {
        BiConsumer<Player, Map<String, Object>> builder = builders.get(menuId);
        if (builder != null) {
            builder.accept(player, context != null ? context : Map.of());
        }
    }
}
