package org.windy.guildshelter.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.windy.guildshelter.database.SqLiteDatabase;

import java.sql.*;

public class PermissionCheck {
    public static final Logger LOGGER = LogManager.getLogger();

    public static boolean hasPermission(String playerName, String worldname, int playerX, int playerZ) {
        // 确保连接到数据库
        SqLiteDatabase.connect();

        LOGGER.info("Checking permissions for player: " + playerName + " at world: " + worldname + " with coordinates (" + playerX + ", " + playerZ + ")");

        // SQL 查询语句，查询包含玩家坐标范围的区域，同时根据 worldname 查询
        String sql = "SELECT * FROM guild_plot WHERE world = ? AND " +
                "((? BETWEEN x1 AND x2) AND (? BETWEEN z1 AND z2))";  // 匹配玩家坐标范围

        try (PreparedStatement pstmt = SqLiteDatabase.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, worldname);  // 设置世界名称（传入的 worldname）
            pstmt.setInt(2, playerX);       // 设置玩家的 x 坐标
            pstmt.setInt(3, playerZ);       // 设置玩家的 z 坐标

            LOGGER.info("Executing query: " + pstmt.toString());  // 打印 SQL 查询语句

            ResultSet rs = pstmt.executeQuery();

            // 遍历查询结果
            while (rs.next()) {
                // 获取当前区域的 owner 和 member 数据
                String owner = rs.getString("owner");
                String member = rs.getString("member");

                LOGGER.info("Found plot: Owner: " + owner + " Members: " + member);

                // 判断玩家是否是 owner 或 member
                if (owner.equals(playerName) || member.contains(playerName)) {
                    LOGGER.info("Player " + playerName + " has permission in this area.");
                    return true;  // 玩家有权限
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to check player permission: " + e.getMessage());
            return false;  // 发生错误时返回没有权限
        }

        // 如果没有找到符合条件的权限区域，则返回 false
        LOGGER.info("No permission found for player " + playerName);
        return false;
    }
}
