package org.windy.guildshelter.persistence;

import org.windy.guildshelter.domain.layout.LayoutConfig;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;

/** JDBC 存储后端（SQLite / MySQL）：一个 {@link JdbcDatabase} + 两个 JDBC 仓库。 */
public final class JdbcStorage implements Storage {

    private final JdbcDatabase db;
    private final GuildRepository guilds;
    private final ManorRepository manors;

    public JdbcStorage(JdbcDatabase db, SqlDialect dialect, LayoutConfig fallbackLayout) {
        this.db = db;
        this.guilds = new JdbcGuildRepository(db, dialect, fallbackLayout);
        this.manors = new JdbcManorRepository(db, dialect);
    }

    @Override
    public GuildRepository guilds() {
        return guilds;
    }

    @Override
    public ManorRepository manors() {
        return manors;
    }

    @Override
    public void close() {
        db.close();
    }
}
