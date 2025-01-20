package org.windy.guildshelter.util;
import org.windy.guildshelter.database.SqLiteDatabase;
import org.windy.guildshelter.plugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
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

    // 测试方法
    public static void main(String[] args) {
        String playerName = "player123";
        String level = "world";  // 世界名称
        int playerX = 150;       // 玩家 x 坐标
        int playerZ = 70;        // 玩家 z 坐标

        boolean hasPermission = hasPermission(playerName, level, playerX, playerZ);
        System.out.println("Player has permission: " + hasPermission);
    }
}
