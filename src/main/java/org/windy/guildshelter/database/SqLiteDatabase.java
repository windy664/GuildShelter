package org.windy.guildshelter.database;

import org.windy.guildshelter.plugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.windy.guildshelter.util.GenerateGuildBase.sqLiteDatabase;

public class SqLiteDatabase {

    private static final String DB_URL = "jdbc:sqlite:" + plugin.getPlugin(plugin.class).getDataFolder() + "/database.db";
    private static Connection connection;

    // Connect to the database
    // 连接数据库
    public static void connect() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL);
                plugin.LOGGER.info("SQLite database connection successful!");
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Unable to connect to SQLite database: " + e.getMessage());
        }
    }


    // 断开数据库连接
    public static void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.LOGGER.info("SQLite database connection closed!");
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Unable to close SQLite database connection: " + e.getMessage());
        }
    }

    // Create the plot table
    public void createPlotTable() {
        connect();  // Ensure connection to the database
        String sql = "CREATE TABLE IF NOT EXISTS guild_plot (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "x1 INTEGER NOT NULL, " +
                "z1 INTEGER NOT NULL, " +
                "x2 INTEGER NOT NULL, " +
                "z2 INTEGER NOT NULL, " +
                "owner TEXT NOT NULL, " +
                "member TEXT NOT NULL, " +
                "levels TEXT NOT NULL, " +
                "guild TEXT NOT NULL, " +
                "state TEXT NOT NULL);";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            plugin.LOGGER.info("Guild plot table created successfully!");
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to create guild plot table: " + e.getMessage());
        }
    }


    // Insert plot data
    public void insertPlot(int x1, int z1, int x2, int z2, String owner, String member, String levels, String guild, String state) {
        connect();  // Ensure connection to the database
        String sql = "INSERT INTO guild_plot (x1, z1, x2, z2, owner, member, levels, guild, state) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, x1);
            pstmt.setInt(2, z1);
            pstmt.setInt(3, x2);
            pstmt.setInt(4, z2);
            pstmt.setString(5, owner);
            pstmt.setString(6, member);
            pstmt.setString(7, levels);
            pstmt.setString(8, guild);
            pstmt.setString(9, state);
            pstmt.executeUpdate();
            plugin.LOGGER.info("Plot data inserted: (" + x1 + ", " + z1 + ") to (" + x2 + ", " + z2 + ") Guild: " + guild + " Owner: " + owner);
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to insert plot data: " + e.getMessage());
        }
    }



    // Get the database connection
    // 获取数据库连接
    public static Connection getConnection() {
        if (connection == null) {
            connect();  // 如果连接为空，则尝试连接数据库
        }
        return connection;
    }
    public List<PlotData> getPlotsByGuild(String guildName) {
        connect();  // Ensure connection to the database
        List<PlotData> plots = new ArrayList<>();
        String sql = "SELECT * FROM guild_plot WHERE guild = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, guildName);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                plots.add(new PlotData(
                        rs.getInt("x1"),
                        rs.getInt("z1"),
                        rs.getInt("x2"),
                        rs.getInt("z2"),
                        rs.getString("owner"),
                        rs.getString("member"),
                        rs.getString("levels"),
                        rs.getString("guild"),
                        rs.getString("state")
                ));
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to query plot data by guild: " + e.getMessage());
        }
        return plots;
    }
    public List<PlotData> getPlotsByMember(String memberName) {
        connect();  // Ensure connection to the database
        List<PlotData> plots = new ArrayList<>();
        String sql = "SELECT * FROM guild_plot WHERE member LIKE ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, "%" + memberName + "%");
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                plots.add(new PlotData(
                        rs.getInt("x1"),
                        rs.getInt("z1"),
                        rs.getInt("x2"),
                        rs.getInt("z2"),
                        rs.getString("owner"),
                        rs.getString("member"),
                        rs.getString("levels"),
                        rs.getString("guild"),
                        rs.getString("state")
                ));
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to query plot data by member: " + e.getMessage());
        }
        return plots;
    }
    public List<PlotData> getPlotsByOwner(String ownerName) {
        connect();  // Ensure connection to the database
        List<PlotData> plots = new ArrayList<>();
        String sql = "SELECT * FROM guild_plot WHERE owner = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ownerName);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                plots.add(new PlotData(
                        rs.getInt("x1"),
                        rs.getInt("z1"),
                        rs.getInt("x2"),
                        rs.getInt("z2"),
                        rs.getString("owner"),
                        rs.getString("member"),
                        rs.getString("levels"),
                        rs.getString("guild"),
                        rs.getString("state")
                ));
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to query plot data by owner: " + e.getMessage());
        }
        return plots;
    }
    public void updateMember(int plotId, String newMember) {
        connect();  // Ensure connection to the database
        String sql = "UPDATE guild_plot SET member = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, newMember);
            pstmt.setInt(2, plotId);
            pstmt.executeUpdate();
            plugin.LOGGER.info("Member updated for plot ID: " + plotId);
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to update member: " + e.getMessage());
        }
    }
    public void removeMember(int plotId, String memberToRemove) {
        connect();  // Ensure connection to the database
        String sql = "UPDATE guild_plot SET member = REPLACE(member, ?, '') WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, memberToRemove);
            pstmt.setInt(2, plotId);
            pstmt.executeUpdate();
            plugin.LOGGER.info("Member removed from plot ID: " + plotId);
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to remove member: " + e.getMessage());
        }
    }

}
