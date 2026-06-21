package org.windy.guildshelter.adapter.bukkit.gui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.windy.guildshelter.domain.port.ui.UiIcon;
import org.windy.guildshelter.domain.port.ui.UiItem;
import org.windy.guildshelter.domain.port.ui.UiView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 从 gui.yml 加载菜单定义，转换为平台中立的 {@link UiView}。
 * 缺失的菜单/按钮用硬编码默认值兜底（{@link Menus}）。
 *
 * <p>材质名只当字符串存进 {@link UiIcon}，不在此解析为 Bukkit {@code Material}——交给具体后端
 * （{@code BukkitInventoryUi} 用 {@code matchMaterial}，模组后端按命名空间 id），保持加载器平台中立。
 */
public final class YamlGuiLoader {

    private final YamlConfiguration config;
    private final Logger logger;

    public YamlGuiLoader(File dataFolder, Logger logger) {
        this.logger = logger;
        // 按语言优先级加载：gui_{lang}.yml → gui.yml
        String lang = org.windy.guildshelter.adapter.bukkit.Messages.lang();
        File file = new File(dataFolder, "gui_" + lang + ".yml");
        if (!file.exists()) {
            file = new File(dataFolder, "gui.yml");
        }
        if (!file.exists()) {
            // 从 resources 复制默认文件
            String resName = "/gui_" + lang + ".yml";
            InputStream res = getClass().getResourceAsStream(resName);
            if (res == null) res = getClass().getResourceAsStream("/gui.yml");
            if (res != null) {
                try (InputStreamReader reader = new InputStreamReader(res, StandardCharsets.UTF_8)) {
                    YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                    defaults.save(file);
                } catch (IOException e) {
                    logger.warning("[GuildShelter] 保存默认 gui.yml 失败: " + e.getMessage());
                }
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        logger.info("[GuildShelter] GUI 配置已加载: " + file.getName());
    }

    /** 加载指定菜单的 {@link UiView}，返回 null 表示未定义（应用硬编码默认）。 */
    public UiView loadMenu(String menuId, Map<String, Object> context) {
        ConfigurationSection section = config.getConfigurationSection("menus." + menuId);
        if (section == null) {
            return null;
        }
        String title = section.getString("title", "§8[§6GuildShelter§8]");
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 3)));

        Map<Integer, UiItem> items = new HashMap<>();
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    ConfigurationSection itemSec = itemsSection.getConfigurationSection(key);
                    if (itemSec == null) continue;
                    UiItem item = parseItem(itemSec);
                    if (item != null) {
                        items.put(slot, item);
                    }
                } catch (NumberFormatException e) {
                    logger.warning("[GuildShelter] gui.yml 菜单 " + menuId + " 的 slot 不是数字: " + key);
                }
            }
        }

        // 填充分隔线（空 slot 用玻璃板）
        String paneMat = config.getString("settings.pane-material", "GRAY_STAINED_GLASS_PANE");
        UiIcon pane = UiIcon.of(paneMat);
        for (int i = 0; i < rows * 9; i++) {
            if (!items.containsKey(i)) {
                items.put(i, UiItem.separator(pane));
            }
        }

        return new UiView(menuId, title, rows, items, context);
    }

    private UiItem parseItem(ConfigurationSection sec) {
        String matName = sec.getString("material", "PAPER");
        int customModelData = sec.getInt("custom-model-data", 0);
        String name = sec.getString("name", "");
        List<String> lore = sec.getStringList("lore");
        String action = sec.getString("action", "");
        return UiItem.of(UiIcon.of(matName, customModelData), name, lore, action);
    }

    /** 检查某菜单是否在 gui.yml 中定义。 */
    public boolean hasMenu(String menuId) {
        return config.contains("menus." + menuId);
    }
}
