package org.windy.guildshelter.database;

import org.windy.guildshelter.plugin;
import java.sql.*;

public class GuildShelterAreaTable {

    // Create the guild shelter area table
    public void createGuildShelterArea() {
        String sql = "CREATE TABLE IF NOT EXISTS guild_shelter_area (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "x1 INTEGER NOT NULL, " +
                "z1 INTEGER NOT NULL, " +
                "x2 INTEGER NOT NULL, " +
                "z2 INTEGER NOT NULL, " +
                "guild TEXT NOT NULL, " +
                "world TEXT NOT NULL);";
        try (Connection conn = DatabaseManager.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql);
                plugin.LOGGER.info("Guild shelter area table created successfully!");
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to create guild shelter area table: " + e.getMessage());
        }
    }

    // Insert guild shelter area
    public void insertGuildShelterArea(int x1, int z1, int x2, int z2, String guild, String world) {
        String sql = "INSERT INTO guild_shelter_area (x1, z1, x2, z2, guild, world) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, x1);
                pstmt.setInt(2, z1);
                pstmt.setInt(3, x2);
                pstmt.setInt(4, z2);
                pstmt.setString(5, guild);
                pstmt.setString(6, world);
                pstmt.executeUpdate();
                plugin.LOGGER.info("Guild shelter area inserted for Guild: " + guild);
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Failed to insert guild shelter area: " + e.getMessage());
        }
    }
}
