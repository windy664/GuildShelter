package org.windy.guildshelter.domain.rule.quota;

import java.util.Locale;

/**
 * 机器配额维度：以方块实体的命名空间 id 为 key（如 {@code "minecraft:blast_furnace"}）。
 *
 * <p>机器无玩家自调 flag。增量 flag 键把 id 里的 {@code ':' '/'} 等清洗成 {@code '_'}，避免破坏 CSV 持久化。
 */
public record MachineKey(String id) implements ManorQuotaKey {

    public MachineKey {
        id = id == null ? "" : id.toLowerCase(Locale.ROOT);
    }

    @Override
    public String bonusFlagKey() {
        return "_mlim_" + id.replaceAll("[^a-z0-9]", "_");
    }
}
