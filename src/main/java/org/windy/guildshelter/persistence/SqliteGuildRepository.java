package org.windy.guildshelter.persistence;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.port.GuildRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class SqliteGuildRepository implements GuildRepository {

    private final SqliteDatabase db;

    public SqliteGuildRepository(SqliteDatabase db) {
        this.db = db;
    }

    @Override
    public Optional<GuildWorld> find(GuildId guild) {
        String sql = "SELECT world_name, seed, origin_x, origin_z, guild_level, allocated_slots "
                + "FROM guild_world WHERE guild_id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new GuildWorld(
                            guild,
                            rs.getString("world_name"),
                            rs.getLong("seed"),
                            rs.getInt("origin_x"),
                            rs.getInt("origin_z"),
                            rs.getInt("guild_level"),
                            rs.getInt("allocated_slots")));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询公会世界失败: " + guild.value(), e);
        }
    }

    @Override
    public boolean exists(GuildId guild) {
        String sql = "SELECT 1 FROM guild_world WHERE guild_id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询公会世界存在性失败: " + guild.value(), e);
        }
    }

    @Override
    public void save(GuildWorld world) {
        String sql = """
                INSERT INTO guild_world(guild_id, world_name, seed, origin_x, origin_z, guild_level, allocated_slots)
                VALUES(?,?,?,?,?,?,?)
                ON CONFLICT(guild_id) DO UPDATE SET
                    world_name=excluded.world_name,
                    seed=excluded.seed,
                    origin_x=excluded.origin_x,
                    origin_z=excluded.origin_z,
                    guild_level=excluded.guild_level,
                    allocated_slots=excluded.allocated_slots""";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, world.guild().value());
            ps.setString(2, world.worldName());
            ps.setLong(3, world.seed());
            ps.setInt(4, world.originChunkX());
            ps.setInt(5, world.originChunkZ());
            ps.setInt(6, world.guildLevel());
            ps.setInt(7, world.allocatedSlots());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("保存公会世界失败: " + world.guild().value(), e);
        }
    }

    @Override
    public void delete(GuildId guild) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM guild_world WHERE guild_id=?")) {
            ps.setString(1, guild.value());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("删除公会世界失败: " + guild.value(), e);
        }
    }

    @Override
    public java.util.List<GuildWorld> findAll() {
        String sql = "SELECT guild_id, world_name, seed, origin_x, origin_z, guild_level, allocated_slots "
                + "FROM guild_world";
        java.util.List<GuildWorld> out = new java.util.ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new GuildWorld(
                        new GuildId(rs.getString("guild_id")),
                        rs.getString("world_name"),
                        rs.getLong("seed"),
                        rs.getInt("origin_x"),
                        rs.getInt("origin_z"),
                        rs.getInt("guild_level"),
                        rs.getInt("allocated_slots")));
            }
        } catch (SQLException e) {
            throw new PersistenceException("列举公会世界失败", e);
        }
        return out;
    }
}
