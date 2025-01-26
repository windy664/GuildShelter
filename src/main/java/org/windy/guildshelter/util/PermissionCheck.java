package org.windy.guildshelter.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.windy.guildshelter.database.mysql.AreaInfo;
import org.windy.guildshelter.database.mysql.DatabaseManager;

public class PermissionCheck {

    private static final Logger LOGGER = LogManager.getLogger();

    private final DatabaseManager databaseManager;

    public PermissionCheck(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    // 判断玩家是否有权限
    public boolean hasPermission(String playerName, String worldname, double playerX, double playerZ) {
        if (databaseManager == null) {
            LOGGER.error("数据库管理器未初始化！");
            return false;  // 如果未初始化，返回 false
        }


        // 获取 GuildShelter_plot 数据
        AreaInfo areaInfo = databaseManager.isPointInPlotAreaWithCache(playerX, playerZ);
        if (areaInfo == null) {
            LOGGER.error("未找到与玩家坐标 ({}, {}) 相关的区域信息", playerX, playerZ);
            return false;  // 如果没有找到区域信息，返回 false
        }

        String member = areaInfo.getMember();
        String owner = areaInfo.getOwner();

        // 检查玩家是否在公会区域内
        if (areaInfo.isInArea() && (member.contains(playerName) || owner.equals(playerName))) {
            LOGGER.info("玩家 {} 在公会区域内，拥有权限。", playerName);
            return true;  // 玩家在公会区域内
        } else {
            LOGGER.info("玩家 {} 没有权限，未在公会区域内。", playerName);
            return false;  // 玩家不在公会区域内
        }
    }
}
