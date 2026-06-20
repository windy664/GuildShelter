package org.windy.guildshelter.persistence;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.port.RoadPermitStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** JDBC 实现的限时路权（{@code road_permit} 表，SQLite/MySQL 共用，upsert 续期）。 */
public final class JdbcRoadPermitStore implements RoadPermitStore {

    private final JdbcDatabase db;
    private final boolean sqlite;

    public JdbcRoadPermitStore(JdbcDatabase db, boolean sqlite) {
        this.db = db;
        this.sqlite = sqlite;
    }

    @Override
    public void grant(GuildId guild, UUID player, long expireAtMillis) {
        String sql = sqlite
                ? "INSERT INTO road_permit(guild_id,player_uuid,expire_at) VALUES(?,?,?) "
                + "ON CONFLICT(guild_id,player_uuid) DO UPDATE SET expire_at=excluded.expire_at"
                : "INSERT INTO road_permit(guild_id,player_uuid,expire_at) VALUES(?,?,?) "
                + "ON DUPLICATE KEY UPDATE expire_at=VALUES(expire_at)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            ps.setString(2, player.toString());
            ps.setLong(3, expireAtMillis);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("授予路权失败: " + guild.value(), e);
        }
    }

    @Override
    public void revoke(GuildId guild, UUID player) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM road_permit WHERE guild_id=? AND player_uuid=?")) {
            ps.setString(1, guild.value());
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("撤销路权失败: " + guild.value(), e);
        }
    }

    @Override
    public long expireAt(GuildId guild, UUID player) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT expire_at FROM road_permit WHERE guild_id=? AND player_uuid=?")) {
            ps.setString(1, guild.value());
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询路权失败: " + guild.value(), e);
        }
    }

    @Override
    public List<Entry> loadAll() {
        List<Entry> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT guild_id,player_uuid,expire_at FROM road_permit");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    out.add(new Entry(new GuildId(rs.getString(1)),
                            UUID.fromString(rs.getString(2)), rs.getLong(3)));
                } catch (IllegalArgumentException ignored) {
                    // 坏 UUID 跳过
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("加载路权失败", e);
        }
        return out;
    }

    @Override
    public void purgeExpired(long nowMillis) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM road_permit WHERE expire_at < ?")) {
            ps.setLong(1, nowMillis);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("清理过期路权失败", e);
        }
    }
}
