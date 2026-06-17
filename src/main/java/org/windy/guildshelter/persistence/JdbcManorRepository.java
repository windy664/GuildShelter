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

/** JDBC 实现的庄园仓库（SQLite/MySQL 共用，upsert 语法由方言给出）。 */
public final class JdbcManorRepository implements ManorRepository {

    private final JdbcDatabase db;
    private final SqlDialect dialect;

    public JdbcManorRepository(JdbcDatabase db, SqlDialect dialect) {
        this.db = db;
        this.dialect = dialect;
    }

    @Override
    public Optional<Manor> findBySlot(GuildId guild, int slot) {
        String sql = "SELECT owner_uuid, level, flags FROM manor WHERE guild_id=? AND slot=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                PlayerRef owner = PlayerRef.of(UUID.fromString(rs.getString("owner_uuid")));
                int level = rs.getInt("level");
                return Optional.of(new Manor(slot, guild, owner, level,
                        loadPlayers(c, "manor_cobuilder", guild, slot),
                        loadPlayers(c, "manor_member", guild, slot),
                        loadPlayers(c, "manor_denied", guild, slot),
                        FlagsCsv.parse(rs.getString("flags"))));
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询庄园失败: " + guild.value() + "#" + slot, e);
        }
    }

    @Override
    public Optional<Manor> findByOwner(GuildId guild, PlayerRef owner) {
        String sql = "SELECT slot, level, flags FROM manor WHERE guild_id=? AND owner_uuid=? LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            ps.setString(2, owner.uuid().toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                int slot = rs.getInt("slot");
                int level = rs.getInt("level");
                return Optional.of(new Manor(slot, guild, owner, level,
                        loadPlayers(c, "manor_cobuilder", guild, slot),
                        loadPlayers(c, "manor_member", guild, slot),
                        loadPlayers(c, "manor_denied", guild, slot),
                        FlagsCsv.parse(rs.getString("flags"))));
            }
        } catch (SQLException e) {
            throw new PersistenceException("按庄主查询庄园失败: " + guild.value(), e);
        }
    }

    @Override
    public Optional<Manor> findByOwnerAnywhere(PlayerRef owner) {
        String sql = "SELECT guild_id, slot, level, flags FROM manor WHERE owner_uuid=? LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, owner.uuid().toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                GuildId guild = new GuildId(rs.getString("guild_id"));
                int slot = rs.getInt("slot");
                int level = rs.getInt("level");
                return Optional.of(new Manor(slot, guild, owner, level,
                        loadPlayers(c, "manor_cobuilder", guild, slot),
                        loadPlayers(c, "manor_member", guild, slot),
                        loadPlayers(c, "manor_denied", guild, slot),
                        FlagsCsv.parse(rs.getString("flags"))));
            }
        } catch (SQLException e) {
            throw new PersistenceException("跨公会按 owner 查庄园失败", e);
        }
    }

    @Override
    public List<Manor> findAll(GuildId guild) {
        String sql = "SELECT slot, owner_uuid, level, flags FROM manor WHERE guild_id=? ORDER BY slot";
        List<Manor> result = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    PlayerRef owner = PlayerRef.of(UUID.fromString(rs.getString("owner_uuid")));
                    int level = rs.getInt("level");
                    result.add(new Manor(slot, guild, owner, level,
                            loadPlayers(c, "manor_cobuilder", guild, slot),
                        loadPlayers(c, "manor_member", guild, slot),
                        loadPlayers(c, "manor_denied", guild, slot),
                        FlagsCsv.parse(rs.getString("flags"))));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("列举庄园失败: " + guild.value(), e);
        }
        return result;
    }

    @Override
    public void save(Manor manor) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(dialect.upsertManor())) {
                    ps.setString(1, manor.guild().value());
                    ps.setInt(2, manor.slot());
                    ps.setString(3, manor.owner().uuid().toString());
                    ps.setInt(4, manor.level());
                    ps.setString(5, FlagsCsv.toCsv(manor.flags()));
                    ps.executeUpdate();
                }
                replacePlayers(c, "manor_cobuilder", manor.guild(), manor.slot(), manor.coBuilders());
                replacePlayers(c, "manor_member", manor.guild(), manor.slot(), manor.members());
                replacePlayers(c, "manor_denied", manor.guild(), manor.slot(), manor.denied());
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
                for (String table : new String[]{"manor_cobuilder", "manor_member", "manor_denied"}) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "DELETE FROM " + table + " WHERE guild_id=? AND slot=?")) {
                        ps.setString(1, guild.value());
                        ps.setInt(2, slot);
                        ps.executeUpdate();
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
                        return expected;
                    }
                    expected++;
                }
                return expected;
            }
        } catch (SQLException e) {
            throw new PersistenceException("计算空闲 slot 失败: " + guild.value(), e);
        }
    }

    /** 从某成员表(manor_cobuilder/manor_member/manor_denied)读该 slot 的玩家集。table 为代码内常量，无注入风险。 */
    private Set<PlayerRef> loadPlayers(Connection c, String table, GuildId guild, int slot) throws SQLException {
        Set<PlayerRef> out = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT player_uuid FROM " + table + " WHERE guild_id=? AND slot=?")) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(PlayerRef.of(UUID.fromString(rs.getString("player_uuid"))));
                }
            }
        }
        return out;
    }

    /** 全量替换某成员表里该 slot 的玩家集（先删后批量插，事务内调用）。 */
    private void replacePlayers(Connection c, String table, GuildId guild, int slot,
                                Set<PlayerRef> players) throws SQLException {
        try (PreparedStatement del = c.prepareStatement(
                "DELETE FROM " + table + " WHERE guild_id=? AND slot=?")) {
            del.setString(1, guild.value());
            del.setInt(2, slot);
            del.executeUpdate();
        }
        if (players.isEmpty()) {
            return;
        }
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO " + table + "(guild_id, slot, player_uuid) VALUES(?,?,?)")) {
            for (PlayerRef p : players) {
                ins.setString(1, guild.value());
                ins.setInt(2, slot);
                ins.setString(3, p.uuid().toString());
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }
}
