package org.windy.guildshelter.adapter.bukkit.gui;

import org.bukkit.entity.Player;

/**
 * 外部 UI 模组适配器接口（占位）。
 * 未来接具体模组（CustomUI/FormAPI/其他）只需实现此接口。
 *
 * <p>实现方式：
 * 1. 创建一个类实现 ExternalGuiAdapter
 * 2. 在 GuildShelterPlugin.onEnable 中检测模组是否存在
 * 3. 存在 → 创建 ExternalGuiProvider 包装此适配器
 * 4. 不存在 → fallback 到 VanillaGuiProvider
 */
public interface ExternalGuiAdapter {

    /** 打开表单菜单。 */
    void showForm(Player player, FormSpec spec);

    /** 关闭表单。 */
    void closeForm(Player player);

    /**
     * 表单规格（简化版，供外部 UI 模组使用）。
     */
    record FormSpec(
        String title,
        String content,
        java.util.List<FormField> fields
    ) {}

    /**
     * 表单字段。
     */
    record FormField(
        String id,
        String label,
        FieldType type,
        String defaultValue,
        java.util.List<String> options
    ) {}

    enum FieldType {
        TEXT, TOGGLE, DROPDOWN, SLIDER, BUTTON
    }
}
