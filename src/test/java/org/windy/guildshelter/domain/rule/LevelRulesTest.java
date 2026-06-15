package org.windy.guildshelter.domain.rule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelRulesTest {

    private final LevelRules rules = LevelRules.defaults(); // 公会10, 每级1, 庄园上限10

    @Test
    void maxManorLevelTracksGuildLevelThenCaps() {
        assertEquals(1, rules.maxManorLevel(1));
        assertEquals(5, rules.maxManorLevel(5));
        assertEquals(10, rules.maxManorLevel(10));
        assertEquals(10, rules.maxManorLevel(15)); // 封顶
    }

    @Test
    void manorUpgradeGatedByGuildLevel() {
        assertFalse(rules.canUpgradeManor(1, 1)); // 1级公会, 庄园上限1, 已满
        assertTrue(rules.canUpgradeManor(1, 2));
        assertFalse(rules.canUpgradeManor(10, 10));
    }

    @Test
    void guildUpgradeBoundary() {
        assertTrue(rules.canUpgradeGuild(9));
        assertFalse(rules.canUpgradeGuild(10));
    }

    @Test
    void customRatioAndCap() {
        LevelRules r = new LevelRules(5, 2, 8);
        assertEquals(6, r.maxManorLevel(3)); // 3*2=6
        assertEquals(8, r.maxManorLevel(5)); // 5*2=10 -> 封顶8
    }
}
