package org.windy.guildshelter.util;

import net.minecraft.world.entity.player.Player;
import org.windy.guildshelter.database.SqLiteDatabase;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class PermissionCheck {

    // 检查玩家是否在 plot 区域内，并且该区域是否允许玩家操作
    public static boolean checkPlayerInPlotArea(Player player, int playerX, int playerZ, int radius) {
        // 获取玩家的名字
        String playerName = player.getName().getString();  // 获取玩家名称

        // 输出玩家的当前位置和检查半径
        System.out.println("[PermissionCheck] Player " + playerName + " is checking plot area at (" + playerX + ", " + playerZ + ") with radius " + radius);

        // 获取已经建立的数据库连接
        try (Statement stmt = SqLiteDatabase.getConnection().createStatement()) {
            // 执行 SQL 查询，获取所有 plot 数据
            String query = "SELECT * FROM plot";
            ResultSet resultSet = stmt.executeQuery(query);

            while (resultSet.next()) {
                // 获取数据库中的 plot 坐标和玩家信息
                int plotX = resultSet.getInt("x");
                int plotY = resultSet.getInt("y");  // plotY 是高度（Y轴），不用于计算水平距离
                String plotPlayer = resultSet.getString("player");
                String plotTruster = resultSet.getString("truster");

                // 输出从数据库中获取到的 plot 信息
                System.out.println("[PermissionCheck] Checking plot at (" + plotX + ", " + plotY + ") owned by " + plotPlayer + " and trusted by " + plotTruster);

                // 使用曼哈顿距离判断玩家是否在 plot 区域内
                // 曼哈顿距离只涉及 x 和 z 坐标，而不是 y 坐标
                int distanceX = Math.abs(plotX - playerX);
                int distanceZ = Math.abs(plotY - playerZ);  // 这里是使用 plotX 和 plotY 作为水平距离计算
                System.out.println("[PermissionCheck] Distance to plot: X = " + distanceX + ", Z = " + distanceZ);

                if (distanceX <= radius && distanceZ <= radius) {
                    // 如果玩家在该 plot 区域内，检查玩家是否是该区域的 owner 或 truster
                    System.out.println("[PermissionCheck] Player is within the plot area. Checking ownership or trust.");
                    if (plotPlayer.equals(playerName) || plotTruster.equals(playerName)) {
                        System.out.println("[PermissionCheck] Player " + playerName + " has permission for this plot.");
                        return true;  // 玩家有权限
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("[PermissionCheck] Player " + playerName + " does not have permission in the plot area.");
        return false;  // 玩家没有权限
    }
}
