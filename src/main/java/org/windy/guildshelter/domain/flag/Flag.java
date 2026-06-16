package org.windy.guildshelter.domain.flag;

import java.util.Map;
import java.util.Optional;

/**
 * 地皮 flag 的注册表(枚举即注册)。每个 flag 有 id、类型、默认值、说明。
 * v1 全为 BOOLEAN;解析时取地皮已设的值,没设则用默认。仿 PlotSquared 的 flag 体系,后续按需扩类型。
 */
public enum Flag {

    PVP("pvp", FlagType.BOOLEAN, "false", "玩家间是否可互相伤害"),
    MOB_SPAWN("mob-spawn", FlagType.BOOLEAN, "true", "怪物是否自然生成"),
    EXPLOSION("explosion", FlagType.BOOLEAN, "false", "爆炸是否破坏方块"),
    FIRE_SPREAD("fire-spread", FlagType.BOOLEAN, "false", "火是否蔓延/烧毁方块"),
    MOB_GRIEFING("mob-griefing", FlagType.BOOLEAN, "false", "怪物是否能破坏方块(苦力怕/末影人等)"),
    DENY_ENTRY("deny-entry", FlagType.BOOLEAN, "false", "是否禁止非成员进入本地皮"),
    GREETING("greeting", FlagType.STRING, "", "进入本地皮时显示的消息(空=无,&颜色码)"),
    FAREWELL("farewell", FlagType.STRING, "", "离开本地皮时显示的消息(空=无,&颜色码)");

    private final String id;
    private final FlagType type;
    private final String defaultValue;
    private final String description;

    Flag(String id, FlagType type, String defaultValue, String description) {
        this.id = id;
        this.type = type;
        this.defaultValue = defaultValue;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public FlagType type() {
        return type;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public String description() {
        return description;
    }

    /** 解析该地皮 flags 里本 flag 的布尔值；未设返回默认。 */
    public boolean resolveBool(Map<String, String> flags) {
        String v = flags.get(id);
        return v == null ? Boolean.parseBoolean(defaultValue) : Boolean.parseBoolean(v);
    }

    /** 校验并归一化一个待写入的值(布尔型只认 true/false)。非法返回 empty。 */
    public Optional<String> normalize(String raw) {
        if (type == FlagType.BOOLEAN) {
            if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
                return Optional.of(raw.toLowerCase());
            }
            return Optional.empty();
        }
        if (type == FlagType.INTEGER) {
            try {
                return Optional.of(Integer.toString(Integer.parseInt(raw.trim())));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.of(raw); // STRING
    }

    public static Optional<Flag> byId(String id) {
        for (Flag f : values()) {
            if (f.id.equalsIgnoreCase(id)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }
}
