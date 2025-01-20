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
    // 获取数据库连接
    public static Connection getConnection() {
        if (connection == null) {
            connect();  // 如果连接为空，则尝试连接数据库
        }
        return connection;
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
                "world TEXT NOT NULL, " +  // 将 levels 修改为 world
                "guild TEXT NOT NULL, " +
                "state TEXT NOT NULL);";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            plugin.LOGGER.info("Guild plot table created successfully!");
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to create guild plot table: " + e.getMessage());
        }
    }


    public void createGuildShelterArea() {
        connect();  // Ensure connection to the database
        String sql = "CREATE TABLE IF NOT EXISTS guild_shelter_area (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "x1 INTEGER NOT NULL, " +
                "z1 INTEGER NOT NULL, " +
                "x2 INTEGER NOT NULL, " +
                "z2 INTEGER NOT NULL, " +
                "guild TEXT NOT NULL, " +
                "world TEXT NOT NULL);";  // 确保使用 world 列
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            plugin.LOGGER.info("Guild shelter area table created successfully!");
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to create guild shelter area table: " + e.getMessage());
        }
    }


    public void insertGuildShelterArea(int x1, int z1, int x2, int z2, String guild, String world) {
        connect();  // Ensure connection to the database
        String sql = "INSERT INTO guild_shelter_area (x1, z1, x2, z2, guild, world) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, x1);
            pstmt.setInt(2, z1);
            pstmt.setInt(3, x2);
            pstmt.setInt(4, z2);
            pstmt.setString(5, guild);
            pstmt.setString(6, world);  // 插入 world 值
            pstmt.executeUpdate();
            plugin.LOGGER.info("Guild shelter area inserted: (" + x1 + ", " + z1 + ") to (" + x2 + ", " + z2 + ") Guild: " + guild + " World: " + world);
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to insert guild shelter area: " + e.getMessage());
        }
    }


    // Insert plot data
    public void insertPlot(int x1, int z1, int x2, int z2, String owner, String member, String world, String guild, String state) {
        connect();  // Ensure connection to the database
        String sql = "INSERT INTO guild_plot (x1, z1, x2, z2, owner, member, world, guild, state) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, x1);
            pstmt.setInt(2, z1);
            pstmt.setInt(3, x2);
            pstmt.setInt(4, z2);
            pstmt.setString(5, owner);
            pstmt.setString(6, member);
            pstmt.setString(7, world);  // 使用 world 而不是 levels
            pstmt.setString(8, guild);
            pstmt.setString(9, state);
            pstmt.executeUpdate();
            plugin.LOGGER.info("Plot data inserted: (" + x1 + ", " + z1 + ") to (" + x2 + ", " + z2 + ") Guild: " + guild + " Owner: " + owner);
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to insert plot data: " + e.getMessage());
        }
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
                        rs.getString("world"),  // 使用 world 而不是 levels
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
                        rs.getString("world"),
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
                        rs.getString("world"),
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
    public void removeGuildArea(String guildName) {
        connect();  // Ensure connection to the database
        String sql = "DELETE FROM guild_plot WHERE guild =?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, guildName);
            pstmt.executeUpdate();
            plugin.LOGGER.info("Guild area removed for guild: " + guildName);
        }catch (SQLException e) {
            plugin.LOGGER.error("Failed to remove guild area: " + e.getMessage());
        }
    }
    public boolean isConflictWithExistingArea(int x1, int z1, int x2, int z2, String world) {
        connect();  // Ensure connection to the database
        String sql = "SELECT * FROM guild_shelter_area WHERE world = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, world);  // 使用正确的 world 列名
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                int dbX1 = rs.getInt("x1");
                int dbZ1 = rs.getInt("z1");
                int dbX2 = rs.getInt("x2");
                int dbZ2 = rs.getInt("z2");

                // Check for overlap between the input range and the database range
                if (isOverlap(x1, z1, x2, z2, dbX1, dbZ1, dbX2, dbZ2)) {
                    return true; // Conflict found
                }
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to check for conflicts in guild shelter area: " + e.getMessage());
        }
        return false; // No conflict found
    }


    private boolean isOverlap(int x1, int z1, int x2, int z2, int dbX1, int dbZ1, int dbX2, int dbZ2) {
        // Check if the two areas overlap
        return !(x2 < dbX1 || x1 > dbX2 || z2 < dbZ1 || z1 > dbZ2);
    }

}
