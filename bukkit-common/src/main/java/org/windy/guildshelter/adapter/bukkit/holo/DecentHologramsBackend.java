package org.windy.guildshelter.adapter.bukkit.holo;

import eu.decentsoftware.holograms.api.DHAPI;
import org.bukkit.Location;

import java.util.List;

/**
 * {@link HologramBackend} 的 DecentHolograms 实现（软依赖，JitPack {@code com.github.decentsoftware-eu:decentholograms}）。
 *
 * <p><b>仅在检测到 DecentHolograms 插件时才构造本类</b>（见 {@code GuildShelterPlugin}），否则用
 * {@link HologramBackend#NOOP}——这样 DH 缺席时本类永不被类加载，不会 NoClassDefFoundError。
 *
 * <p>用 {@code saveToFile=true} 让 DH 自己持久化悬浮字（重启自动恢复）；本插件只另存"哪些悬浮字属于哪个公会"
 * 的映射（{@link org.windy.guildshelter.domain.port.CityHologramStore}）用于限额/清理。
 */
public final class DecentHologramsBackend implements HologramBackend {

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean create(String name, Location loc, List<String> lines) {
        try {
            DHAPI.createHologram(name, loc, true, lines); // saveToFile=true → DH 持久化
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void remove(String name) {
        try {
            DHAPI.removeHologram(name);
        } catch (Throwable ignored) {
            // 不存在 / DH 异常：静默
        }
    }

    @Override
    public boolean move(String name, Location loc) {
        try {
            if (DHAPI.getHologram(name) == null) {
                return false;
            }
            DHAPI.moveHologram(name, loc);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean exists(String name) {
        try {
            return DHAPI.getHologram(name) != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
