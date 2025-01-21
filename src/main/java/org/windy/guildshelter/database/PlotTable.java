package org.windy.guildshelter.database;

import org.windy.guildshelter.plugin;
import java.sql.*;

public class PlotTable {

    // 创建 plot 表和 R-tree 表
    public void createPlotTable() {
        String sqlPlotTable = "CREATE TABLE IF NOT EXISTS guildshelter_plot (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "owner TEXT NOT NULL, " +
                "member TEXT NOT NULL, " +
                "title TEXT NOT NULL, " +
                "state TEXT NOT NULL, " +
                "guild TEXT NOT NULL, " +
                "world TEXT NOT NULL, " +
                "flag TEXT, " +
                "doorplate TEXT);";

        String sqlRtreeTable = "CREATE VIRTUAL TABLE IF NOT EXISTS guildshelter_plot_tree USING rtree(id, x1, z1, x2, z2);";

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sqlPlotTable);
            stmt.executeUpdate(sqlRtreeTable);
            plugin.LOGGER.info("Guild shelter plot and R-tree table created successfully!");
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to create guild shelter plot or R-tree table: " + e.getMessage(), e);
        }
    }

    // 插入 plot 区域数据
    public void insertPlot(int x1, int z1, int x2, int z2, String owner, String member, String title, String state, String guild, String world, String flag, String doorplate) {
        String sqlPlot = "INSERT INTO guildshelter_plot (owner, member, title, state, guild, world, flag, doorplate) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String sqlRtree = "INSERT INTO guildshelter_plot_tree(id, x1, z1, x2, z2) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false); // 启用事务

            try (PreparedStatement pstmt = conn.prepareStatement(sqlPlot, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, owner);
                pstmt.setString(2, member);
                pstmt.setString(3, title);
                pstmt.setString(4, state);
                pstmt.setString(5, guild);
                pstmt.setString(6, world);
                pstmt.setString(7, flag);
                pstmt.setString(8, doorplate);
                pstmt.executeUpdate();

                ResultSet rs = pstmt.getGeneratedKeys();
                int plotId = -1;
                if (rs.next()) {
                    plotId = rs.getInt(1);  // 获取生成的 plot_id
                }

                // 插入到 guildshelter_plot_tree 表
                if (plotId != -1) {
                    try (PreparedStatement pstmtRtree = conn.prepareStatement(sqlRtree)) {
                        pstmtRtree.setInt(1, plotId);
                        pstmtRtree.setInt(2, x1);
                        pstmtRtree.setInt(3, z1);
                        pstmtRtree.setInt(4, x2);
                        pstmtRtree.setInt(5, z2);
                        pstmtRtree.executeUpdate();
                    }
                }

                conn.commit();  // 提交事务
                plugin.LOGGER.info("Plot inserted for Guild: " + guild);
            } catch (SQLException e) {
                conn.rollback();  // 回滚事务
                plugin.LOGGER.error("Failed to insert plot: " + e.getMessage(), e);
            } finally {
                conn.setAutoCommit(true);  // 恢复自动提交
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Database connection issue during plot insert: " + e.getMessage(), e);
        }
    }

    // 根据坐标范围查询 Plot
    public PlotData getPlotByRange(int x1, int z1, int x2, int z2, String world) {
        // 优化：直接联结查询，减少查询次数
        String sql = "SELECT p.id, p.owner, p.member, p.title, p.state, p.guild, p.world, p.flag, p.doorplate, " +
                "pt.x1, pt.z1, pt.x2, pt.z2 " +
                "FROM guildshelter_plot p " +
                "JOIN guildshelter_plot_tree pt ON p.id = pt.id " +
                "WHERE pt.x1 BETWEEN ? AND ? AND pt.z1 BETWEEN ? AND ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, x1);
            pstmt.setInt(2, x2);
            pstmt.setInt(3, z1);
            pstmt.setInt(4, z2);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new PlotData(
                        rs.getInt("id"),
                        rs.getInt("x1"), rs.getInt("z1"), rs.getInt("x2"), rs.getInt("z2"),
                        rs.getString("owner"),
                        rs.getString("member"),
                        rs.getString("title"),
                        rs.getString("state"),
                        rs.getString("guild"),
                        rs.getString("world"),
                        rs.getString("flag"),
                        rs.getString("doorplate")
                );
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to query plot data by range: " + e.getMessage(), e);
        }

        return null;
    }
    // 判断点是否在 Plot 区域内Where
    public boolean isInPlot(int x, int z, String world) {
        String sql = "SELECT id FROM guildshelter_plot_tree WHERE x1 <= ? AND x2 >= ? AND z1 <= ? AND z2 >= ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, x); // 设置 x 坐标
            pstmt.setInt(2, x); // 设置 x 坐标
            pstmt.setInt(3, z); // 设置 z 坐标
            pstmt.setInt(4, z); // 设置 z 坐标
            ResultSet rs = pstmt.executeQuery();

            return rs.next();  // 如果有结果，说明该点在某个 plot 区域内
        } catch (SQLException e) {
            plugin.LOGGER.error("查询点是否在 plot 区域内失败: " + e.getMessage(), e);
        }

        return false;  // 如果没有结果，说明该点不在任何 plot 区域内
    }
    public Integer getPlotId(int x, int z, String world) {
        String sql = "SELECT id FROM guildshelter_plot_tree WHERE x1 <= ? AND x2 >= ? AND z1 <= ? AND z2 >= ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, x); // 设置 x 坐标
            pstmt.setInt(2, x); // 设置 x 坐标
            pstmt.setInt(3, z); // 设置 z 坐标
            pstmt.setInt(4, z); // 设置 z 坐标
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("id");  // 返回找到的 plot id
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("查询点对应的 plot id 失败: " + e.getMessage(), e);
        }

        return null;  // 如果没有找到，返回 null
    }

}
