package org.windy.guildshelter.persistence;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.ManorRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SqliteManorRepository implements ManorRepository {

    private final SqliteDatabase db;

    public SqliteManorRepository(SqliteDatabase db) {
        this.db = db;
    }

    @Override
    public Optional<Manor> findBySlot(GuildId guild, int slot) {
        String sql = "SELECT owner_uuid, level FROM manor WHERE guild_id=? AND slot=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                PlayerRef owner = PlayerRef.of(UUID.fromString(rs.getString("owner_uuid")));
                int level = rs.getInt("level");
                Set<PlayerRef> co = loadCoBuilders(c, guild, slot);
                return Optional.of(new Manor(slot, guild, owner, level, co));
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询庄园失败: " + guild.value() + "#" + slot, e);
        }
    }

    @Override
    public Optional<Manor> findByOwner(GuildId guild, PlayerRef owner) {
        String sql = "SELECT slot, level FROM manor WHERE guild_id=? AND owner_uuid=? LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            ps.setString(2, owner.uuid().toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                int slot = rs.getInt("slot");
                int level = rs.getInt("level");
                Set<PlayerRef> co = loadCoBuilders(c, guild, slot);
                return Optional.of(new Manor(slot, guild, owner, level, co));
            }
        } catch (SQLException e) {
            throw new PersistenceException("按庄主查询庄园失败: " + guild.value(), e);
        }
    }

    @Override
    public Optional<Manor> findByOwnerAnywhere(PlayerRef owner) {
        String sql = "SELECT guild_id, slot, level FROM manor WHERE owner_uuid=? LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, owner.uuid().toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                GuildId guild = new GuildId(rs.getString("guild_id"));
                int slot = rs.getInt("slot");
                int level = rs.getInt("level");
                Set<PlayerRef> co = loadCoBuilders(c, guild, slot);
                return Optional.of(new Manor(slot, guild, owner, level, co));
            }
        } catch (SQLException e) {
            throw new PersistenceException("跨公会按 owner 查庄园失败", e);
        }
    }

    @Override
    public List<Manor> findAll(GuildId guild) {
        String sql = "SELECT slot, owner_uuid, level FROM manor WHERE guild_id=? ORDER BY slot";
        List<Manor> result = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    PlayerRef owner = PlayerRef.of(UUID.fromString(rs.getString("owner_uuid")));
                    int level = rs.getInt("level");
                    result.add(new Manor(slot, guild, owner, level, loadCoBuilders(c, guild, slot)));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("列举庄园失败: " + guild.value(), e);
        }
        return result;
    }

    @Override
    public void save(Manor manor) {
        String upsert = """
                INSERT INTO manor(guild_id, slot, owner_uuid, level) VALUES(?,?,?,?)
                ON CONFLICT(guild_id, slot) DO UPDATE SET
                    owner_uuid=excluded.owner_uuid,
                    level=excluded.level""";
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(upsert)) {
                    ps.setString(1, manor.guild().value());
                    ps.setInt(2, manor.slot());
                    ps.setString(3, manor.owner().uuid().toString());
                    ps.setInt(4, manor.level());
                    ps.executeUpdate();
                }
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM manor_cobuilder WHERE guild_id=? AND slot=?")) {
                    del.setString(1, manor.guild().value());
                    del.setInt(2, manor.slot());
                    del.executeUpdate();
                }
                if (!manor.coBuilders().isEmpty()) {
                    try (PreparedStatement ins = c.prepareStatement(
                            "INSERT INTO manor_cobuilder(guild_id, slot, player_uuid) VALUES(?,?,?)")) {
                        for (PlayerRef co : manor.coBuilders()) {
                            ins.setString(1, manor.guild().value());
                            ins.setInt(2, manor.slot());
                            ins.setString(3, co.uuid().toString());
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new PersistenceException("保存庄园失败: " + manor.guild().value() + "#" + manor.slot(), e);
        }
    }

    @Override
    public void delete(GuildId guild, int slot) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM manor WHERE guild_id=? AND slot=?")) {
                    ps.setString(1, guild.value());
                    ps.setInt(2, slot);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM manor_cobuilder WHERE guild_id=? AND slot=?")) {
                    ps.setString(1, guild.value());
                    ps.setInt(2, slot);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new PersistenceException("删除庄园失败: " + guild.value() + "#" + slot, e);
        }
    }

    @Override
    public int nextFreeSlot(GuildId guild) {
        String sql = "SELECT slot FROM manor WHERE guild_id=? ORDER BY slot";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            try (ResultSet rs = ps.executeQuery()) {
                int expected = 0;
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    if (slot != expected) {
                        return expected; // 最小空缺(复用离会成员释放的 slot, 保持螺旋紧凑)
                    }
                    expected++;
                }
                return expected;
            }
        } catch (SQLException e) {
            throw new PersistenceException("计算空闲 slot 失败: " + guild.value(), e);
        }
    }

    private Set<PlayerRef> loadCoBuilders(Connection c, GuildId guild, int slot) throws SQLException {
        Set<PlayerRef> co = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT player_uuid FROM manor_cobuilder WHERE guild_id=? AND slot=?")) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    co.add(PlayerRef.of(UUID.fromString(rs.getString("player_uuid"))));
                }
            }
        }
        return co;
    }
}
