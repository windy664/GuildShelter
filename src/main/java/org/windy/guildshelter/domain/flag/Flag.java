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
    PVE("pve", FlagType.BOOLEAN, "true", "玩家与怪物之间是否可互相伤害"),
    INVINCIBLE("invincible", FlagType.BOOLEAN, "false", "玩家在本地皮内是否免疫一切伤害"),
    KEEP_INVENTORY("keep-inventory", FlagType.BOOLEAN, "false", "在本地皮内死亡是否保留物品/经验"),
    ITEM_DROP("item-drop", FlagType.BOOLEAN, "true", "玩家是否可丢出物品"),
    INSTABREAK("instabreak", FlagType.BOOLEAN, "false", "本地皮内方块是否秒破"),
    MOB_PLACE("mob-place", FlagType.BOOLEAN, "true", "是否允许用刷怪蛋放置生物"),
    DENY_ENTRY("deny-entry", FlagType.BOOLEAN, "false", "是否禁止非成员进入本地皮"),
    DENY_EXIT("deny-exit", FlagType.BOOLEAN, "false", "是否禁止非成员离开本地皮(困住)"),
    GREETING("greeting", FlagType.STRING, "", "进入本地皮时显示的消息(空=无,&颜色码)"),
    FAREWELL("farewell", FlagType.STRING, "", "离开本地皮时显示的消息(空=无,&颜色码)"),
    TITLES("titles", FlagType.BOOLEAN, "false", "进出消息用标题(屏幕中央)显示而非聊天框"),
    NOTIFY_ENTER("notify-enter", FlagType.BOOLEAN, "false", "有人进入本地皮时通知在线成员(庄主/共建人)"),
    NOTIFY_LEAVE("notify-leave", FlagType.BOOLEAN, "false", "有人离开本地皮时通知在线成员(庄主/共建人)"),
    FLY("fly", FlagType.BOOLEAN, "false", "在本地皮内是否允许飞行"),
    FEED("feed", FlagType.BOOLEAN, "false", "在本地皮内是否保持饱食"),
    HEAL("heal", FlagType.BOOLEAN, "false", "在本地皮内是否缓慢恢复生命"),

    // --- 交互/访客组(默认 false=严格,庄主按需开放给访客;成员/管理始终放行)---
    USE("use", FlagType.BOOLEAN, "false", "访客是否可使用门/按钮/拉杆/压力板/告示牌等(无库存交互)"),
    CONTAINER("container", FlagType.BOOLEAN, "false", "访客是否可打开箱子/熔炉/漏斗/酿造台等带库存方块"),
    ITEM_FRAME("item-frame", FlagType.BOOLEAN, "false", "访客是否可与展示框/盔甲架交互(旋转·取放·破坏)"),
    VEHICLE_USE("vehicle-use", FlagType.BOOLEAN, "false", "访客是否可乘坐/破坏船与矿车(放置走方块保护)"),

    // --- 方块环境组(默认 true=保持原版,设 false 即冻结该机制)---
    REDSTONE("redstone", FlagType.BOOLEAN, "true", "红石是否工作"),
    LIQUID_FLOW("liquid-flow", FlagType.BOOLEAN, "true", "水/岩浆是否流动"),
    CROP_GROW("crop-grow", FlagType.BOOLEAN, "true", "作物/树苗/甘蔗/海带等是否生长"),
    GRASS_GROW("grass-grow", FlagType.BOOLEAN, "true", "草方块是否蔓延"),
    VINE_GROW("vine-grow", FlagType.BOOLEAN, "true", "藤蔓/地衣是否蔓延"),
    MYCELIUM_GROW("mycelium-grow", FlagType.BOOLEAN, "true", "菌丝是否蔓延"),
    ICE_FORM("ice-form", FlagType.BOOLEAN, "true", "是否结冰"),
    ICE_MELT("ice-melt", FlagType.BOOLEAN, "true", "冰是否融化"),
    SNOW_FORM("snow-form", FlagType.BOOLEAN, "true", "是否积雪"),
    SNOW_MELT("snow-melt", FlagType.BOOLEAN, "true", "雪是否融化"),
    LEAF_DECAY("leaf-decay", FlagType.BOOLEAN, "true", "树叶是否凋落"),

    // --- 实体数量上限组(INTEGER,默认 -1=无限;实时扫描地皮范围,达上限即拦新生成)---
    ANIMAL_CAP("animal-cap", FlagType.INTEGER, "-1", "本地皮被动生物(动物)数量上限,-1=无限"),
    HOSTILE_CAP("hostile-cap", FlagType.INTEGER, "-1", "本地皮敌对生物数量上限,-1=无限"),
    MOB_CAP("mob-cap", FlagType.INTEGER, "-1", "本地皮生物总数(动物+敌对+其它)上限,-1=无限"),
    VEHICLE_CAP("vehicle-cap", FlagType.INTEGER, "-1", "本地皮载具(船/矿车)数量上限,-1=无限"),

    // --- 经济杂项组 ---
    DESCRIPTION("description", FlagType.STRING, "", "地皮描述(显示在 /gs info)"),
    BLOCKED_CMDS("blocked-cmds", FlagType.STRING, "", "本地皮内禁止使用的命令(逗号分隔,不含/;如 spawn,tp,home)"),
    KEEP("keep", FlagType.BOOLEAN, "false", "庄主退会时是否保留地皮不清扫"),
    PRICE("price", FlagType.DOUBLE, "0", "访客进入本地皮需支付的费用(需 Vault;0=免费)"),

    // --- 地皮管理组 ---
    ALIAS("alias", FlagType.STRING, "", "地皮别名(显示在 /gs info 和家园卡)"),
    HOME_X("home-x", FlagType.INTEGER, "0", "/gs home 传送点 X 坐标(0=使用地皮中心)"),
    HOME_Y("home-y", FlagType.INTEGER, "0", "/gs home 传送点 Y 坐标"),
    HOME_Z("home-z", FlagType.INTEGER, "0", "/gs home 传送点 Z 坐标"),
    DONE("done", FlagType.BOOLEAN, "false", "地皮是否已完工标记");

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

    /** 解析整数值；未设/非法返回默认（默认也非法则 -1）。用于 INTEGER 型(如 *-cap)。 */
    public int resolveInt(Map<String, String> flags) {
        String v = flags.getOrDefault(id, defaultValue);
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            try {
                return Integer.parseInt(defaultValue.trim());
            } catch (NumberFormatException ex) {
                return -1;
            }
        }
    }

    /** 解析字符串值；未设返回默认。 */
    public String resolveString(Map<String, String> flags) {
        String v = flags.get(id);
        return v == null ? defaultValue : v;
    }

    /** 解析浮点值；未设/非法返回默认。用于 DOUBLE 型(如 price)。 */
    public double resolveDouble(Map<String, String> flags) {
        String v = flags.getOrDefault(id, defaultValue);
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            try {
                return Double.parseDouble(defaultValue.trim());
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
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
        if (type == FlagType.DOUBLE) {
            try {
                double d = Double.parseDouble(raw.trim());
                return Optional.of(Double.toString(d));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.of(raw.replace(';', ',')); // STRING：分号是 flag 存储分隔符，替成逗号避免串行
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
