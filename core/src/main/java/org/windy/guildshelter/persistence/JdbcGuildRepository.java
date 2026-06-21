package org.windy.guildshelter.persistence;

import org.windy.guildshelter.domain.layout.LayoutConfig;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.TerrainPrepMode;
import org.windy.guildshelter.domain.port.GuildRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC 实现的公会营地仓库（SQLite/MySQL 共用，upsert 语法由方言给出）。 */
public final class JdbcGuildRepository implements GuildRepository {

    private final JdbcDatabase db;
    private final SqlDialect dialect;
    private final LayoutConfig fallbackLayout; // 老库 layout_params 为 NULL 时回退

    public JdbcGuildRepository(JdbcDatabase db, SqlDialect dialect, LayoutConfig fallbackLayout) {
        this.db = db;
        this.dialect = dialect;
        this.fallbackLayout = fallbackLayout;
    }

    @Override
    public Optional<GuildWorld> find(GuildId guild) {
        String sql = "SELECT world_name, seed, origin_x, origin_z, guild_level, allocated_slots, layout_params, funds, bulletin, terrain_mode, server_name, city_unlocked, city_quota "
                + "FROM guild_world WHERE guild_id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(read(guild, rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询公会营地失败: " + guild.value(), e);
        }
    }

    @Override
    public boolean exists(GuildId guild) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM guild_world WHERE guild_id=?")) {
            ps.setString(1, guild.value());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询公会营地存在性失败: " + guild.value(), e);
        }
    }

    @Override
    public void save(GuildWorld world) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(dialect.upsertGuildWorld())) {
            ps.setString(1, world.guild().value());
            ps.setString(2, world.worldName());
            ps.setLong(3, world.seed());
            ps.setInt(4, world.originChunkX());
            ps.setInt(5, world.originChunkZ());
            ps.setInt(6, world.guildLevel());
            ps.setInt(7, world.allocatedSlots());
            ps.setString(8, LayoutCsv.toCsv(world.layout()));
            ps.setDouble(9, world.funds());
            ps.setString(10, world.bulletin());
            ps.setString(11, world.terrainMode().name());
            ps.setString(12, world.serverName());
            ps.setString(13, UnlockedCsv.toCsv(world.cityUnlockedChunks()));
            ps.setInt(14, world.cityQuotaOverride());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("保存公会营地失败: " + world.guild().value(), e);
        }
    }

    @Override
    public void delete(GuildId guild) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM guild_world WHERE guild_id=?")) {
            ps.setString(1, guild.value());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("删除公会营地失败: " + guild.value(), e);
        }
    }

    @Override
    public List<GuildWorld> findAll() {
        String sql = "SELECT guild_id, world_name, seed, origin_x, origin_z, guild_level, allocated_slots, layout_params, funds, bulletin, terrain_mode, server_name, city_unlocked, city_quota "
                + "FROM guild_world";
        List<GuildWorld> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(read(new GuildId(rs.getString("guild_id")), rs));
            }
        } catch (SQLException e) {
            throw new PersistenceException("列举公会营地失败", e);
        }
        return out;
    }

    private GuildWorld read(GuildId guild, ResultSet rs) throws SQLException {
        String modeStr = rs.getString("terrain_mode");
        TerrainPrepMode mode;
        try {
            mode = modeStr != null ? TerrainPrepMode.valueOf(modeStr) : TerrainPrepMode.CLEAR_VEGETATION;
        } catch (IllegalArgumentException e) {
            mode = TerrainPrepMode.CLEAR_VEGETATION;
        }
        String serverName = rs.getString("server_name");
        int cityQuota = rs.getObject("city_quota") == null ? -1 : rs.getInt("city_quota"); // 旧行无列→-1(按等级)
        return new GuildWorld(
                guild,
                rs.getString("world_name"),
                rs.getLong("seed"),
                rs.getInt("origin_x"),
                rs.getInt("origin_z"),
                rs.getInt("guild_level"),
                rs.getInt("allocated_slots"),
                LayoutCsv.parse(rs.getString("layout_params"), fallbackLayout),
                rs.getDouble("funds"),
                rs.getString("bulletin"),
                mode,
                serverName != null ? serverName : "",
                UnlockedCsv.parse(rs.getString("city_unlocked")),
                cityQuota);
    }
}
