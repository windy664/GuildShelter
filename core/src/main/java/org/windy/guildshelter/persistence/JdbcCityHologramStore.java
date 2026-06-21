package org.windy.guildshelter.persistence;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.port.CityHologramStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** JDBC 实现的主城悬浮字归属（{@code guild_hologram} 表，SQLite/MySQL 共用）。 */
public final class JdbcCityHologramStore implements CityHologramStore {

    private final JdbcDatabase db;

    public JdbcCityHologramStore(JdbcDatabase db) {
        this.db = db;
    }

    @Override
    public List<HoloRecord> list(GuildId guild) {
        List<HoloRecord> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name, label FROM guild_hologram WHERE guild_id=? ORDER BY id")) {
            ps.setString(1, guild.value());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new HoloRecord(rs.getString("name"), rs.getString("label")));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询主城悬浮字失败: " + guild.value(), e);
        }
        return out;
    }

    @Override
    public void add(GuildId guild, String name, String label) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO guild_hologram(guild_id, name, label) VALUES(?,?,?)")) {
            ps.setString(1, guild.value());
            ps.setString(2, name);
            ps.setString(3, label);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("新增主城悬浮字失败: " + guild.value(), e);
        }
    }

    @Override
    public void updateLabel(GuildId guild, String name, String label) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE guild_hologram SET label=? WHERE guild_id=? AND name=?")) {
            ps.setString(1, label);
            ps.setString(2, guild.value());
            ps.setString(3, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("更新主城悬浮字失败: " + guild.value(), e);
        }
    }

    @Override
    public void remove(GuildId guild, String name) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM guild_hologram WHERE guild_id=? AND name=?")) {
            ps.setString(1, guild.value());
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("删除主城悬浮字失败: " + guild.value(), e);
        }
    }

    @Override
    public void clear(GuildId guild) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM guild_hologram WHERE guild_id=?")) {
            ps.setString(1, guild.value());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("清除主城悬浮字失败: " + guild.value(), e);
        }
    }
}
