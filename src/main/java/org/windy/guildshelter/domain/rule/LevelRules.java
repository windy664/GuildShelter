package org.windy.guildshelter.domain.rule;

/**
 * 两套<b>互相独立</b>的等级规则（纯逻辑，配置驱动）：
 *
 * <ul>
 *   <li><b>庄园等级</b>：成员个人的事——成员自行把自己那块庄园从初始升到满级，
 *       只受物理满级 {@code manorMaxLevelCap} 限制，<b>不</b>受公会等级门控。</li>
 *   <li><b>公会等级</b>：容量的事——公会升级 → 放开更多成员名额 {@code membersPerGuildLevel}，
 *       边界随之扩大，从而能容纳/加入更多新成员。</li>
 * </ul>
 *
 * @param maxGuildLevel        公会最高等级
 * @param membersPerGuildLevel 公会每升 1 级放开多少成员名额（容量 = 等级 × 本值）
 * @param manorMaxLevelCap     庄园等级绝对上限（= 庄园从初始涨到物理满级所需的级数）
 */
public record LevelRules(int maxGuildLevel, int membersPerGuildLevel, int manorMaxLevelCap) {

    public LevelRules {
        if (maxGuildLevel < 1 || membersPerGuildLevel < 1 || manorMaxLevelCap < 1) {
            throw new IllegalArgumentException("等级规则参数必须 ≥1");
        }
    }

    /** 给定公会等级时允许的成员名额（容量）。公会升级让这个数变大 → 能进更多人。 */
    public int maxMembers(int guildLevel) {
        if (guildLevel < 1) {
            throw new IllegalArgumentException("guildLevel 必须 ≥1");
        }
        return guildLevel * membersPerGuildLevel;
    }

    /** 庄园物理满级（与公会等级无关）。 */
    public int manorMaxLevel() {
        return manorMaxLevelCap;
    }

    /** 庄园能否再升一级——只看是否已达物理满级，成员自己的事。 */
    public boolean canUpgradeManor(int currentManorLevel) {
        return currentManorLevel < manorMaxLevelCap;
    }

    public boolean canUpgradeGuild(int currentGuildLevel) {
        return currentGuildLevel < maxGuildLevel;
    }

    /** 默认：公会 5 级、每级放开 5 个成员名额（满级 25 人）、庄园物理满级 5。 */
    public static LevelRules defaults() {
        return new LevelRules(5, 5, 5);
    }
}
