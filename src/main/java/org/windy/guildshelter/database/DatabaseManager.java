package org.windy.guildshelter.database;

import org.windy.guildshelter.plugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:" + plugin.getPlugin(plugin.class).getDataFolder() + "/database.db";
    private static Connection connection;
    private static final BlockingQueue<Connection> connectionPool = new ArrayBlockingQueue<>(10);  // 使用连接池

    // 初始化数据库连接池
    static {
        try {
            for (int i = 0; i < 10; i++) {  // 初始化连接池
                connectionPool.add(DriverManager.getConnection(DB_URL));
            }
        } catch (SQLException e) {
            plugin.LOGGER.error("Error initializing database connection pool: " + e.getMessage());
        }
    }

    // 获取数据库连接
    public static Connection getConnection() {
        try {
            Connection conn = connectionPool.take();  // 从池中取出连接
            if (conn == null || conn.isClosed()) {
                conn = DriverManager.getConnection(DB_URL);  // 如果连接为空或已关闭，则新建连接
            }
            return conn;
        } catch (InterruptedException | SQLException e) {
            plugin.LOGGER.error("Error getting database connection: " + e.getMessage());
        }
        return null;
    }

    // 归还连接
    public static void releaseConnection(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    connectionPool.put(conn);  // 归还连接池
                }
            } catch (InterruptedException e) {
                plugin.LOGGER.error("Error releasing database connection: " + e.getMessage());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // 断开数据库连接
    public static void disconnect() {
        try {
            for (Connection conn : connectionPool) {
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                }
            }
            plugin.LOGGER.info("SQLite database connection pool closed!");
        } catch (SQLException e) {
            plugin.LOGGER.error("Unable to close SQLite database connections: " + e.getMessage());
        }
    }
}
