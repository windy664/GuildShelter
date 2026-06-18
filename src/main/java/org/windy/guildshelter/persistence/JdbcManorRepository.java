package org.windy.guildshelter.persistence;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.port.ManorRepository;
import org.windy.guildshelter.domain.port.ManorRepository.CommentEntry;

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
                // 清理 merge 记录（该 slot 作为主合并方或被吸收方）
                for (String col : new String[]{"primary_slot", "absorbed_slot"}) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "DELETE FROM manor_merge WHERE guild_id=? AND " + col + "=?")) {
                        ps.setString(1, guild.value());
                        ps.setInt(2, slot);
                        ps.executeUpdate();
                    }
                }
                // 清理评分和留言
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM manor_rating WHERE guild_id=? AND slot=?")) {
                    ps.setString(1, guild.value());
                    ps.setInt(2, slot);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM manor_comment WHERE guild_id=? AND slot=?")) {
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

    // ===== 访问统计 =====

    @Override
    public void incrementVisit(GuildId guild, int slot) {
        String sql = dialect instanceof SqliteDialect
                ? "INSERT INTO manor_visit(guild_id,slot,visit_count) VALUES(?,?,1) ON CONFLICT(guild_id,slot) DO UPDATE SET visit_count=visit_count+1"
                : "INSERT INTO manor_visit(guild_id,slot,visit_count) VALUES(?,?,1) ON DUPLICATE KEY UPDATE visit_count=visit_count+1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("记录访问失败", e);
        }
    }

    @Override
    public void incrementVisitBy(GuildId guild, int slot, int count) {
        if (count <= 0) return;
        // 先尝试 UPDATE，如果不存在则 INSERT
        String updateSql = "UPDATE manor_visit SET visit_count = visit_count + ? WHERE guild_id=? AND slot=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(updateSql)) {
            ps.setInt(1, count);
            ps.setString(2, guild.value());
            ps.setInt(3, slot);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                // 不存在，插入初始值
                String insertSql = dialect instanceof SqliteDialect
                        ? "INSERT INTO manor_visit(guild_id,slot,visit_count) VALUES(?,?,?) ON CONFLICT(guild_id,slot) DO UPDATE SET visit_count=visit_count+?"
                        : "INSERT INTO manor_visit(guild_id,slot,visit_count) VALUES(?,?,?) ON DUPLICATE KEY UPDATE visit_count=visit_count+?";
                try (PreparedStatement ins = c.prepareStatement(insertSql)) {
                    ins.setString(1, guild.value());
                    ins.setInt(2, slot);
                    ins.setInt(3, count);
                    ins.setInt(4, count);
                    ins.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("批量记录访问失败", e);
        }
    }

    @Override
    public int getVisitCount(GuildId guild, int slot) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT visit_count FROM manor_visit WHERE guild_id=? AND slot=?")) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("visit_count") : 0;
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询访问次数失败", e);
        }
    }

    // ===== 送花/人气 =====

    @Override
    public void sendFlower(GuildId guild, int slot, PlayerRef sender) {
        String today = java.time.LocalDate.now().toString(); // YYYY-MM-DD
        String sql = dialect instanceof SqliteDialect
                ? "INSERT INTO manor_flower(guild_id,slot,sender_uuid,sent_date) VALUES(?,?,?,?) ON CONFLICT DO NOTHING"
                : "INSERT IGNORE INTO manor_flower(guild_id,slot,sender_uuid,sent_date) VALUES(?,?,?,?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            ps.setString(3, sender.uuid().toString());
            ps.setString(4, today);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("送花失败", e);
        }
    }

    @Override
    public int getTodayFlowerCount(GuildId guild, int slot) {
        String today = java.time.LocalDate.now().toString();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) AS cnt FROM manor_flower WHERE guild_id=? AND slot=? AND sent_date=?")) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            ps.setString(3, today);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("cnt") : 0;
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询今日花数失败", e);
        }
    }

    @Override
    public boolean hasSentFlowerToday(GuildId guild, int slot, PlayerRef sender) {
        String today = java.time.LocalDate.now().toString();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM manor_flower WHERE guild_id=? AND slot=? AND sender_uuid=? AND sent_date=? LIMIT 1")) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            ps.setString(3, sender.uuid().toString());
            ps.setString(4, today);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询送花记录失败", e);
        }
    }

    @Override
    public double getPopularity(GuildId guild, int slot) {
        // 人气 = 累计花数 × 0.3 + 累计访问量 × 0.1
        int flowers = 0;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(DISTINCT sender_uuid || sent_date) AS cnt FROM manor_flower WHERE guild_id=? AND slot=?")) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) flowers = rs.getInt("cnt");
            }
        } catch (SQLException ignored) {}
        int visits = getVisitCount(guild, slot);
        return flowers * 0.3 + visits * 0.1;
    }

    // ===== 评分系统 =====

    @Override
    public void rate(GuildId guild, int slot, PlayerRef rater, int score) {
        String sql = dialect instanceof SqliteDialect
                ? "INSERT INTO manor_rating(guild_id,slot,rater_uuid,score) VALUES(?,?,?,?) ON CONFLICT(guild_id,slot,rater_uuid) DO UPDATE SET score=excluded.score"
                : "INSERT INTO manor_rating(guild_id,slot,rater_uuid,score) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE score=VALUES(score)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            ps.setString(3, rater.uuid().toString());
            ps.setInt(4, score);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("保存评分失败", e);
        }
    }

    @Override
    public int getRating(GuildId guild, int slot, PlayerRef rater) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT score FROM manor_rating WHERE guild_id=? AND slot=? AND rater_uuid=?")) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            ps.setString(3, rater.uuid().toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("score") : 0;
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询评分失败", e);
        }
    }

    @Override
    public double getAverageRating(GuildId guild, int slot) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT AVG(score) AS avg_score FROM manor_rating WHERE guild_id=? AND slot=?")) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("avg_score") : 0;
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询平均评分失败", e);
        }
    }

    @Override
    public List<Integer> getTopRatedSlots(GuildId guild, int limit) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT slot, AVG(score) AS avg_score FROM manor_rating WHERE guild_id=? GROUP BY slot ORDER BY avg_score DESC LIMIT ?")) {
            ps.setString(1, guild.value());
            ps.setInt(2, limit);
            List<Integer> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getInt("slot"));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new PersistenceException("查询评分排行失败", e);
        }
    }

    @Override
    public int getRatingCount(GuildId guild, int slot) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) AS cnt FROM manor_rating WHERE guild_id=? AND slot=?")) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("cnt") : 0;
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询评分数失败", e);
        }
    }

    // ===== 留言系统 =====

    @Override
    public void addComment(GuildId guild, int slot, PlayerRef author, String message) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO manor_comment(guild_id,slot,author_uuid,message,created_at) VALUES(?,?,?,?,?)")) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            ps.setString(3, author.uuid().toString());
            ps.setString(4, message);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("保存留言失败", e);
        }
    }

    @Override
    public List<CommentEntry> getComments(GuildId guild, int slot, int limit) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT author_uuid, message, created_at FROM manor_comment WHERE guild_id=? AND slot=? ORDER BY created_at DESC LIMIT ?")) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            ps.setInt(3, limit);
            List<CommentEntry> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new CommentEntry(guild, slot,
                            PlayerRef.of(UUID.fromString(rs.getString("author_uuid"))),
                            rs.getString("message"),
                            rs.getLong("created_at")));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new PersistenceException("查询留言失败", e);
        }
    }

    @Override
    public List<CommentEntry> getInbox(PlayerRef owner, int limit) {
        // 查该玩家拥有的所有地皮收到的留言
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT mc.guild_id, mc.slot, mc.author_uuid, mc.message, mc.created_at "
                        + "FROM manor_comment mc JOIN manor m ON mc.guild_id=m.guild_id AND mc.slot=m.slot "
                        + "WHERE m.owner_uuid=? ORDER BY mc.created_at DESC LIMIT ?")) {
            ps.setString(1, owner.uuid().toString());
            ps.setInt(2, limit);
            List<CommentEntry> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new CommentEntry(
                            new GuildId(rs.getString("guild_id")),
                            rs.getInt("slot"),
                            PlayerRef.of(UUID.fromString(rs.getString("author_uuid"))),
                            rs.getString("message"),
                            rs.getLong("created_at")));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new PersistenceException("查询收件箱失败", e);
        }
    }

    // ===== 合并系统 =====

    @Override
    public void merge(int primarySlot, int absorbedSlot, GuildId guild) {
        String sql = dialect instanceof SqliteDialect
                ? "INSERT INTO manor_merge(guild_id,primary_slot,absorbed_slot) VALUES(?,?,?) ON CONFLICT DO NOTHING"
                : "INSERT IGNORE INTO manor_merge(guild_id,primary_slot,absorbed_slot) VALUES(?,?,?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            ps.setInt(2, primarySlot);
            ps.setInt(3, absorbedSlot);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("保存合并失败", e);
        }
    }

    @Override
    public int getMergedTarget(GuildId guild, int slot) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT primary_slot FROM manor_merge WHERE guild_id=? AND absorbed_slot=?")) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("primary_slot") : slot;
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询合并目标失败", e);
        }
    }

    @Override
    public List<Integer> getMergedSlots(GuildId guild, int primarySlot) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT absorbed_slot FROM manor_merge WHERE guild_id=? AND primary_slot=?")) {
            ps.setString(1, guild.value());
            ps.setInt(2, primarySlot);
            List<Integer> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getInt("absorbed_slot"));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new PersistenceException("查询合并 slot 失败", e);
        }
    }

    @Override
    public java.util.List<MergeEntry> getAllMerges(GuildId guild) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT primary_slot, absorbed_slot FROM manor_merge WHERE guild_id=?")) {
            ps.setString(1, guild.value());
            java.util.List<MergeEntry> result = new java.util.ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new MergeEntry(rs.getInt("primary_slot"), rs.getInt("absorbed_slot")));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new PersistenceException("查询全部合并记录失败", e);
        }
    }

    @Override
    public void unmerge(GuildId guild, int primarySlot) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM manor_merge WHERE guild_id=? AND primary_slot=?")) {
            ps.setString(1, guild.value());
            ps.setInt(2, primarySlot);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("取消合并失败", e);
        }
    }

    @Override
    public void unmergeOne(GuildId guild, int primarySlot, int absorbedSlot) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM manor_merge WHERE guild_id=? AND primary_slot=? AND absorbed_slot=?")) {
            ps.setString(1, guild.value());
            ps.setInt(2, primarySlot);
            ps.setInt(3, absorbedSlot);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("取消单条合并失败", e);
        }
    }

    // ===== 权限模板 =====

    @Override
    public void saveTemplate(GuildId guild, String name, java.util.Map<String, String> flags) {
        String sql = dialect instanceof SqliteDialect
                ? "INSERT INTO manor_template(guild_id,name,flags) VALUES(?,?,?) ON CONFLICT(guild_id,name) DO UPDATE SET flags=excluded.flags"
                : "INSERT INTO manor_template(guild_id,name,flags) VALUES(?,?,?) ON DUPLICATE KEY UPDATE flags=VALUES(flags)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            ps.setString(2, name);
            ps.setString(3, FlagsCsv.toCsv(flags));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("保存模板失败", e);
        }
    }

    @Override
    public void deleteTemplate(GuildId guild, String name) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM manor_template WHERE guild_id=? AND name=?")) {
            ps.setString(1, guild.value());
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("删除模板失败", e);
        }
    }

    @Override
    public java.util.Optional<java.util.Map<String, String>> getTemplate(GuildId guild, String name) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT flags FROM manor_template WHERE guild_id=? AND name=?")) {
            ps.setString(1, guild.value());
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return java.util.Optional.empty();
                return java.util.Optional.of(FlagsCsv.parse(rs.getString("flags")));
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询模板失败", e);
        }
    }

    @Override
    public java.util.List<String> listTemplates(GuildId guild) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT name FROM manor_template WHERE guild_id=? ORDER BY name")) {
            ps.setString(1, guild.value());
            java.util.List<String> result = new java.util.ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString("name"));
            }
            return result;
        } catch (SQLException e) {
            throw new PersistenceException("列举模板失败", e);
        }
    }

    // ===== 子领地 =====

    @Override
    public void saveSub(GuildId guild, int slot, String name, int minX, int minZ, int maxX, int maxZ, java.util.Map<String, String> flags) {
        String sql = dialect instanceof SqliteDialect
                ? "INSERT INTO manor_sub(guild_id,slot,name,min_x,min_z,max_x,max_z,flags) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(guild_id,slot,name) DO UPDATE SET min_x=excluded.min_x,min_z=excluded.min_z,max_x=excluded.max_x,max_z=excluded.max_z,flags=excluded.flags"
                : "INSERT INTO manor_sub(guild_id,slot,name,min_x,min_z,max_x,max_z,flags) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE min_x=VALUES(min_x),min_z=VALUES(min_z),max_x=VALUES(max_x),max_z=VALUES(max_z),flags=VALUES(flags)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            ps.setString(3, name);
            ps.setInt(4, minX);
            ps.setInt(5, minZ);
            ps.setInt(6, maxX);
            ps.setInt(7, maxZ);
            ps.setString(8, FlagsCsv.toCsv(flags));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("保存子领地失败", e);
        }
    }

    @Override
    public void deleteSub(GuildId guild, int slot, String name) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM manor_sub WHERE guild_id=? AND slot=? AND name=?")) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            ps.setString(3, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("删除子领地失败", e);
        }
    }

    @Override
    public java.util.List<SubEntry> getSubs(GuildId guild, int slot) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT name,min_x,min_z,max_x,max_z,flags FROM manor_sub WHERE guild_id=? AND slot=?")) {
            ps.setString(1, guild.value());
            ps.setInt(2, slot);
            java.util.List<SubEntry> result = new java.util.ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new SubEntry(guild, slot,
                            rs.getString("name"),
                            rs.getInt("min_x"), rs.getInt("min_z"),
                            rs.getInt("max_x"), rs.getInt("max_z"),
                            FlagsCsv.parse(rs.getString("flags"))));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new PersistenceException("查询子领地失败", e);
        }
    }

    // ===== 搬家记录 =====

    @Override
    public long getLastMoveTime(java.util.UUID playerUuid) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT last_move_at FROM manor_move_record WHERE player_uuid=?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("last_move_at") : 0;
            }
        } catch (SQLException e) {
            throw new PersistenceException("查询搬家记录失败", e);
        }
    }

    @Override
    public void recordMove(java.util.UUID playerUuid, long timestamp) {
        String sql = dialect instanceof SqliteDialect
                ? "INSERT INTO manor_move_record(player_uuid,last_move_at) VALUES(?,?) ON CONFLICT(player_uuid) DO UPDATE SET last_move_at=excluded.last_move_at"
                : "INSERT INTO manor_move_record(player_uuid,last_move_at) VALUES(?,?) ON DUPLICATE KEY UPDATE last_move_at=VALUES(last_move_at)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("记录搬家失败", e);
        }
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
