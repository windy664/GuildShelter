package org.windy.guildshelter.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.windy.guildshelter.database.DatabaseManager;
import org.windy.guildshelter.database.GuildRegionTable;
import org.windy.guildshelter.database.PlotTable;

import java.sql.*;

public class PermissionCheck {
    public static final Logger LOGGER = LogManager.getLogger();

    public static boolean hasPermission(String playerName, String worldname, int playerX, int playerZ) {
        // 参数验证
        if (playerName == null || playerName.isEmpty() || worldname == null || worldname.isEmpty()) {
            LOGGER.warn("无效的参数: 玩家名称或世界名称为空。");
            return false;
        }

        // 获取数据库连接
        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            if (conn == null || conn.isClosed()) {
                LOGGER.error("数据库连接不可用，正在重新连接...");
                return false; // 如果连接不可用，直接返回失败
            }
            LOGGER.info("正在检查玩家: {} 在世界: {} 中坐标 ({}, {}) 的权限", playerName, worldname, playerX, playerZ);

            // 映射 world 为数据库中的存储值
            if ("overworld".equals(worldname)) {
                worldname = "world";  // 将 'overworld' 映射为 'world'
            }

            // 检查玩家是否在公会区域内
            GuildRegionTable guildRegionTable = new GuildRegionTable();
            if (!guildRegionTable.isInRegion(playerX, playerZ, worldname)) {
                LOGGER.info("玩家 {} 不在公会区域内，权限检查失败。", playerName);
                return false; // 如果不在公会区域内，返回没有权限
            }

            LOGGER.info("玩家 {} 位于公会区域内，正在检查公会地块...", playerName);

            // 检查玩家是否在公会地块内
            PlotTable plotTable = new PlotTable();
            if (!plotTable.isInPlot(playerX, playerZ, worldname)) {
                LOGGER.info("玩家 {} 不在公会地块内，权限检查失败。", playerName);
                return false; // 如果不在公会地块内，返回没有权限
            }

            // 如果玩家在 plot 区域内，检查权限
            String sql = "SELECT * FROM guildshelter_plot WHERE world = ? AND " +
                    "((? BETWEEN LEAST(x1, x2) AND GREATEST(x1, x2)) AND (? BETWEEN LEAST(z1, z2) AND GREATEST(z1, z2)))";

            boolean permissionFound = false;
            int retries = 3; // 重试次数
            while (retries > 0) {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, worldname);  // 设置世界名称
                    pstmt.setInt(2, playerX);       // 设置玩家的 x 坐标
                    pstmt.setInt(3, playerZ);       // 设置玩家的 z 坐标

                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            String owner = rs.getString("owner");
                            String member = rs.getString("member");
                            LOGGER.debug("找到的地块所有者: {}, 成员: {}", owner, member);

                            if (owner.equals(playerName) || member.contains(playerName)) {
                                permissionFound = true;
                                LOGGER.info("玩家 {} 拥有作为所有者或成员的权限。", playerName);
                                break;  // 找到权限后跳出循环
                            }
                        }
                    }
                    if (permissionFound) {
                        return true;  // 如果找到了权限，直接返回
                    }
                    LOGGER.info("玩家 {} 不在地块内，权限检查失败。", playerName);
                    return false;  // 如果不在 plot 内，返回没有权限
                } catch (SQLException e) {
                    LOGGER.error("检查地块权限时失败: {}，正在重试...剩余重试次数: {}", e.getMessage(), retries - 1);
                    retries--;
                    if (retries == 0) {
                        LOGGER.error("重试次数用尽，无法获取地块权限。");
                        return false; // 如果重试次数用尽，返回失败
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.error("获取数据库连接时失败: {}", e.getMessage(), e);
            return false;
        } finally {
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.close(); // 确保数据库连接在查询后关闭
                }
            } catch (SQLException e) {
                LOGGER.error("关闭数据库连接时失败: {}", e.getMessage(), e);
            }
        }

        // 默认返回成功
        return true;
    }
}
