package org.windy.guildshelter.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.windy.guildshelter.database.DatabaseManager;

import java.sql.*;

public class PermissionCheck {
    public static final Logger LOGGER = LogManager.getLogger();

    public static boolean hasPermission(String playerName, String worldname, int playerX, int playerZ) {
        // 获取数据库连接
        try (Connection conn = DatabaseManager.getConnection()) {
            LOGGER.info("Checking permissions for player: " + playerName + " at world: " + worldname + " with coordinates (" + playerX + ", " + playerZ + ")");

            // 映射 world 为数据库中的存储值
            if (worldname.equals("overworld")) {
                worldname = "world";  // 将 'overworld' 映射为 'world'
            }

            // SQL 查询语句，查询包含玩家坐标范围的区域，同时根据 worldname 查询
            String sql = "SELECT * FROM guild_plot WHERE world = ? AND " +
                    "((? BETWEEN LEAST(x1, x2) AND GREATEST(x1, x2)) AND (? BETWEEN LEAST(z1, z2) AND GREATEST(z1, z2)))";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                LOGGER.info("Executing query: " + sql);
                LOGGER.info("Parameters: world=" + worldname + ", x=" + playerX + ", z=" + playerZ);

                pstmt.setString(1, worldname);  // 设置世界名称（传入的 worldname）
                pstmt.setInt(2, playerX);       // 设置玩家的 x 坐标
                pstmt.setInt(3, playerZ);       // 设置玩家的 z 坐标

                ResultSet rs = pstmt.executeQuery();

                boolean permissionFound = false; // 标记是否找到权限
                while (rs.next()) {
                    String owner = rs.getString("owner");
                    String member = rs.getString("member");
                    LOGGER.info("Found plot with owner: " + owner + ", members: " + member);

                    if (owner.equals(playerName) || member.contains(playerName)) {
                        permissionFound = true;
                        LOGGER.info("Player " + playerName + " has permission as owner or member.");
                        break;  // 找到权限后跳出循环
                    }
                }

                if (!permissionFound) {
                    LOGGER.info("No permission found for player " + playerName);
                    return false;
                }

            } catch (SQLException e) {
                LOGGER.error("Failed to check player permission: " + e.getMessage());
                LOGGER.error("SQL State: " + e.getSQLState());
                LOGGER.error("Error Code: " + e.getErrorCode());
                return false;  // 发生错误时返回没有权限
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get database connection: " + e.getMessage());
            return false;
        }

        return true; // 如果玩家有权限，返回 true
    }
}
