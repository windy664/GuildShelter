package org.windy.guildshelter.database.sqlite;

import org.windy.guildshelter.database.sqlite.DatabaseManager;
import org.windy.guildshelter.database.sqlite.GuildRegionData;
import org.windy.guildshelter.plugin;
import java.sql.*;

public class GuildRegionTable {

    // 创建公会区域表和 R-tree 表
    public void createGuildRegionTable() {
        String sqlRegionTable = "CREATE TABLE IF NOT EXISTS guild_region (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "x1 INTEGER NOT NULL, " +
                "z1 INTEGER NOT NULL, " +
                "x2 INTEGER NOT NULL, " +
                "z2 INTEGER NOT NULL, " +
                "world TEXT NOT NULL, " +
                "guild TEXT NOT NULL);";

        String sqlRtreeTable = "CREATE VIRTUAL TABLE IF NOT EXISTS guild_region_rtree USING rtree(id, x1, z1, x2, z2);";

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sqlRegionTable);
            stmt.executeUpdate(sqlRtreeTable);
            plugin.LOGGER.info("公会区域表和 R-tree 表创建成功！");
        } catch (SQLException e) {
            plugin.LOGGER.error("创建公会区域或 R-tree 表失败: " + e.getMessage());
        }
    }

    // 插入公会区域数据
    public void insertGuildRegion(int x1, int z1, int x2, int z2, String guild, String world) {
        String sqlRegion = "INSERT INTO guild_region (x1, z1, x2, z2, guild, world) VALUES (?, ?, ?, ?, ?, ?)";
        String sqlRtree = "INSERT INTO guild_region_rtree(id, x1, z1, x2, z2) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);  // 启用事务

            // 插入到 guild_region 表
            try (PreparedStatement pstmt = conn.prepareStatement(sqlRegion, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, x1);
                pstmt.setInt(2, z1);
                pstmt.setInt(3, x2);
                pstmt.setInt(4, z2);
                pstmt.setString(5, guild);
                pstmt.setString(6, world);
                pstmt.executeUpdate();

                // 获取生成的 id
                ResultSet rs = pstmt.getGeneratedKeys();
                int regionId = -1;
                if (rs.next()) {
                    regionId = rs.getInt(1);  // 获取生成的 region_id
                }

                // 插入到 guild_region_rtree 表
                if (regionId != -1) {
                    try (PreparedStatement pstmtRtree = conn.prepareStatement(sqlRtree)) {
                        pstmtRtree.setInt(1, regionId);
                        pstmtRtree.setInt(2, x1);
                        pstmtRtree.setInt(3, z1);
                        pstmtRtree.setInt(4, x2);
                        pstmtRtree.setInt(5, z2);
                        pstmtRtree.executeUpdate();
                    }
                }

                conn.commit();  // 提交事务
                plugin.LOGGER.info("公会《" + guild + "》的区域已插入！");
            } catch (SQLException e) {
                conn.rollback();  // 回滚事务
                plugin.LOGGER.error("插入公会区域失败: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);  // 恢复自动提交
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("数据库连接问题，插入公会区域失败: " + e.getMessage());
        }
    }

    // 根据坐标范围查询公会区域
    public GuildRegionData getRegionByRange(int x1, int z1, int x2, int z2, String world) {
        String sqlRtree = "SELECT id FROM guild_region_rtree WHERE x1 BETWEEN ? AND ? AND z1 BETWEEN ? AND ?";
        String sqlRegion = "SELECT * FROM guild_region WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement pstmtRtree = conn.prepareStatement(sqlRtree)) {
                pstmtRtree.setInt(1, x1);
                pstmtRtree.setInt(2, x2);
                pstmtRtree.setInt(3, z1);
                pstmtRtree.setInt(4, z2);
                ResultSet rsRtree = pstmtRtree.executeQuery();

                while (rsRtree.next()) {
                    int regionId = rsRtree.getInt("id");

                    try (PreparedStatement pstmtRegion = conn.prepareStatement(sqlRegion)) {
                        pstmtRegion.setInt(1, regionId);
                        ResultSet rsRegion = pstmtRegion.executeQuery();

                        if (rsRegion.next()) {
                            return new GuildRegionData(
                                    regionId,
                                    rsRegion.getInt("x1"),
                                    rsRegion.getInt("z1"),
                                    rsRegion.getInt("x2"),
                                    rsRegion.getInt("z2"),
                                    rsRegion.getString("guild"),
                                    rsRegion.getString("world")
                            );
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("查询公会区域数据失败: " + e.getMessage());
        }

        return null;
    }

    // 判断某个点是否在公会区域内
// 更新后的 isInRegion 方法
// 更新后的 isInRegion 方法
    public boolean isInRegion(int x, int z, String world) {
        String sql = "SELECT id FROM guild_region WHERE world = ? AND x1 <= ? AND x2 >= ? AND z1 <= ? AND z2 >= ?";

        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, world);
                pstmt.setInt(2, x);
                pstmt.setInt(3, x);
                pstmt.setInt(4, z);
                pstmt.setInt(5, z);
                ResultSet rs = pstmt.executeQuery();

                return rs.next();  // 如果有结果，说明该点在公会区域内
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("查询公会区域内点是否存在失败: " + e.getMessage());
        }

        return false;  // 如果没有结果，说明该点不在任何公会区域内
    }

}
