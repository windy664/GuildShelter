package org.windy.guildshelter.persistence;

import org.windy.guildshelter.domain.layout.LayoutConfig;
import org.windy.guildshelter.domain.port.CampSpawnStore;
import org.windy.guildshelter.domain.port.CityFlagStore;
import org.windy.guildshelter.domain.port.CityHologramStore;
import org.windy.guildshelter.domain.port.CityTrustStore;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;
import org.windy.guildshelter.domain.port.RoadPermitStore;

/** JDBC 存储后端（SQLite / MySQL）：一个 {@link JdbcDatabase} + 领域仓库。 */
public final class JdbcStorage implements Storage {

    private final JdbcDatabase db;
    private final GuildRepository guilds;
    private final ManorRepository manors;
    private final CityTrustStore cityTrust;
    private final RoadPermitStore roadPermit;
    private final CampSpawnStore campSpawn;
    private final CityFlagStore cityFlags;
    private final CityHologramStore cityHolograms;

    public JdbcStorage(JdbcDatabase db, SqlDialect dialect, LayoutConfig fallbackLayout) {
        this.db = db;
        this.guilds = new JdbcGuildRepository(db, dialect, fallbackLayout);
        this.manors = new JdbcManorRepository(db, dialect);
        this.cityTrust = new JdbcCityTrustStore(db);
        this.roadPermit = new JdbcRoadPermitStore(db, dialect instanceof SqliteDialect);
        boolean sqlite = dialect instanceof SqliteDialect;
        this.campSpawn = new JdbcCampSpawnStore(db, sqlite);
        this.cityFlags = new JdbcCityFlagStore(db, sqlite);
        this.cityHolograms = new JdbcCityHologramStore(db);
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
    public CityTrustStore cityTrust() {
        return cityTrust;
    }

    @Override
    public RoadPermitStore roadPermit() {
        return roadPermit;
    }

    @Override
    public CampSpawnStore campSpawn() {
        return campSpawn;
    }

    @Override
    public CityFlagStore cityFlags() {
        return cityFlags;
    }

    @Override
    public CityHologramStore cityHolograms() {
        return cityHolograms;
    }

    @Override
    public void close() {
        db.close();
    }
}
