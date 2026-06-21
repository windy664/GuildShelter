package org.windy.guildshelter.persistence;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.port.CityFlagStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** JDBC 实现的主城 flag（{@code guild_city_flag} 表，SQLite/MySQL 共用，原子 upsert）。 */
public final class JdbcCityFlagStore implements CityFlagStore {

    private final JdbcDatabase db;
    private final boolean sqlite;

    public JdbcCityFlagStore(JdbcDatabase db, boolean sqlite) {
        this.db = db;
        this.sqlite = sqlite;
    }

    @Override
    public Map<String, String> flags(GuildId guild) {
        Map<String, String> out = new LinkedHashMap<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT flag_id, value FROM guild_city_flag WHERE guild_id=?")) {
            ps.setString(1, guild.value());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("flag_id"), rs.getString("value"));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询主城 flag 失败: " + guild.value(), e);
        }
        return out;
    }

    @Override
    public void put(GuildId guild, String flagId, String value) {
        String sql = sqlite
                ? "INSERT INTO guild_city_flag(guild_id,flag_id,value) VALUES(?,?,?) "
                + "ON CONFLICT(guild_id,flag_id) DO UPDATE SET value=excluded.value"
                : "INSERT INTO guild_city_flag(guild_id,flag_id,value) VALUES(?,?,?) "
                + "ON DUPLICATE KEY UPDATE value=VALUES(value)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            ps.setString(2, flagId);
            ps.setString(3, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("保存主城 flag 失败: " + guild.value(), e);
        }
    }

    @Override
    public void remove(GuildId guild, String flagId) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM guild_city_flag WHERE guild_id=? AND flag_id=?")) {
            ps.setString(1, guild.value());
            ps.setString(2, flagId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("删除主城 flag 失败: " + guild.value(), e);
        }
    }

    @Override
    public void clear(GuildId guild) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM guild_city_flag WHERE guild_id=?")) {
            ps.setString(1, guild.value());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("清除主城 flag 失败: " + guild.value(), e);
        }
    }
}
