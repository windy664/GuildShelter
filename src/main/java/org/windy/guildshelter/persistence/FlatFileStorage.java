package org.windy.guildshelter.persistence;

import org.windy.guildshelter.domain.layout.LayoutConfig;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.GuildWorld;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;
import org.windy.guildshelter.domain.model.TerrainPrepMode;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;
import org.windy.guildshelter.domain.port.ManorRepository.CommentEntry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 平铺文件存储后端（不用数据库的用户）：两份 TSV 文件 + 内存缓存，每次变更整文件重写。
 * 纯 Java(不碰 Bukkit)，数据量小，写法简单可靠。制表符/换行在写入时被替换为空格以防串行。
 */
public final class FlatFileStorage implements Storage {

    private final FileGuildRepo guilds;
    private final FileManorRepo manors;

    public FlatFileStorage(Path dir, LayoutConfig fallbackLayout) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new PersistenceException("创建数据目录失败: " + dir, e);
        }
        this.guilds = new FileGuildRepo(dir.resolve("guilds.tsv"), fallbackLayout);
        this.manors = new FileManorRepo(dir.resolve("manors.tsv"));
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
        // 内存即缓存，变更已即时落盘，无需额外动作。
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static List<String> readLines(Path file) {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeLines(Path file, List<String> lines) {
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PersistenceException("写入文件失败: " + file, e);
        }
    }

    // ---- guild_world ----
    static final class FileGuildRepo implements GuildRepository {
        private final Path file;
        private final LayoutConfig fallback;
        private final Map<String, GuildWorld> byId = new LinkedHashMap<>();

        FileGuildRepo(Path file, LayoutConfig fallback) {
            this.file = file;
            this.fallback = fallback;
            for (String line : readLines(file)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] f = line.split("\t", -1);
                GuildId g = new GuildId(f[0]);
                TerrainPrepMode mode = TerrainPrepMode.CLEAR_VEGETATION;
                if (f.length > 10 && !f[10].isBlank()) {
                    try { mode = TerrainPrepMode.valueOf(f[10]); } catch (IllegalArgumentException ignored) {}
                }
                String serverName = f.length > 11 ? f[11] : "";
                byId.put(g.value(), new GuildWorld(g, f[1], Long.parseLong(f[2]),
                        Integer.parseInt(f[3]), Integer.parseInt(f[4]),
                        Integer.parseInt(f[5]), Integer.parseInt(f[6]),
                        LayoutCsv.parse(f.length > 7 ? f[7] : null, fallback),
                        f.length > 8 ? Double.parseDouble(f[8]) : 0,
                        f.length > 9 ? f[9] : "",
                        mode, serverName));
            }
        }

        @Override
        public Optional<GuildWorld> find(GuildId guild) {
            return Optional.ofNullable(byId.get(guild.value()));
        }

        @Override
        public boolean exists(GuildId guild) {
            return byId.containsKey(guild.value());
        }

        @Override
        public void save(GuildWorld world) {
            byId.put(world.guild().value(), world);
            persist();
        }

        @Override
        public void delete(GuildId guild) {
            if (byId.remove(guild.value()) != null) {
                persist();
            }
        }

        @Override
        public List<GuildWorld> findAll() {
            return new ArrayList<>(byId.values());
        }

        private void persist() {
            List<String> lines = new ArrayList<>();
            for (GuildWorld w : byId.values()) {
                lines.add(String.join("\t",
                        clean(w.guild().value()), clean(w.worldName()), Long.toString(w.seed()),
                        Integer.toString(w.originChunkX()), Integer.toString(w.originChunkZ()),
                        Integer.toString(w.guildLevel()), Integer.toString(w.allocatedSlots()),
                        LayoutCsv.toCsv(w.layout()), Double.toString(w.funds()),
                        clean(w.bulletin()), w.terrainMode().name(),
                        clean(w.serverName())));
            }
            writeLines(file, lines);
        }
    }

    // ---- manor (+ 共建人内联为最后一列, 逗号分隔) ----
    static final class FileManorRepo implements ManorRepository {
        private final Path file;
        private final Map<String, Manor> bySlot = new LinkedHashMap<>();

        FileManorRepo(Path file) {
            this.file = file;
            for (String line : readLines(file)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] f = line.split("\t", -1);
                GuildId g = new GuildId(f[0]);
                int slot = Integer.parseInt(f[1]);
                PlayerRef owner = PlayerRef.of(UUID.fromString(f[2]));
                int level = Integer.parseInt(f[3]);
                Set<PlayerRef> co = parseUuidSet(f.length > 4 ? f[4] : null);
                Map<String, String> flags = FlagsCsv.parse(f.length > 5 ? f[5] : null);
                // members/denied 追加在 flags 之后(列 6/7);旧文件无这两列→空集。
                Set<PlayerRef> members = parseUuidSet(f.length > 6 ? f[6] : null);
                Set<PlayerRef> denied = parseUuidSet(f.length > 7 ? f[7] : null);
                bySlot.put(key(g, slot), new Manor(slot, g, owner, level, co, members, denied, flags));
            }
        }

        private static String key(GuildId g, int slot) {
            return g.value() + "#" + slot;
        }

        @Override
        public Optional<Manor> findBySlot(GuildId guild, int slot) {
            return Optional.ofNullable(bySlot.get(key(guild, slot)));
        }

        @Override
        public Optional<Manor> findByOwner(GuildId guild, PlayerRef owner) {
            return bySlot.values().stream()
                    .filter(m -> m.guild().equals(guild) && m.owner().equals(owner)).findFirst();
        }

        @Override
        public Optional<Manor> findByOwnerAnywhere(PlayerRef owner) {
            return bySlot.values().stream().filter(m -> m.owner().equals(owner)).findFirst();
        }

        @Override
        public List<Manor> findAll(GuildId guild) {
            List<Manor> out = new ArrayList<>();
            for (Manor m : bySlot.values()) {
                if (m.guild().equals(guild)) {
                    out.add(m);
                }
            }
            return out;
        }

        @Override
        public void save(Manor manor) {
            bySlot.put(key(manor.guild(), manor.slot()), manor);
            persist();
        }

        @Override
        public void delete(GuildId guild, int slot) {
            if (bySlot.remove(key(guild, slot)) != null) {
                persist();
            }
        }

        @Override
        public int nextFreeSlot(GuildId guild) {
            Set<Integer> used = new HashSet<>();
            for (Manor m : bySlot.values()) {
                if (m.guild().equals(guild)) {
                    used.add(m.slot());
                }
            }
            int expected = 0;
            while (used.contains(expected)) {
                expected++;
            }
            return expected;
        }

        private void persist() {
            List<String> lines = new ArrayList<>();
            for (Manor m : bySlot.values()) {
                // 列序: guild slot owner level coBuilders flags members denied
                // (members/denied 追加在 flags 后, 保持旧列索引不变=向后兼容)
                lines.add(String.join("\t",
                        clean(m.guild().value()), Integer.toString(m.slot()),
                        m.owner().uuid().toString(), Integer.toString(m.level()),
                        joinUuids(m.coBuilders()), FlagsCsv.toCsv(m.flags()),
                        joinUuids(m.members()), joinUuids(m.denied())));
            }
            writeLines(file, lines);
        }

        private static Set<PlayerRef> parseUuidSet(String csv) {
            Set<PlayerRef> out = new HashSet<>();
            if (csv != null && !csv.isBlank()) {
                for (String u : csv.split(",")) {
                    out.add(PlayerRef.of(UUID.fromString(u.trim())));
                }
            }
            return out;
        }

        private static String joinUuids(Set<PlayerRef> players) {
            StringBuilder sb = new StringBuilder();
            for (PlayerRef p : players) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(p.uuid());
            }
            return sb.toString();
        }

        // ===== 访问统计（内存存储，重启丢失）=====
        private final Map<String, Integer> visits = new LinkedHashMap<>();

        @Override
        public void incrementVisit(GuildId guild, int slot) {
            visits.merge(rateKey(guild, slot), 1, Integer::sum);
        }

        @Override
        public int getVisitCount(GuildId guild, int slot) {
            return visits.getOrDefault(rateKey(guild, slot), 0);
        }

        // ===== 评分/留言/合并：平铺文件版用内存存储（重启丢失，原型够用）=====
        private final Map<String, Map<String, Integer>> ratings = new LinkedHashMap<>();
        private final List<CommentEntry> comments = new ArrayList<>();
        private final Map<String, Map<Integer, Integer>> merges = new LinkedHashMap<>(); // guild → absorbed→primary

        private static String rateKey(GuildId g, int slot) { return g.value() + "#" + slot; }

        @Override
        public void rate(GuildId guild, int slot, PlayerRef rater, int score) {
            ratings.computeIfAbsent(rateKey(guild, slot), k -> new LinkedHashMap<>())
                    .put(rater.uuid().toString(), score);
        }

        @Override
        public int getRating(GuildId guild, int slot, PlayerRef rater) {
            Map<String, Integer> m = ratings.get(rateKey(guild, slot));
            return m != null ? m.getOrDefault(rater.uuid().toString(), 0) : 0;
        }

        @Override
        public double getAverageRating(GuildId guild, int slot) {
            Map<String, Integer> m = ratings.get(rateKey(guild, slot));
            if (m == null || m.isEmpty()) return 0;
            return m.values().stream().mapToInt(Integer::intValue).average().orElse(0);
        }

        @Override
        public List<Integer> getTopRatedSlots(GuildId guild, int limit) {
            return ratings.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(guild.value() + "#"))
                    .sorted((a, b) -> Double.compare(
                            b.getValue().values().stream().mapToInt(Integer::intValue).average().orElse(0),
                            a.getValue().values().stream().mapToInt(Integer::intValue).average().orElse(0)))
                    .limit(limit)
                    .map(e -> Integer.parseInt(e.getKey().split("#")[1]))
                    .toList();
        }

        @Override
        public int getRatingCount(GuildId guild, int slot) {
            Map<String, Integer> m = ratings.get(rateKey(guild, slot));
            return m != null ? m.size() : 0;
        }

        @Override
        public void addComment(GuildId guild, int slot, PlayerRef author, String message) {
            comments.add(new CommentEntry(guild, slot, author, message, System.currentTimeMillis()));
        }

        @Override
        public List<CommentEntry> getComments(GuildId guild, int slot, int limit) {
            return comments.stream()
                    .filter(c -> c.guild().equals(guild) && c.slot() == slot)
                    .sorted((a, b) -> Long.compare(b.timestamp(), a.timestamp()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<CommentEntry> getInbox(PlayerRef owner, int limit) {
            Set<String> ownedSlots = new HashSet<>();
            for (Manor m : bySlot.values()) {
                if (m.owner().equals(owner)) {
                    ownedSlots.add(rateKey(m.guild(), m.slot()));
                }
            }
            return comments.stream()
                    .filter(c -> ownedSlots.contains(rateKey(c.guild(), c.slot())))
                    .sorted((a, b) -> Long.compare(b.timestamp(), a.timestamp()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void merge(int primarySlot, int absorbedSlot, GuildId guild) {
            merges.computeIfAbsent(guild.value(), k -> new LinkedHashMap<>())
                    .put(absorbedSlot, primarySlot);
        }

        @Override
        public int getMergedTarget(GuildId guild, int slot) {
            Map<Integer, Integer> m = merges.get(guild.value());
            return m != null ? m.getOrDefault(slot, slot) : slot;
        }

        @Override
        public List<Integer> getMergedSlots(GuildId guild, int primarySlot) {
            Map<Integer, Integer> m = merges.get(guild.value());
            if (m == null) return List.of();
            return m.entrySet().stream()
                    .filter(e -> e.getValue() == primarySlot)
                    .map(Map.Entry::getKey)
                    .toList();
        }

        @Override
        public void unmerge(GuildId guild, int primarySlot) {
            Map<Integer, Integer> m = merges.get(guild.value());
            if (m != null) {
                m.values().removeIf(v -> v == primarySlot);
            }
        }

        @Override
        public void unmergeOne(GuildId guild, int primarySlot, int absorbedSlot) {
            Map<Integer, Integer> m = merges.get(guild.value());
            if (m != null) {
                m.remove(absorbedSlot, primarySlot);
            }
        }

        // ===== 权限模板（内存存储，重启丢失）=====
        private final Map<String, Map<String, Map<String, String>>> templates = new LinkedHashMap<>();

        @Override
        public void saveTemplate(GuildId guild, String name, Map<String, String> flags) {
            templates.computeIfAbsent(guild.value(), k -> new LinkedHashMap<>())
                    .put(name, Map.copyOf(flags));
        }

        @Override
        public void deleteTemplate(GuildId guild, String name) {
            Map<String, Map<String, String>> g = templates.get(guild.value());
            if (g != null) g.remove(name);
        }

        @Override
        public Optional<Map<String, String>> getTemplate(GuildId guild, String name) {
            Map<String, Map<String, String>> g = templates.get(guild.value());
            return g != null ? Optional.ofNullable(g.get(name)) : Optional.empty();
        }

        @Override
        public List<String> listTemplates(GuildId guild) {
            Map<String, Map<String, String>> g = templates.get(guild.value());
            return g != null ? new ArrayList<>(g.keySet()) : List.of();
        }

        // ===== 子领地（内存存储）=====
        private final Map<String, List<SubEntry>> subs = new LinkedHashMap<>();

        @Override
        public void saveSub(GuildId guild, int slot, String name, int minX, int minZ, int maxX, int maxZ, Map<String, String> flags) {
            String key = guild.value() + "#" + slot;
            List<SubEntry> list = subs.computeIfAbsent(key, k -> new ArrayList<>());
            list.removeIf(s -> s.name().equals(name));
            list.add(new SubEntry(guild, slot, name, minX, minZ, maxX, maxZ, Map.copyOf(flags)));
        }

        @Override
        public void deleteSub(GuildId guild, int slot, String name) {
            String key = guild.value() + "#" + slot;
            List<SubEntry> list = subs.get(key);
            if (list != null) list.removeIf(s -> s.name().equals(name));
        }

        @Override
        public List<SubEntry> getSubs(GuildId guild, int slot) {
            String key = guild.value() + "#" + slot;
            return subs.getOrDefault(key, List.of());
        }
    }
}
