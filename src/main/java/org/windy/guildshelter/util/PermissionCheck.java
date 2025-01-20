package org.windy.guildshelter.util;
import org.windy.guildshelter.database.SqLiteDatabase;
import org.windy.guildshelter.plugin;

import java.sql.*;
public class PermissionCheck {
    public static boolean hasPermission(String playerName, String level, int playerX, int playerZ) {
        SqLiteDatabase.connect();  // Ensure connection to the database

        // SQL 查询语句，查询包含玩家坐标范围的区域
        String sql = "SELECT * FROM guild_plot WHERE level = ? AND " +
                "((? BETWEEN x1 AND x2) AND (? BETWEEN z1 AND z2))";  // 匹配玩家坐标范围

        try (PreparedStatement pstmt = SqLiteDatabase.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, level);  // 设置世界名称
            pstmt.setInt(2, playerX);   // 设置玩家的 x 坐标
            pstmt.setInt(3, playerZ);   // 设置玩家的 z 坐标

            ResultSet rs = pstmt.executeQuery();

            // 遍历查询结果
            while (rs.next()) {
                // 获取当前区域的 owner 和 member 数据
                String owner = rs.getString("owner");
                String member = rs.getString("member");

                // 判断玩家是否是 owner 或 member
                if (owner.equals(playerName) || member.contains(playerName)) {
                    return true;  // 玩家有权限
                }
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to check player permission: " + e.getMessage());
        }

        // 如果没有找到符合条件的权限区域，则返回 false
        return false;
    }
}
