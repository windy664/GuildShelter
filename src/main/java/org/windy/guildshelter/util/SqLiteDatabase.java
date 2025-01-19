package org.windy.guildshelter.util;

import org.windy.guildshelter.plugin;

import java.sql.*;

public class SqLiteDatabase {

    private static final String DB_URL = "jdbc:sqlite:" + plugin.getPlugin(plugin.class).getDataFolder() + "/database.db";
    private Connection connection;

    // 连接数据库
    public void connect() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL);
                plugin.LOGGER.info("SQLite数据库连接成功！");
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("无法连接到SQLite数据库: " + e.getMessage());
        }
    }

    // 断开数据库连接
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.LOGGER.info("SQLite数据库连接已关闭！");
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("无法关闭SQLite数据库连接: " + e.getMessage());
        }
    }

    // 创建 plot 表
    public void createPlotTable() {
        String sql = "CREATE TABLE IF NOT EXISTS plot (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "x INTEGER NOT NULL, " +
                "y INTEGER NOT NULL, " +
                "player TEXT NOT NULL, " +
                "guild TEXT NOT NULL);";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            plugin.LOGGER.info("plot 表格已创建！");
        } catch (SQLException e) {
            plugin.LOGGER.error("无法创建 plot 表格: " + e.getMessage());
        }
    }

    // 插入 plot 数据
    public void insertPlot(int x, int y, String player, String guild) {
        String sql = "INSERT INTO plot (x, y, player, guild) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, x);
            pstmt.setInt(2, y);
            pstmt.setString(3, player);
            pstmt.setString(4, guild);
            pstmt.executeUpdate();
            plugin.LOGGER.info("已插入 plot 数据: (" + x + ", " + y + ") 玩家: " + player + " 公会: " + guild);
        } catch (SQLException e) {
            plugin.LOGGER.error("插入 plot 数据失败: " + e.getMessage());
        }
    }
}
