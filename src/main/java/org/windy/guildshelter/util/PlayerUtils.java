package org.windy.guildshelter.util;

import net.minecraft.class_


import org.bukkit.Bukkit;
import org.bukkit.entity.Player as BukkitPlayer;  // Bukkit Player

public class PlayerUtils {

    /**
     * 将 Forge 玩家对象转换为 Bukkit 玩家对象
     *
     * @param forgePlayer Forge 的玩家对象 (Intermediary: net.minecraft.class_1297)
     * @return 对应的 Bukkit 玩家对象，如果未找到，则返回 null
     */
    public static BukkitPlayer getBukkitPlayer(class_1297 forgePlayer) {
        if (forgePlayer == null) {
            return null; // 如果传入的 forgePlayer 为空，则返回 null
        }

        // 获取 Forge 玩家对象的 UUID (使用 Intermediary 映射)
        java.util.UUID playerUUID = forgePlayer.method_5667();  // Intermediary: 获取 UUID 的方法

        // 使用 UUID 查找 Bukkit 玩家
        BukkitPlayer bukkitPlayer = Bukkit.getPlayer(playerUUID);

        // 如果未找到，尝试使用玩家名字查找
        if (bukkitPlayer == null) {
            String playerName = forgePlayer.getName(); // 你可以使用相应的字段或方法来获取玩家名字
            bukkitPlayer = Bukkit.getPlayer(playerName); // 通过玩家名字查找
        }

        return bukkitPlayer;
    }
}
