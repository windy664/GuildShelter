package org.windy.guildshelter.adapter.bukkit;

import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.port.ManorRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * 访问计数缓冲：内存累加，定时批量刷盘。
 * 避免每次进庄园都写库（高流量服务器上 DB 写入风暴）。
 *
 * <p>key = "guildId:slot"，value = 累计访问次数。
 * 每 {@code flushIntervalMs} 毫秒批量 UPDATE 到 DB，然后清零。
 */
public final class VisitCounter {

    private final ManorRepository manors;
    private final Logger logger;
    private final Map<String, AtomicInteger> buffer = new ConcurrentHashMap<>();

    public VisitCounter(ManorRepository manors, Logger logger) {
        this.manors = manors;
        this.logger = logger;
    }

    /** 访客进入庄园时调用（内存 +1，O(1)）。 */
    public void increment(GuildId guild, int slot) {
        String key = guild.value() + ":" + slot;
        buffer.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /** 定时调用：把缓冲区的计数批量写入 DB，然后清零。 */
    public void flush() {
        if (buffer.isEmpty()) return;
        // 取走当前缓冲区，清空（避免 flush 期间阻塞新的 increment）
        Map<String, AtomicInteger> snapshot = new ConcurrentHashMap<>();
        buffer.forEach((k, v) -> {
            int val = v.getAndSet(0);
            if (val > 0) snapshot.put(k, new AtomicInteger(val));
        });
        if (snapshot.isEmpty()) return;

        int count = 0;
        for (var entry : snapshot.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            int inc = entry.getValue().get();
            if (inc <= 0) continue;
            try {
                GuildId guild = new GuildId(parts[0]);
                int slot = Integer.parseInt(parts[1]);
                manors.incrementVisitBy(guild, slot, inc); // 单条 SQL 原子 +N
                count++;
            } catch (Exception e) {
                logger.warning("[GuildShelter] 刷盘访问计数失败: " + entry.getKey() + " - " + e.getMessage());
            }
        }
        if (count > 0) {
            logger.fine("[GuildShelter] 访问计数刷盘: " + count + " 条");
        }
    }
}
