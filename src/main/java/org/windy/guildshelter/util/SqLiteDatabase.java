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

    // 创建表格
    public void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS guilds (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "guild_name TEXT NOT NULL, " +
                "leader_name TEXT NOT NULL);";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            plugin.LOGGER.info("数据库表格已创建！");
        } catch (SQLException e) {
            plugin.LOGGER.error("无法创建表格: " + e.getMessage());
        }
    }

    // 插入数据
    public void insertGuild(String guildName, String leaderName) {
        String sql = "INSERT INTO guilds(guild_name, leader_name) VALUES(?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, guildName);
            pstmt.setString(2, leaderName);
            pstmt.executeUpdate();
            plugin.LOGGER.info("公会数据已插入: " + guildName);
        } catch (SQLException e) {
            plugin.LOGGER.error("插入公会数据失败: " + e.getMessage());
        }
    }

    // 查询数据
    public String selectGuilds() {
        StringBuilder result = new StringBuilder();
        String sql = "SELECT * FROM guilds";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String guildName = rs.getString("guild_name");
                String leaderName = rs.getString("leader_name");
                result.append("公会: ").append(guildName).append(", 领袖: ").append(leaderName).append("\n");
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("查询公会数据失败: " + e.getMessage());
        }
        return result.toString();
    }

    // 删除公会数据
    public void deleteGuild(String guildName) {
        String sql = "DELETE FROM guilds WHERE guild_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, guildName);
            pstmt.executeUpdate();
            plugin.LOGGER.info("已删除公会: " + guildName);
        } catch (SQLException e) {
            plugin.LOGGER.error("删除公会数据失败: " + e.getMessage());
        }
    }

    // 更新公会数据
    public void updateGuildLeader(String guildName, String newLeader) {
        String sql = "UPDATE guilds SET leader_name = ? WHERE guild_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, newLeader);
            pstmt.setString(2, guildName);
            pstmt.executeUpdate();
            plugin.LOGGER.info("公会 " + guildName + " 的领袖已更新为: " + newLeader);
        } catch (SQLException e) {
            plugin.LOGGER.error("更新公会领袖失败: " + e.getMessage());
        }
    }
}
