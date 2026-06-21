package org.windy.guildshelter.adapter.bukkit.holo;

import org.bukkit.Location;

import java.util.List;

/**
 * 悬浮字后端抽象：把"创建/删除/移动悬浮字"与具体插件解耦。当前唯一实现是
 * {@link DecentHologramsBackend}（DecentHolograms 软依赖）；DH 不在则整个主城悬浮字功能静默关闭。
 *
 * <p>本接口在<b>本插件</b>命名空间，引用它不会触发任何 DH 类加载；DH 类仅出现在
 * {@link DecentHologramsBackend} 内部，该实现只在检测到 DH 插件时才被构造，故 DH 缺席零副作用。
 */
public interface HologramBackend {

    /** 后端是否可用（DH 已加载）。 */
    boolean available();

    /** 在 {@code loc} 创建名为 {@code name} 的悬浮字（持久化由后端负责）。成功返回 true。 */
    boolean create(String name, Location loc, List<String> lines);

    /** 删除悬浮字（不存在则静默）。 */
    void remove(String name);

    /** 把悬浮字移到 {@code loc}；不存在返回 false。 */
    boolean move(String name, Location loc);

    /** 该名字的悬浮字当前是否存在于后端。 */
    boolean exists(String name);

    /** DH 缺席时用的空实现：{@link #available()} 恒 false，其余皆为空操作。 */
    HologramBackend NOOP = new HologramBackend() {
        @Override public boolean available() { return false; }
        @Override public boolean create(String name, Location loc, List<String> lines) { return false; }
        @Override public void remove(String name) { }
        @Override public boolean move(String name, Location loc) { return false; }
        @Override public boolean exists(String name) { return false; }
    };
}
