package org.windy.guildshelter.database;

import org.windy.guildshelter.plugin;
import java.sql.*;

public class PlotTable {

    // Create the plot table and the R-tree table
    public void createPlotTable() {
        String sqlPlotTable = "CREATE TABLE IF NOT EXISTS guild_plot (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "owner TEXT NOT NULL, " +
                "member TEXT NOT NULL, " +
                "guild TEXT NOT NULL, " +
                "world TEXT NOT NULL, " +
                "state TEXT NOT NULL);";

        String sqlRtreeTable = "CREATE VIRTUAL TABLE IF NOT EXISTS guild_plot_rtree USING rtree(id, x1, z1, x2, z2);";

        try (Connection conn = DatabaseManager.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sqlPlotTable);
                stmt.executeUpdate(sqlRtreeTable);
                plugin.LOGGER.info("Guild plot and R-tree table created successfully!");
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to create guild plot or R-tree table: " + e.getMessage());
        }
    }

    // 新增的插入公会庇护所区域数据的方法
    public void insertGuildShelterArea(int x1, int z1, int x2, int z2, String guildName, String world) {
        // 插入数据到 guild_plot 表
        String sqlPlot = "INSERT INTO guild_plot (owner, member, guild, world, state) VALUES (?, ?, ?, ?, ?)";
        // 插入数据到 guild_plot_rtree 表
        String sqlRtree = "INSERT INTO guild_plot_rtree(id, x1, z1, x2, z2) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            // 插入到 guild_plot 表
            try (PreparedStatement pstmt = conn.prepareStatement(sqlPlot, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, "");  // 默认空字符串或其他适当值（owner）
                pstmt.setString(2, "");  // 默认空字符串或其他适当值（member）
                pstmt.setString(3, guildName);
                pstmt.setString(4, world);
                pstmt.setString(5, "active");  // 假设状态是 active
                pstmt.executeUpdate();

                // 获取生成的 id
                ResultSet rs = pstmt.getGeneratedKeys();
                int plotId = -1;
                if (rs.next()) {
                    plotId = rs.getInt(1);  // 获取生成的 plot_id
                }

                // 插入到 guild_plot_rtree 表
                try (PreparedStatement pstmtRtree = conn.prepareStatement(sqlRtree)) {
                    pstmtRtree.setInt(1, plotId);
                    pstmtRtree.setInt(2, x1);
                    pstmtRtree.setInt(3, z1);
                    pstmtRtree.setInt(4, x2);
                    pstmtRtree.setInt(5, z2);
                    pstmtRtree.executeUpdate();
                }

                conn.commit();
                plugin.LOGGER.info("Guild shelter area inserted for Guild: " + guildName);
            } catch (SQLException e) {
                conn.rollback();
                plugin.LOGGER.error("Failed to insert guild shelter area: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Database connection issue during insert: " + e.getMessage());
        }
    }

    // Query plots by coordinate range using R-tree
    public PlotData getPlotByRange(int x1, int z1, int x2, int z2, String world) {
        String sqlRtree = "SELECT id FROM guild_plot_rtree WHERE x1 BETWEEN ? AND ? AND z1 BETWEEN ? AND ?";
        String sqlPlot = "SELECT * FROM guild_plot WHERE id = ?";

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
                                    rsPlot.getString("world"),
                                    rsPlot.getString("guild"),
                                    rsPlot.getString("state")
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
    public void insertPlot(int x1, int z1, int x2, int z2, String owner, String member, String world, String guild, String state) {
        String sqlPlot = "INSERT INTO guild_plot (owner, member, guild, world, state) VALUES (?, ?, ?, ?, ?)";
        String sqlRtree = "INSERT INTO guild_plot_rtree(id, x1, z1, x2, z2) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);  // 启用事务

            // 插入到 guild_plot 表
            try (PreparedStatement pstmt = conn.prepareStatement(sqlPlot, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, owner);
                pstmt.setString(2, member);
                pstmt.setString(3, guild);
                pstmt.setString(4, world);
                pstmt.setString(5, state);
                pstmt.executeUpdate();

                // 获取生成的 plot_id
                ResultSet rs = pstmt.getGeneratedKeys();
                int plotId = -1;
                if (rs.next()) {
                    plotId = rs.getInt(1);  // 获取生成的 plot_id
                }

                // 插入到 guild_plot_rtree 表
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
                plugin.LOGGER.error("Failed to insert plot: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);  // 恢复自动提交
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Database connection issue during plot insert: " + e.getMessage());
        }
    }

}
