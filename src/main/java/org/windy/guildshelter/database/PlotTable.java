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

        try (Connection conn = DatabaseManager.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sqlPlotTable);
                stmt.executeUpdate(sqlRtreeTable);
                plugin.LOGGER.info("Guild shelter plot and R-tree table created successfully!");
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to create guild shelter plot or R-tree table: " + e.getMessage());
        }
    }

    // 插入 plot 区域数据
    public void insertPlot(int x1, int z1, int x2, int z2, String owner, String member, String title, String state, String guild, String world, String flag, String doorplate) {
        String sqlPlot = "INSERT INTO guildshelter_plot (owner, member, title, state, guild, world, flag, doorplate) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String sqlRtree = "INSERT INTO guildshelter_plot_tree(id, x1, z1, x2, z2) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            // 插入到 guildshelter_plot 表
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

                // 获取生成的 id
                ResultSet rs = pstmt.getGeneratedKeys();
                int plotId = -1;
                if (rs.next()) {
                    plotId = rs.getInt(1);  // 获取生成的 plot_id
                }

                // 插入到 guildshelter_plot_tree 表
                try (PreparedStatement pstmtRtree = conn.prepareStatement(sqlRtree)) {
                    pstmtRtree.setInt(1, plotId);
                    pstmtRtree.setInt(2, x1);
                    pstmtRtree.setInt(3, z1);
                    pstmtRtree.setInt(4, x2);
                    pstmtRtree.setInt(5, z2);
                    pstmtRtree.executeUpdate();
                }

                conn.commit();
                plugin.LOGGER.info("Plot inserted for Guild: " + guild);
            } catch (SQLException e) {
                conn.rollback();
                plugin.LOGGER.error("Failed to insert plot: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Database connection issue during plot insert: " + e.getMessage());
        }
    }

    // 根据坐标范围查询 Plot
    public PlotData getPlotByRange(int x1, int z1, int x2, int z2, String world) {
        String sqlRtree = "SELECT id FROM guildshelter_plot_tree WHERE x1 BETWEEN ? AND ? AND z1 BETWEEN ? AND ?";
        String sqlPlot = "SELECT * FROM guildshelter_plot WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement pstmtRtree = conn.prepareStatement(sqlRtree)) {
                pstmtRtree.setInt(1, x1);
                pstmtRtree.setInt(2, x2);
                pstmtRtree.setInt(3, z1);
                pstmtRtree.setInt(4, z2);
                ResultSet rsRtree = pstmtRtree.executeQuery();

                while (rsRtree.next()) {
                    int plotId = rsRtree.getInt("id");

                    try (PreparedStatement pstmtPlot = conn.prepareStatement(sqlPlot)) {
                        pstmtPlot.setInt(1, plotId);
                        ResultSet rsPlot = pstmtPlot.executeQuery();

                        if (rsPlot.next()) {
                            return new PlotData(
                                    plotId,
                                    x1, z1, x2, z2,
                                    rsPlot.getString("owner"),
                                    rsPlot.getString("member"),
                                    rsPlot.getString("title"),
                                    rsPlot.getString("state"),
                                    rsPlot.getString("guild"),
                                    rsPlot.getString("world"),
                                    rsPlot.getString("flag"),
                                    rsPlot.getString("doorplate")
                            );
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to query plot data by range: " + e.getMessage());
        }

        return null;
    }
}
