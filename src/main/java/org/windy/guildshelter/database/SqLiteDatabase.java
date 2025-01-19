package org.windy.guildshelter.database;

import org.windy.guildshelter.plugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.windy.guildshelter.util.GenerateGuildBase.sqLiteDatabase;

public class SqLiteDatabase {

    private static final String DB_URL = "jdbc:sqlite:" + plugin.getPlugin(plugin.class).getDataFolder() + "/database.db";
    private Connection connection;

    // Connect to the database
    public void connect() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL);
                plugin.LOGGER.info("SQLite database connection successful!");
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Unable to connect to SQLite database: " + e.getMessage());
        }
    }

    // Disconnect from the database
    public void disconnect() {
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
        String sql = "CREATE TABLE IF NOT EXISTS plot (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "x INTEGER NOT NULL, " +
                "y INTEGER NOT NULL, " +
                "player TEXT NOT NULL, " +
                "truster TEXT NOT NULL, " +
                "guild TEXT NOT NULL);";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            plugin.LOGGER.info("Plot table created successfully!");
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to create plot table: " + e.getMessage());
        }
    }

    // Insert plot data
    public void insertPlot(int x, int y, String player, String truster, String guild) {
        connect();  // Ensure connection to the database
        String sql = "INSERT INTO plot (x, y, player, truster, guild) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, x);
            pstmt.setInt(2, y);
            pstmt.setString(3, player);
            pstmt.setString(4, truster);
            pstmt.setString(5, guild);  // Corrected parameter binding
            pstmt.executeUpdate();
            plugin.LOGGER.info("Plot data inserted: (" + x + ", " + y + ") Player: " + player + " Guild: " + guild);
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to insert plot data: " + e.getMessage());
        }
    }

    // Get all plots by a specific player
    public List<PlotData> getPlotsByPlayer(String playerName) {
        connect();  // Ensure connection to the database
        List<PlotData> plots = new ArrayList<>();
        String sql = "SELECT * FROM plot WHERE player = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                // Extract data and add to the return list
                plots.add(new PlotData(
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getString("player"),
                        rs.getString("truster"),
                        rs.getString("guild")
                ));
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to query plot data: " + e.getMessage());
        }
        return plots;  // Return all plots of the specified player
    }

    // Update the truster for a specific plot
    public void updateTruster(int x, int y, String truster) {
        connect();  // Ensure connection to the database
        String sql = "UPDATE plot SET truster = ? WHERE x = ? AND y = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, truster);
            pstmt.setInt(2, x);
            pstmt.setInt(3, y);
            pstmt.executeUpdate();
            plugin.LOGGER.info("Plot data updated: (" + x + ", " + y + ") Set truster to: " + truster);
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to update plot data: " + e.getMessage());
        }
    }

    // Get plot data by coordinates
    public PlotData getPlotByCoordinates(int plotX, int plotY) {
        connect();  // Ensure connection to the database
        String sql = "SELECT * FROM plot WHERE x = ? AND y = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, plotX);
            pstmt.setInt(2, plotY);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // Extract data and return a PlotData object
                return new PlotData(
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getString("player"),
                        rs.getString("truster"),
                        rs.getString("guild")
                );
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to query plot data: " + e.getMessage());
        }
        return null;  // Return null if no data is found
    }

    // Get the database connection
    public Connection getConnection() {
        return connection;  // Return the database connection
    }
}
