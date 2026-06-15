package org.windy.guildshelter.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteRepositoryTest {

    private SqliteDatabase db;
    private SqliteGuildRepository guilds;
    private SqliteManorRepository manors;

    private final GuildId g = new GuildId("guild-A");

    @BeforeEach
    void setup(@TempDir Path dir) {
        db = SqliteDatabase.forFile(dir.resolve("test.db"));
        guilds = new SqliteGuildRepository(db);
        manors = new SqliteManorRepository(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void guildWorldSaveFindUpdateDelete() {
        assertFalse(guilds.exists(g));
        guilds.save(GuildWorld.create(g, "guild_A", 123456789L).withOrigin(5, -7));
        assertTrue(guilds.exists(g));

        GuildWorld loaded = guilds.find(g).orElseThrow();
        assertEquals("guild_A", loaded.worldName());
        assertEquals(123456789L, loaded.seed());
        assertEquals(5, loaded.originChunkX());
        assertEquals(-7, loaded.originChunkZ());
        assertEquals(1, loaded.guildLevel());
        assertEquals(0, loaded.allocatedSlots());

        guilds.save(loaded.withGuildLevel(3).withAllocatedSlots(7));
        GuildWorld updated = guilds.find(g).orElseThrow();
        assertEquals(3, updated.guildLevel());
        assertEquals(7, updated.allocatedSlots());
        assertEquals(123456789L, updated.seed());      // seed/origin 不丢
        assertEquals(5, updated.originChunkX());

        guilds.delete(g);
        assertFalse(guilds.exists(g));
        assertTrue(guilds.find(g).isEmpty());
    }

    @Test
    void manorRoundTripWithCoBuilders() {
        PlayerRef owner = PlayerRef.of(UUID.randomUUID());
        PlayerRef co1 = PlayerRef.of(UUID.randomUUID());
        PlayerRef co2 = PlayerRef.of(UUID.randomUUID());

        manors.save(new Manor(0, g, owner, 2, Set.of(co1, co2)));

        Manor loaded = manors.findBySlot(g, 0).orElseThrow();
        assertEquals(owner, loaded.owner());
        assertEquals(2, loaded.level());
        assertEquals(Set.of(co1, co2), loaded.coBuilders());

        assertEquals(0, manors.findByOwner(g, owner).orElseThrow().slot());
    }

    @Test
    void manorSaveReplacesCoBuilders() {
        PlayerRef owner = PlayerRef.of(UUID.randomUUID());
        PlayerRef co1 = PlayerRef.of(UUID.randomUUID());
        PlayerRef co2 = PlayerRef.of(UUID.randomUUID());

        manors.save(new Manor(0, g, owner, 1, Set.of(co1)));
        manors.save(new Manor(0, g, owner, 1, Set.of(co2))); // 覆盖共建人

        assertEquals(Set.of(co2), manors.findBySlot(g, 0).orElseThrow().coBuilders());
    }

    @Test
    void findAllAndDelete() {
        PlayerRef o1 = PlayerRef.of(UUID.randomUUID());
        PlayerRef o2 = PlayerRef.of(UUID.randomUUID());
        manors.save(Manor.create(0, g, o1));
        manors.save(Manor.create(1, g, o2));

        List<Manor> all = manors.findAll(g);
        assertEquals(2, all.size());

        manors.delete(g, 0);
        assertTrue(manors.findBySlot(g, 0).isEmpty());
        assertEquals(1, manors.findAll(g).size());
    }

    @Test
    void nextFreeSlotReusesSmallestGap() {
        assertEquals(0, manors.nextFreeSlot(g)); // 空

        PlayerRef p = PlayerRef.of(UUID.randomUUID());
        manors.save(Manor.create(0, g, p));
        manors.save(Manor.create(1, g, p));
        manors.save(Manor.create(3, g, p)); // 缺 2
        assertEquals(2, manors.nextFreeSlot(g));

        manors.save(Manor.create(2, g, p)); // 填满 0..3
        assertEquals(4, manors.nextFreeSlot(g));
    }
}
