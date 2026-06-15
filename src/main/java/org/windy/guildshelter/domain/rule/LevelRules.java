package org.windy.guildshelter.domain.rule;

/**
 * 两套等级的规则（纯逻辑，配置驱动）。庄园等级受公会等级封顶。
 *
 * @param maxGuildLevel          公会最高等级
 * @param manorLevelsPerGuildLevel 公会每级放开多少庄园等级
 * @param manorMaxLevelCap       庄园等级绝对上限（不超过 LayoutConfig 能扩到满级所需的级数）
 */
public record LevelRules(int maxGuildLevel, int manorLevelsPerGuildLevel, int manorMaxLevelCap) {

    public LevelRules {
        if (maxGuildLevel < 1 || manorLevelsPerGuildLevel < 1 || manorMaxLevelCap < 1) {
            throw new IllegalArgumentException("等级规则参数必须 ≥1");
        }
    }

    /** 给定公会等级时，庄园允许达到的最高等级。 */
    public int maxManorLevel(int guildLevel) {
        if (guildLevel < 1) {
            throw new IllegalArgumentException("guildLevel 必须 ≥1");
        }
        return Math.min(guildLevel * manorLevelsPerGuildLevel, manorMaxLevelCap);
    }

    public boolean canUpgradeManor(int currentManorLevel, int guildLevel) {
        return currentManorLevel < maxManorLevel(guildLevel);
    }

    public boolean canUpgradeGuild(int currentGuildLevel) {
        return currentGuildLevel < maxGuildLevel;
    }

    /** 默认：公会 10 级，每级放开 1 庄园等级，庄园最高 10 级。 */
    public static LevelRules defaults() {
        return new LevelRules(10, 1, 10);
    }
}
