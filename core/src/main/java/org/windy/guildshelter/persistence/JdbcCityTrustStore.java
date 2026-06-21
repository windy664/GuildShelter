package org.windy.guildshelter.persistence;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.port.CityTrustStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** JDBC 实现的主城信任名单（{@code guild_city_trust} 表，SQLite/MySQL 共用，INSERT OR IGNORE/幂等）。 */
public final class JdbcCityTrustStore implements CityTrustStore {

    private final JdbcDatabase db;

    public JdbcCityTrustStore(JdbcDatabase db) {
        this.db = db;
    }

    @Override
    public Set<UUID> trusted(GuildId guild) {
        Set<UUID> out = new HashSet<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT player_uuid FROM guild_city_trust WHERE guild_id=?")) {
            ps.setString(1, guild.value());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        out.add(UUID.fromString(rs.getString(1)));
                    } catch (IllegalArgumentException ignored) {
                        // 坏数据跳过
                    }
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询主城信任失败: " + guild.value(), e);
        }
        return out;
    }

    @Override
    public void add(GuildId guild, UUID player) {
        // 两后端皆支持 INSERT ... ON CONFLICT DO NOTHING（SQLite）/ INSERT IGNORE（MySQL）；
        // 这里用通用的"存在则跳过"：先删后插过于重，直接靠主键冲突吞掉。
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO guild_city_trust(guild_id, player_uuid) VALUES(?,?)")) {
            ps.setString(1, guild.value());
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            // 主键冲突（已存在）= 幂等成功，吞掉；其它错误抛出。
            if (!isDuplicate(e)) {
                throw new PersistenceException("添加主城信任失败: " + guild.value(), e);
            }
        }
    }

    @Override
    public void remove(GuildId guild, UUID player) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM guild_city_trust WHERE guild_id=? AND player_uuid=?")) {
            ps.setString(1, guild.value());
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("撤销主城信任失败: " + guild.value(), e);
        }
    }

    @Override
    public void clear(GuildId guild) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM guild_city_trust WHERE guild_id=?")) {
            ps.setString(1, guild.value());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("清除主城信任失败: " + guild.value(), e);
        }
    }

    private static boolean isDuplicate(SQLException e) {
        String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return m.contains("unique") || m.contains("duplicate") || m.contains("primary key") || m.contains("constraint");
    }
}
