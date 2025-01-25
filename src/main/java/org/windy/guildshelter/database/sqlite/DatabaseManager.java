package org.windy.guildshelter.database.sqlite;

import org.windy.guildshelter.plugin;
import java.sql.Connection;
import java.sql.SQLException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:" + plugin.getPlugin(plugin.class).getDataFolder() + "/database.db";
    private static HikariDataSource dataSource;

    // 初始化连接池
    public static void init() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);  // 设置数据库URL
            config.setMaximumPoolSize(10);  // 设置最大连接数
            config.setConnectionTimeout(30000);  // 设置连接超时时间，单位毫秒
            config.setIdleTimeout(600000);  // 设置连接空闲超时时间，单位毫秒
            config.setMaxLifetime(1800000);  // 设置连接的最大生命周期，单位毫秒
            dataSource = new HikariDataSource(config);  // 创建数据源
            plugin.LOGGER.info("数据库连接池初始化成功！");
        }
    }

    // 获取数据库连接
    public static Connection getConnection() {
        try {
            if (dataSource == null) {
                plugin.LOGGER.warn("数据库连接池未初始化，正在初始化...");
                init();
            }
            return dataSource.getConnection();  // 从连接池获取连接
        } catch (SQLException e) {
            plugin.LOGGER.error("获取数据库连接失败: " + e.getMessage());
            return null;
        }
    }

    // 关闭连接池
    public static void close() {
        if (dataSource != null) {
            dataSource.close();  // 关闭连接池
            plugin.LOGGER.info("数据库连接池已关闭！");
        }
    }

    // 判断数据库连接池是否有效
    public static boolean isConnectionValid() {
        try (Connection connection = getConnection()) {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            plugin.LOGGER.error("Error checking SQLite connection validity: " + e.getMessage());
            return false;
        }
    }
}
