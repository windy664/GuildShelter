package org.windy.guildshelter.domain.rule;

import org.junit.jupiter.api.Test;
import org.windy.guildshelter.domain.layout.LayoutCalculator;
import org.windy.guildshelter.domain.layout.LayoutConfig;
import org.windy.guildshelter.domain.model.GuildId;
import org.windy.guildshelter.domain.model.Manor;
import org.windy.guildshelter.domain.model.PlayerRef;

import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionRulesTest {

    private final LayoutCalculator layout = new LayoutCalculator(LayoutConfig.defaults());
    private final PermissionRules rules = new PermissionRules(layout);

    private final GuildId guild = new GuildId("g1");
    private final PlayerRef owner = PlayerRef.of(UUID.randomUUID());
    private final PlayerRef other = PlayerRef.of(UUID.randomUUID());

    // slot0 地皮 chunk [10..13]×[-5..-2], 1级实占 [11..12]×[-4..-3]
    private IntFunction<Manor> only(int slot, Manor manor) {
        return s -> s == slot ? manor : null;
    }

    @Test
    void ownerCanBuildInActiveArea() {
        Manor m = Manor.create(0, guild, owner);
        assertTrue(rules.canModify(owner, true, only(0, m), 11, -4));
    }

    @Test
    void ownerCannotBuildInReservedButInactiveArea() {
        Manor m = Manor.create(0, guild, owner); // 1级, 实占只有中心2×2
        assertFalse(rules.canModify(owner, true, only(0, m), 10, -5)); // 在满级地皮内但未解锁
    }

    @Test
    void coBuilderCanBuildStrangerCannot() {
        Manor m = new Manor(0, guild, owner, 1, Set.of(other));
        assertTrue(rules.canModify(other, true, only(0, m), 11, -4)); // 共建人
        PlayerRef stranger = PlayerRef.of(UUID.randomUUID());
        assertFalse(rules.canModify(stranger, true, only(0, m), 11, -4));
    }

    @Test
    void nonGuildMemberDeniedEverywhere() {
        Manor m = Manor.create(0, guild, owner);
        assertFalse(rules.canModify(owner, false, only(0, m), 11, -4)); // 不在本公会
        assertFalse(rules.canModify(owner, false, only(0, m), 0, 0));   // 连主城也不行
    }

    @Test
    void guildMemberCanBuildInMainCity() {
        assertTrue(rules.canModify(owner, true, s -> null, 0, 0));
    }

    @Test
    void roadDenied() {
        assertFalse(rules.canModify(owner, true, s -> null, 14, -5));
    }

    @Test
    void unallocatedPlotDenied() {
        // slot1 地皮无庄园
        assertFalse(rules.canModify(owner, true, s -> null, 11, 1));
    }
}
