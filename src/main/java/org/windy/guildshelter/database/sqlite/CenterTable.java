package org.windy.guildshelter.database.sqlite;

import org.windy.guildshelter.plugin;
import java.sql.*;

public class CenterTable {

    // 创建 center 表和 R-tree 表
    public void createCenterTable() {
        String sqlCenterTable = "CREATE TABLE IF NOT EXISTS guildshelter_center (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "member TEXT NOT NULL, " +
                "title TEXT NOT NULL, " +
                "guild TEXT NOT NULL, " +
                "world TEXT NOT NULL, " +
                "flag TEXT);";

        String sqlRtreeTable = "CREATE VIRTUAL TABLE IF NOT EXISTS guildshelter_center_tree USING rtree(id, x1, z1, x2, z2);";

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sqlCenterTable);
            stmt.executeUpdate(sqlRtreeTable);
            plugin.LOGGER.info("公会庇护中心表和 R-tree 表创建成功！");
        } catch (SQLException e) {
            plugin.LOGGER.error("创建公会庇护中心或 R-tree 表失败: " + e.getMessage());
        }
    }

    // 插入 center 区域数据
    public void insertCenter(int x1, int z1, int x2, int z2, String member, String title, String guild, String world, String flag) {
        String sqlCenter = "INSERT INTO guildshelter_center (member, title, guild, world, flag) VALUES (?, ?, ?, ?, ?)";
        String sqlRtree = "INSERT INTO guildshelter_center_tree(id, x1, z1, x2, z2) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            // 插入到 guildshelter_center 表
            try (PreparedStatement pstmt = conn.prepareStatement(sqlCenter, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, member);
                pstmt.setString(2, title);
                pstmt.setString(3, guild);
                pstmt.setString(4, world);
                pstmt.setString(5, flag);
                pstmt.executeUpdate();

                // 获取生成的 id
                ResultSet rs = pstmt.getGeneratedKeys();
                int centerId = -1;
                if (rs.next()) {
                    centerId = rs.getInt(1);  // 获取生成的 center_id
                }

                // 插入到 guildshelter_center_tree 表
                try (PreparedStatement pstmtRtree = conn.prepareStatement(sqlRtree)) {
                    pstmtRtree.setInt(1, centerId);
                    pstmtRtree.setInt(2, x1);
                    pstmtRtree.setInt(3, z1);
                    pstmtRtree.setInt(4, x2);
                    pstmtRtree.setInt(5, z2);
                    pstmtRtree.executeUpdate();
                }

                conn.commit();
                plugin.LOGGER.info("公会《" + guild + "》的庇护中心已插入！");
            } catch (SQLException e) {
                conn.rollback();
                plugin.LOGGER.error("插入庇护中心失败: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("数据库连接问题，插入庇护中心失败: " + e.getMessage());
        }
    }

    // 根据坐标范围查询 Center
    public CenterData getCenterByRange(int x1, int z1, int x2, int z2, String world) {
        String sqlRtree = "SELECT id FROM guildshelter_center_tree WHERE x1 BETWEEN ? AND ? AND z1 BETWEEN ? AND ?";
        String sqlCenter = "SELECT * FROM guildshelter_center WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement pstmtRtree = conn.prepareStatement(sqlRtree)) {
                pstmtRtree.setInt(1, x1);
                pstmtRtree.setInt(2, x2);
                pstmtRtree.setInt(3, z1);
                pstmtRtree.setInt(4, z2);
                ResultSet rsRtree = pstmtRtree.executeQuery();

                while (rsRtree.next()) {
                    int centerId = rsRtree.getInt("id");

                    try (PreparedStatement pstmtCenter = conn.prepareStatement(sqlCenter)) {
                        pstmtCenter.setInt(1, centerId);
                        ResultSet rsCenter = pstmtCenter.executeQuery();

                        if (rsCenter.next()) {
                            return new CenterData(
                                    centerId,
                                    x1, z1, x2, z2,
                                    rsCenter.getString("member"),
                                    rsCenter.getString("title"),
                                    rsCenter.getString("guild"),
                                    rsCenter.getString("world"),
                                    rsCenter.getString("flag")
                            );
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("查询庇护中心数据失败: " + e.getMessage());
        }

        return null;
    }
}
