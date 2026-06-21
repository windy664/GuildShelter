package org.windy.guildshelter.persistence;

import org.windy.guildshelter.domain.model.CampSpawn;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.port.CampSpawnStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** JDBC 实现的营地传送点（{@code guild_spawn} 表，SQLite/MySQL 共用，原子 upsert）。 */
public final class JdbcCampSpawnStore implements CampSpawnStore {

    private final JdbcDatabase db;
    private final boolean sqlite;

    public JdbcCampSpawnStore(JdbcDatabase db, boolean sqlite) {
        this.db = db;
        this.sqlite = sqlite;
    }

    @Override
    public Optional<CampSpawn> get(GuildId guild, Type type) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT x, y, z, yaw, pitch FROM guild_spawn WHERE guild_id=? AND spawn_type=?")) {
            ps.setString(1, guild.value());
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new CampSpawn(rs.getDouble("x"), rs.getDouble("y"),
                            rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch")));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询营地传送点失败: " + guild.value(), e);
        }
        return Optional.empty();
    }

    @Override
    public void set(GuildId guild, Type type, CampSpawn s) {
        String sql = sqlite
                ? "INSERT INTO guild_spawn(guild_id,spawn_type,x,y,z,yaw,pitch) VALUES(?,?,?,?,?,?,?) "
                + "ON CONFLICT(guild_id,spawn_type) DO UPDATE SET x=excluded.x,y=excluded.y,z=excluded.z,"
                + "yaw=excluded.yaw,pitch=excluded.pitch"
                : "INSERT INTO guild_spawn(guild_id,spawn_type,x,y,z,yaw,pitch) VALUES(?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE x=VALUES(x),y=VALUES(y),z=VALUES(z),yaw=VALUES(yaw),pitch=VALUES(pitch)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            ps.setString(2, type.name());
            ps.setDouble(3, s.x());
            ps.setDouble(4, s.y());
            ps.setDouble(5, s.z());
            ps.setFloat(6, s.yaw());
            ps.setFloat(7, s.pitch());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("保存营地传送点失败: " + guild.value(), e);
        }
    }

    @Override
    public void clear(GuildId guild) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM guild_spawn WHERE guild_id=?")) {
            ps.setString(1, guild.value());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("清除营地传送点失败: " + guild.value(), e);
        }
    }
}
