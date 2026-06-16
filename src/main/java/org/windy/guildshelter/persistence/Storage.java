package org.windy.guildshelter.persistence;

import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;

/**
 * 一个存储后端：对外只暴露两个领域端口仓库 + 关闭。具体是 SQLite / MySQL / 平铺文件由实现决定，
 * 领域与服务层只认 {@link GuildRepository}/{@link ManorRepository}，对后端无感。
 */
public interface Storage extends AutoCloseable {

    GuildRepository guilds();

    ManorRepository manors();

    @Override
    void close();
}
