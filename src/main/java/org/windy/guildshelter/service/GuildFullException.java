package org.windy.guildshelter.service;

import org.windy.guildshelter.domain.model.GuildId;

/**
 * 公会成员名额已满（已分配庄园数达到当前公会等级的容量）时抛出。
 * 上层应捕获并提示"公会已满，需先升级公会"，而不是当成内部错误。
 */
public final class GuildFullException extends RuntimeException {

    private final transient GuildId guild;
    private final int capacity;
    private final int guildLevel;

    public GuildFullException(GuildId guild, int capacity, int guildLevel) {
        super("公会 " + guild.value() + " 名额已满（" + capacity + " 人，等级 " + guildLevel + "）");
        this.guild = guild;
        this.capacity = capacity;
        this.guildLevel = guildLevel;
    }

    public GuildId guild() {
        return guild;
    }

    public int capacity() {
        return capacity;
    }

    public int guildLevel() {
        return guildLevel;
    }
}
