package org.windy.guildshelter.persistence;

import org.windy.guildshelter.domain.port.CityTrustStore;
import org.windy.guildshelter.domain.port.GuildRepository;
import org.windy.guildshelter.domain.port.ManorRepository;

/**
 * 一个存储后端：对外只暴露领域端口仓库 + 关闭。具体是 SQLite / MySQL / 平铺文件由实现决定，
 * 领域与服务层只认 {@link GuildRepository}/{@link ManorRepository}/{@link CityTrustStore}，对后端无感。
 */
public interface Storage extends AutoCloseable {

    GuildRepository guilds();

    ManorRepository manors();

    CityTrustStore cityTrust();

    @Override
    void close();
}
