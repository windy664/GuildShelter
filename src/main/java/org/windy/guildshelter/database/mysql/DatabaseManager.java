package org.windy.guildshelter.database.mysql;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class DatabaseManager {

    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/guildshelter";
    private static final String JDBC_USER = "GuildShelter";
    private static final String JDBC_PASSWORD = "bHsw7WbnFJ3rFARd";
    private Connection connection;
    private final Logger logger = Logger.getLogger(DatabaseManager.class.getName());

    // 更新：将缓存类型更改为 Map<String, CacheEntry>
    private Map<String, CacheEntry> pointCache = new HashMap<>();
    private static final long CACHE_EXPIRY_TIME = 5 * 60 * 1000; // 5分钟缓存过期时间

    private JavaPlugin plugin;
    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        try {
            // 连接到数据库
            connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
            logger.info("Database connected successfully.");
        } catch (SQLException e) {
            logger.severe("Database connection failed: " + e.getMessage());
        }
    }

    // 判断是否连接成功
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            logger.severe("Failed to check connection status: " + e.getMessage());
            return false;
        }
    }

    // 注册数据库表
    public void registerTables() {
        String createTablePlotSQL = "CREATE TABLE IF NOT EXISTS GuildShelter_plot (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "x1 DOUBLE, " +
                "z1 DOUBLE, " +
                "x2 DOUBLE, " +
                "z2 DOUBLE, " +
                "member VARCHAR(255), " +
                "title VARCHAR(255), " +
                "guildname VARCHAR(255), " +
                "world VARCHAR(255), " +
                "flag VARCHAR(255), " +
                "message TEXT, " +
                "owner VARCHAR(255), " +
                "boundary GEOMETRY" +
                ")";
        String createTableBaseSQL = "CREATE TABLE IF NOT EXISTS GuildShelter_base (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "x1 DOUBLE, " +
                "z1 DOUBLE, " +
                "x2 DOUBLE, " +
                "z2 DOUBLE, " +
                "member VARCHAR(255), " +
                "title VARCHAR(255), " +
                "guildname VARCHAR(255), " +
                "world VARCHAR(255), " +
                "flag VARCHAR(255), " +
                "message TEXT, " +
                "owner VARCHAR(255), " +
                "boundary GEOMETRY" +
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createTablePlotSQL);
            stmt.executeUpdate(createTableBaseSQL);
            logger.info("Tables 'GuildShelter_plot' and 'GuildShelter_base' have been created or already exist.");
        } catch (SQLException e) {
            logger.severe("Failed to create tables: " + e.getMessage());
        }
    }

    // 关闭数据库连接
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Database connection closed.");
            }
        } catch (SQLException e) {
            logger.severe("Failed to close database connection: " + e.getMessage());
        }
    }

    // 判断一个点是否在 GuildShelter_plot 区域内（带缓存）并返回其他信息
    public AreaInfo isPointInPlotAreaWithCache(double x, double z) {
        String cacheKey = x + "," + z;
        long currentTime = System.currentTimeMillis();

        if (pointCache.containsKey(cacheKey)) {
            CacheEntry entry = pointCache.get(cacheKey);
            if (currentTime - entry.timestamp < CACHE_EXPIRY_TIME) {
                return entry.areaInfo;  // 返回缓存的结果
            }
        }

        // 如果缓存过期或者没有缓存，查询数据库
        AreaInfo areaInfo = isPointInPlotArea(x, z);
        pointCache.put(cacheKey, new CacheEntry(areaInfo, currentTime));  // 更新缓存
        return areaInfo;
    }

    // 查询 GuildShelter_plot 中点是否在区域内，并返回其他信息
    private AreaInfo isPointInPlotArea(double x, double z) {
        String sql = "SELECT member, title, guildname, world, flag, message, owner " +
                "FROM GuildShelter_plot WHERE ST_Within(ST_GeomFromText('POINT(? ?)'), boundary)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setDouble(1, x);
            stmt.setDouble(2, z);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String member = rs.getString("member");
                String title = rs.getString("title");
                String guildname = rs.getString("guildname");
                String world = rs.getString("world");
                String flag = rs.getString("flag");
                String message = rs.getString("message");
                String owner = rs.getString("owner");

                return new AreaInfo(true, member, title, guildname, world, flag, message, owner);
            }
        } catch (SQLException e) {
            logger.severe("Point check in GuildShelter_plot failed: " + e.getMessage());
        }

        return new AreaInfo(false, null, null, null, null, null, null, null);  // 如果没有找到，返回默认的 AreaInfo
    }

    // 缓存条目类，保存查询结果和时间戳
    private static class CacheEntry {
        AreaInfo areaInfo;
        long timestamp;

        CacheEntry(AreaInfo areaInfo, long timestamp) {
            this.areaInfo = areaInfo;
            this.timestamp = timestamp;
        }
    }

    // 插入 GuildShelter_plot 数据（包括 owner 字段）
    public boolean insertGuildShelterPlot(double x1, double z1, double x2, double z2, String member,
                                          String title, String guildname, String world, String flag,
                                          String message, String owner) {
        String sql = "INSERT INTO GuildShelter_plot (x1, z1, x2, z2, member, title, guildname, world, flag, message, owner, boundary) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ST_GeomFromText('POLYGON(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'))";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setDouble(1, x1);
            stmt.setDouble(2, z1);
            stmt.setDouble(3, x2);
            stmt.setDouble(4, z2);
            stmt.setString(5, member);
            stmt.setString(6, title);
            stmt.setString(7, guildname);
            stmt.setString(8, world);
            stmt.setString(9, flag);
            stmt.setString(10, message);
            stmt.setString(11, owner);

            // 填充边界坐标
            stmt.setDouble(12, x1); // x1, z1
            stmt.setDouble(13, z1);
            stmt.setDouble(14, x2); // x2, z1
            stmt.setDouble(15, z1);
            stmt.setDouble(16, x2); // x2, z2
            stmt.setDouble(17, z2);
            stmt.setDouble(18, x1); // x1, z2
            stmt.setDouble(19, z2);
            stmt.setDouble(20, x1); // x1, z1 (closing the polygon)
            stmt.setDouble(21, z1);

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.severe("Insert into GuildShelter_plot failed: " + e.getMessage());
        }
        return false;
    }

    // 更新 GuildShelter_plot 数据（包括 owner 字段）
    public boolean updateGuildShelterPlot(String guildname, String member, String title, String world, String flag,
                                          String message, String owner, double x1, double z1, double x2, double z2) {
        String sql = "UPDATE GuildShelter_plot SET member = ?, title = ?, guildname = ?, world = ?, flag = ?, message = ?, owner = ?, " +
                "boundary = ST_GeomFromText('POLYGON(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)') " +
                "WHERE guildname = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, member);
            stmt.setString(2, title);
            stmt.setString(3, guildname);
            stmt.setString(4, world);
            stmt.setString(5, flag);
            stmt.setString(6, message);
            stmt.setString(7, owner);

            // 填充边界坐标
            stmt.setDouble(8, x1); // x1, z1
            stmt.setDouble(9, z1);
            stmt.setDouble(10, x2); // x2, z1
            stmt.setDouble(11, z1);
            stmt.setDouble(12, x2); // x2, z2
            stmt.setDouble(13, z2);
            stmt.setDouble(14, x1); // x1, z2
            stmt.setDouble(15, z2);
            stmt.setDouble(16, x1); // x1, z1 (closing the polygon)
            stmt.setDouble(17, z1);
            stmt.setString(18, guildname);

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.severe("Update GuildShelter_plot failed: " + e.getMessage());
        }
        return false;
    }

    // 插入 GuildShelter_base 数据
    public boolean insertGuildShelterBase(double x1, double z1, double x2, double z2, String member,
                                          String title, String guildname, String world, String flag,
                                          String message, String owner) {
        String sql = "INSERT INTO GuildShelter_base (x1, z1, x2, z2, member, title, guildname, world, flag, message, owner, boundary) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ST_GeomFromText('POLYGON(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'))";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setDouble(1, x1);
            stmt.setDouble(2, z1);
            stmt.setDouble(3, x2);
            stmt.setDouble(4, z2);
            stmt.setString(5, member);
            stmt.setString(6, title);
            stmt.setString(7, guildname);
            stmt.setString(8, world);
            stmt.setString(9, flag);
            stmt.setString(10, message);
            stmt.setString(11, owner);

            // 填充边界坐标
            stmt.setDouble(12, x1); // x1, z1
            stmt.setDouble(13, z1);
            stmt.setDouble(14, x2); // x2, z1
            stmt.setDouble(15, z1);
            stmt.setDouble(16, x2); // x2, z2
            stmt.setDouble(17, z2);
            stmt.setDouble(18, x1); // x1, z2
            stmt.setDouble(19, z2);
            stmt.setDouble(20, x1); // x1, z1 (closing the polygon)
            stmt.setDouble(21, z1);

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.severe("Insert into GuildShelter_base failed: " + e.getMessage());
        }
        return false;
    }
    // 更新 GuildShelter_base 数据
    public boolean updateGuildShelterBase(String guildname, String member, String title, String world, String flag,
                                          String message, String owner, double x1, double z1, double x2, double z2) {
        String sql = "UPDATE GuildShelter_base SET member = ?, title = ?, guildname = ?, world = ?, flag = ?, message = ?, owner = ?, " +
                "boundary = ST_GeomFromText('POLYGON(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)') " +
                "WHERE guildname = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, member);
            stmt.setString(2, title);
            stmt.setString(3, guildname);
            stmt.setString(4, world);
            stmt.setString(5, flag);
            stmt.setString(6, message);
            stmt.setString(7, owner);

            // 填充边界坐标
            stmt.setDouble(8, x1); // x1, z1
            stmt.setDouble(9, z1);
            stmt.setDouble(10, x2); // x2, z1
            stmt.setDouble(11, z1);
            stmt.setDouble(12, x2); // x2, z2
            stmt.setDouble(13, z2);
            stmt.setDouble(14, x1); // x1, z2
            stmt.setDouble(15, z2);
            stmt.setDouble(16, x1); // x1, z1 (closing the polygon)
            stmt.setDouble(17, z1);
            stmt.setString(18, guildname);

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.severe("Update GuildShelter_base failed: " + e.getMessage());
        }
        return false;
    }
    // 删除 GuildShelter_base 数据
    public boolean deleteGuildShelterBase(String guildname) {
        String sql = "DELETE FROM GuildShelter_base WHERE guildname = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, guildname);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.severe("Delete from GuildShelter_base failed: " + e.getMessage());
        }
        return false;
    }
    // 判断一个点是否在 GuildShelter_base 区域内（带缓存）并返回其他信息
    public AreaInfo isPointInBaseAreaWithCache(double x, double z) {
        String cacheKey = x + "," + z;
        long currentTime = System.currentTimeMillis();

        if (pointCache.containsKey(cacheKey)) {
            CacheEntry entry = pointCache.get(cacheKey);
            if (currentTime - entry.timestamp < CACHE_EXPIRY_TIME) {
                return entry.areaInfo;  // 返回缓存的结果
            }
        }

        // 如果缓存过期或者没有缓存，查询数据库
        AreaInfo areaInfo = isPointInBaseArea(x, z);
        pointCache.put(cacheKey, new CacheEntry(areaInfo, currentTime));  // 更新缓存
        return areaInfo;
    }

    // 查询 GuildShelter_base 中点是否在区域内，并返回其他信息
    private AreaInfo isPointInBaseArea(double x, double z) {
        String sql = "SELECT member, title, guildname, world, flag, message, owner " +
                "FROM GuildShelter_base WHERE ST_Within(ST_GeomFromText('POINT(? ?)'), boundary)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setDouble(1, x);
            stmt.setDouble(2, z);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String member = rs.getString("member");
                String title = rs.getString("title");
                String guildname = rs.getString("guildname");
                String world = rs.getString("world");
                String flag = rs.getString("flag");
                String message = rs.getString("message");
                String owner = rs.getString("owner");

                return new AreaInfo(true, member, title, guildname, world, flag, message, owner);
            }
        } catch (SQLException e) {
            logger.severe("Point check in GuildShelter_base failed: " + e.getMessage());
        }

        return new AreaInfo(false, null, null, null, null, null, null, null);  // 如果没有找到，返回默认的 AreaInfo
    }



}
