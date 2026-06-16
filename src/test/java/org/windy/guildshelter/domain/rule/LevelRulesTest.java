package org.windy.guildshelter.domain.rule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelRulesTest {

    private final LevelRules rules = LevelRules.defaults(); // 公会5, 每级5名额, 庄园上限5

    @Test
    void maxMembersScalesWithGuildLevel() {
        assertEquals(5, rules.maxMembers(1));
        assertEquals(10, rules.maxMembers(2));
        assertEquals(25, rules.maxMembers(5));
    }

    @Test
    void manorUpgradeOnlyGatedByPhysicalCap() {
        // 庄园升级与公会等级无关，只看是否到物理满级（默认 5）。
        assertEquals(5, rules.manorMaxLevel());
        assertTrue(rules.canUpgradeManor(1));
        assertTrue(rules.canUpgradeManor(4));
        assertFalse(rules.canUpgradeManor(5)); // 已满级
    }

    @Test
    void guildUpgradeBoundary() {
        assertTrue(rules.canUpgradeGuild(4));
        assertFalse(rules.canUpgradeGuild(5));
    }

    @Test
    void customCapacityCurve() {
        LevelRules r = new LevelRules(10, 8, 6); // 公会10级, 每级8名额, 庄园上限6
        assertEquals(8, r.maxMembers(1));
        assertEquals(80, r.maxMembers(10));
        assertEquals(6, r.manorMaxLevel());
        assertFalse(r.canUpgradeManor(6));
    }
}
