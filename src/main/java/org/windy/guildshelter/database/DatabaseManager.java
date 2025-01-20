package org.windy.guildshelter.database;

import org.windy.guildshelter.plugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:" + plugin.getPlugin(plugin.class).getDataFolder() + "/database.db";
    private static Connection connection;

    // Connect to the database
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
}
