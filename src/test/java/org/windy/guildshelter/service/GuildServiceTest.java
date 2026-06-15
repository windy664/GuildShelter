package org.windy.guildshelter.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.layout.LayoutConfig;
import org.windy.guildshelter.domain.model.ChunkRegion;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.model.TerrainPrepMode;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;
import org.windy.guildshelter.domain.port.TerrainPreparer;
import org.windy.guildshelter.domain.port.WorldControl;
import org.windy.guildshelter.domain.rule.LevelRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildServiceTest {

    private final GuildId g = new GuildId("g1");
    private final LayoutCalculator layout = new LayoutCalculator(LayoutConfig.defaults());

    private FakeGuildRepo guilds;
    private FakeManorRepo manors;
    private FakeWorldControl worlds;
    private FakeTerrain terrain;
    private GuildService service;

    @BeforeEach
    void setup() {
        guilds = new FakeGuildRepo();
        manors = new FakeManorRepo();
        worlds = new FakeWorldControl();
        terrain = new FakeTerrain();
        service = new GuildService(guilds, manors, worlds, terrain, layout,
                LevelRules.defaults(), TerrainPrepMode.CLEAR_VEGETATION);
    }

    @Test
    void createGuildIsIdempotent() {
        GuildWorld a = service.createGuild(g, 42L);
        GuildWorld b = service.createGuild(g, 999L);
        assertEquals(a, b);
        assertEquals(1, worlds.ensureCalls); // 第二次不再创建
    }

    @Test
    void assignManorAllocatesSequentialSlotsAndPrepsTerrain() {
        service.createGuild(g, 1L);
        Manor m0 = service.assignManor(g, player());
        Manor m1 = service.assignManor(g, player());
        assertEquals(0, m0.slot());
        assertEquals(1, m1.slot());
        assertEquals(2, guilds.find(g).orElseThrow().allocatedSlots());
        assertEquals(2, terrain.calls.size()); // 每次分配整地一次
        assertTrue(worlds.borderCalls >= 2);   // 边界随分配扩
    }

    @Test
    void assignManorIsIdempotentPerPlayer() {
        service.createGuild(g, 1L);
        PlayerRef p = player();
        Manor first = service.assignManor(g, p);
        Manor again = service.assignManor(g, p);
        assertEquals(first.slot(), again.slot());
        assertEquals(1, manors.findAll(g).size());
    }

    @Test
    void releaseThenAssignReusesGap() {
        service.createGuild(g, 1L);
        PlayerRef p0 = player();
        PlayerRef p1 = player();
        service.assignManor(g, p0); // slot0
        service.assignManor(g, p1); // slot1
        service.releaseManor(g, p0); // 释放 slot0
        Manor reused = service.assignManor(g, player());
        assertEquals(0, reused.slot()); // 复用最小空缺
    }

    @Test
    void terrainRegionIsWorldShiftedByOrigin() {
        worlds.originX = 10;
        worlds.originZ = -3;
        service.createGuild(g, 1L);
        service.assignManor(g, player());
        ChunkRegion layoutActive = layout.activeRegion(0, 1);
        ChunkRegion expected = layoutActive.shift(10, -3);
        assertEquals(expected, terrain.calls.get(0).region);
    }

    @Test
    void releaseAnywhereAndDissolve() {
        service.createGuild(g, 1L);
        PlayerRef p = player();
        service.assignManor(g, p);
        service.releaseManorAnywhere(p); // 不需知道公会
        assertTrue(manors.findByOwner(g, p).isEmpty());

        service.assignManor(g, player());
        service.dissolveGuild(g);
        assertTrue(guilds.find(g).isEmpty());
        assertTrue(manors.findAll(g).isEmpty());
    }

    @Test
    void manorUpgradeGatedByGuildLevel() {
        service.createGuild(g, 1L);
        PlayerRef p = player();
        service.assignManor(g, p);
        // 1级公会, 庄园上限1 → 不能升
        assertFalse(service.upgradeManor(g, p));
        service.upgradeGuild(g); // 公会升到2
        assertTrue(service.upgradeManor(g, p));
        assertEquals(2, manors.findByOwner(g, p).orElseThrow().level());
    }

    private PlayerRef player() {
        return PlayerRef.of(UUID.randomUUID());
    }

    // ---- 内存假实现 ----

    static final class FakeGuildRepo implements GuildRepository {
        final Map<String, GuildWorld> map = new HashMap<>();
        public Optional<GuildWorld> find(GuildId g) { return Optional.ofNullable(map.get(g.value())); }
        public boolean exists(GuildId g) { return map.containsKey(g.value()); }
        public void save(GuildWorld w) { map.put(w.guild().value(), w); }
        public void delete(GuildId g) { map.remove(g.value()); }
        public List<GuildWorld> findAll() { return new ArrayList<>(map.values()); }
    }

    static final class FakeManorRepo implements ManorRepository {
        final Map<String, Manor> bySlot = new HashMap<>();
        private String k(GuildId g, int s) { return g.value() + "#" + s; }
        public Optional<Manor> findBySlot(GuildId g, int s) { return Optional.ofNullable(bySlot.get(k(g, s))); }
        public Optional<Manor> findByOwner(GuildId g, PlayerRef o) {
            return bySlot.values().stream()
                    .filter(m -> m.guild().equals(g) && m.owner().equals(o)).findFirst();
        }
        public Optional<Manor> findByOwnerAnywhere(PlayerRef o) {
            return bySlot.values().stream().filter(m -> m.owner().equals(o)).findFirst();
        }
        public List<Manor> findAll(GuildId g) {
            List<Manor> out = new ArrayList<>();
            bySlot.values().forEach(m -> { if (m.guild().equals(g)) out.add(m); });
            return out;
        }
        public void save(Manor m) { bySlot.put(k(m.guild(), m.slot()), m); }
        public void delete(GuildId g, int s) { bySlot.remove(k(g, s)); }
        public int nextFreeSlot(GuildId g) {
            int i = 0;
            while (bySlot.containsKey(k(g, i))) i++;
            return i;
        }
    }

    static final class FakeWorldControl implements WorldControl {
        int ensureCalls = 0;
        int borderCalls = 0;
        int originX = 0;
        int originZ = 0;
        public String worldName(GuildId g) { return "guild_" + g.value(); }
        public GuildWorld ensureWorld(GuildWorld w) {
            ensureCalls++;
            return w.withOrigin(originX, originZ);
        }
        public void applyBorder(GuildWorld w) { borderCalls++; }
        public boolean unloadGuild(GuildId g) { return true; }
    }

    record PrepCall(String world, ChunkRegion region, TerrainPrepMode mode) { }

    static final class FakeTerrain implements TerrainPreparer {
        final List<PrepCall> calls = new ArrayList<>();
        public void prepare(String worldName, ChunkRegion region, TerrainPrepMode mode) {
            calls.add(new PrepCall(worldName, region, mode));
        }
    }
}
