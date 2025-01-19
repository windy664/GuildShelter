package org.windy.guildshelter.database;

import org.windy.guildshelter.plugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.windy.guildshelter.util.GenerateGuildBase.sqLiteDatabase;

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
                "truster TEXT NOT NULL" +
                "guild TEXT NOT NULL);";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            plugin.LOGGER.info("plot 表格已创建！");
        } catch (SQLException e) {
            plugin.LOGGER.error("无法创建 plot 表格: " + e.getMessage());
        }
    }

    // 插入 plot 数据
    public void insertPlot(int x, int y, String player, String truster ,String guild) {
        String sql = "INSERT INTO plot (x, y, player, truster,guild) VALUES (?, ?, ?,?,?)";
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
    public List<PlotData> getPlotsByPlayer(String playerName) {
        List<PlotData> plots = new ArrayList<>();
        String sql = "SELECT * FROM plot WHERE player = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                // 提取数据并加入返回列表
                plots.add(new PlotData(
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getString("player"),
                        rs.getString("truster"),
                        rs.getString("guild")
                ));
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("查询 plot 数据失败: " + e.getMessage());
        }
        return plots;  // 返回所有该玩家的 plot
    }
    public void updateTruster(int x, int y, String truster) {
        String sql = "UPDATE plot SET truster = ? WHERE x = ? AND y = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, truster);
            pstmt.setInt(2, x);
            pstmt.setInt(3, y);
            pstmt.executeUpdate();
            plugin.LOGGER.info("已更新 plot 数据: (" + x + ", " + y + ") 设置 truster 为: " + truster);
        } catch (SQLException e) {
            plugin.LOGGER.error("更新 plot 数据失败: " + e.getMessage());
        }
    }
    public static void updatePlotTrusterByPlayer(String playerName, String truster) {
        // 获取该玩家所有的 plot 数据
        List<PlotData> plots = sqLiteDatabase.getPlotsByPlayer(playerName);

        // 遍历每个 plot，更新 truster
        for (PlotData plot : plots) {
            sqLiteDatabase.updateTruster(plot.getX(), plot.getY(), truster);
        }
    }
    public PlotData getPlotByCoordinates(int plotX, int plotY) {
        String sql = "SELECT * FROM plot WHERE x = ? AND y = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, plotX);
            pstmt.setInt(2, plotY);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // 提取数据并返回一个 PlotData 对象
                return new PlotData(
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getString("player"),
                        rs.getString("truster"),
                        rs.getString("guild")
                );
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("查询 plot 数据失败: " + e.getMessage());
        }
        return null;  // 如果没有找到数据，返回 null
    }
    public Connection getConnection() {
        return connection;  // 返回数据库连接
    }

}
