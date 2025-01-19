package org.windy.guildshelter.util;


import net.minecraft.world.entity.player.Player;
import org.windy.guildshelter.database.SqLiteDatabase;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PermissionCheck {

    // 检查玩家是否在 plot 区域内，并且该区域是否允许玩家操作
    public static boolean checkPlayerInPlotArea(Player player, int playerX, int playerZ, int radius) {
        // 获取玩家的名字
        String playerName = player.getName().getString();  // 获取玩家名称

        // 创建 SqLiteDatabase 实例并连接数据库
        SqLiteDatabase sqLiteDatabase = new SqLiteDatabase();
        sqLiteDatabase.connect();  // 确保已经连接到数据库

        // 执行 SQL 查询，获取所有 plot 数据
        String query = "SELECT * FROM plot";
        try (ResultSet resultSet = sqLiteDatabase.getConnection().createStatement().executeQuery(query)) {
            while (resultSet.next()) {
                // 获取数据库中的 plot 坐标和玩家信息
                int plotX = resultSet.getInt("x");
                int plotY = resultSet.getInt("y");
                String plotPlayer = resultSet.getString("player");
                String plotTruster = resultSet.getString("truster");

                // 使用曼哈顿距离判断玩家是否在 plot 区域内
                int distanceX = Math.abs(plotX - playerX);
                int distanceZ = Math.abs(plotY - playerZ);  // 使用 plotY 作为 z 坐标
                if (distanceX <= radius && distanceZ <= radius) {
                    // 如果玩家在该 plot 区域内，检查玩家是否是该区域的 owner 或 truster
                    if (plotPlayer.equals(playerName) || plotTruster.equals(playerName)) {
                        return true;  // 玩家有权限
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            sqLiteDatabase.disconnect();  // 断开数据库连接
        }

        return false;  // 玩家没有权限
    }
}
