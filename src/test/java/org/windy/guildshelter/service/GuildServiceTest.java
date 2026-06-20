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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        service = new GuildService(guilds, manors, worlds, terrain, LayoutConfig.defaults(),
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
    void manorUpgradesFreelyToPhysicalCapRegardlessOfGuildLevel() {
        service.createGuild(g, 1L); // 公会仍 1 级
        PlayerRef p = player();
        service.assignManor(g, p); // 庄园 1 级
        // 庄园升级是成员自己的事，与公会等级无关：1 级公会也能一路升到物理满级(默认 5)。
        assertTrue(service.upgradeManor(g, p));  // ->2
        assertTrue(service.upgradeManor(g, p));  // ->3
        assertTrue(service.upgradeManor(g, p));  // ->4
        assertTrue(service.upgradeManor(g, p));  // ->5
        assertEquals(5, manors.findByOwner(g, p).orElseThrow().level());
        assertFalse(service.upgradeManor(g, p)); // 已满级，不能再升
    }

    @Test
    void assignManorBlockedWhenGuildFullThenUnlockedByUpgrade() {
        service.createGuild(g, 1L); // 1 级容量 = 5
        for (int i = 0; i < 5; i++) {
            service.assignManor(g, player());
        }
        assertEquals(5, manors.findAll(g).size());
        // 第 6 个超出当前等级名额 → 抛 GuildFullException
        assertThrows(GuildFullException.class, () -> service.assignManor(g, player()));
        // 公会升级 → 容量到 10，新成员可继续加入，复用下一个 slot
        assertTrue(service.upgradeGuild(g));
        Manor sixth = service.assignManor(g, player());
        assertEquals(5, sixth.slot());
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
        public List<Manor> findAllByOwner(GuildId g, PlayerRef o) {
            List<Manor> out = new ArrayList<>();
            bySlot.values().forEach(m -> { if (m.guild().equals(g) && m.owner().equals(o)) out.add(m); });
            return out;
        }
        public int countByOwner(GuildId g, PlayerRef o) {
            return (int) bySlot.values().stream().filter(m -> m.guild().equals(g) && m.owner().equals(o)).count();
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
        // 评分（内存 stub）
        public void rate(GuildId g, int s, PlayerRef r, int score) {}
        public int getRating(GuildId g, int s, PlayerRef r) { return 0; }
        public double getAverageRating(GuildId g, int s) { return 0; }
        public List<Integer> getTopRatedSlots(GuildId g, int limit) { return List.of(); }
        public int getRatingCount(GuildId g, int s) { return 0; }
        // 留言（内存 stub）
        public void addComment(GuildId g, int s, PlayerRef a, String msg) {}
        public List<ManorRepository.CommentEntry> getComments(GuildId g, int s, int limit) { return List.of(); }
        public List<ManorRepository.CommentEntry> getInbox(PlayerRef o, int limit) { return List.of(); }
        // 子领地（内存 stub）
        public void saveSub(GuildId g, int s, String n, int x1, int z1, int x2, int z2, java.util.Map<String,String> f) {}
        public void deleteSub(GuildId g, int s, String n) {}
        public List<ManorRepository.SubEntry> getSubs(GuildId g, int s) { return List.of(); }
        // 模板（内存 stub）
        public void saveTemplate(GuildId g, String n, java.util.Map<String,String> f) {}
        public void deleteTemplate(GuildId g, String n) {}
        public Optional<java.util.Map<String,String>> getTemplate(GuildId g, String n) { return Optional.empty(); }
        public List<String> listTemplates(GuildId g) { return List.of(); }
        // 合并（内存 stub）
        public void merge(int p, int a, GuildId g) {}
        public int getMergedTarget(GuildId g, int s) { return s; }
        public List<Integer> getMergedSlots(GuildId g, int p) { return List.of(); }
        public void unmerge(GuildId g, int p) {}
        public void unmergeOne(GuildId g, int p, int a) {}
        public List<ManorRepository.MergeEntry> getAllMerges(GuildId g) { return List.of(); }
        public void incrementVisit(GuildId g, int s) {}
        public void incrementVisitBy(GuildId g, int s, int count) {}
        public int getVisitCount(GuildId g, int s) { return 0; }
        public void sendFlower(GuildId g, int s, PlayerRef sender) {}
        public int getTodayFlowerCount(GuildId g, int s) { return 0; }
        public double getPopularity(GuildId g, int s) { return 0; }
        public boolean hasSentFlowerToday(GuildId g, int s, PlayerRef sender) { return false; }
        public long getLastMoveTime(java.util.UUID playerUuid) { return 0; }
        public void recordMove(java.util.UUID playerUuid, long timestamp) {}
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
        final List<ChunkRegion> roadCalls = new ArrayList<>();
        public void prepare(String worldName, ChunkRegion region, TerrainPrepMode mode) {
            calls.add(new PrepCall(worldName, region, mode));
        }
        public void surfaceRoad(String worldName, ChunkRegion region, org.windy.guildshelter.domain.layout.RoadMask roadMask) {
            roadCalls.add(region);
        }
    }
}
