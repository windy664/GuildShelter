package org.windy.guildshelter.database.mysql;

// 用来返回包含区域内信息的类
public class AreaInfo {
    private boolean isInArea;
    private String member;
    private String title;
    private String guildname;
    private String world;
    private String flag;
    private String message;
    private String owner;  // 新添加的字段

    public AreaInfo(boolean isInArea, String member, String title, String guildname, String world, String flag,
                    String message, String owner) {
        this.isInArea = isInArea;
        this.member = member;
        this.title = title;
        this.guildname = guildname;
        this.world = world;
        this.flag = flag;
        this.message = message;
        this.owner = owner;  // 新的字段赋值
    }

    // Getter methods
    public boolean isInArea() {
        return isInArea;
    }

    public String getMember() {
        return member;
    }

    public String getTitle() {
        return title;
    }

    public String getGuildname() {
        return guildname;
    }

    public String getWorld() {
        return world;
    }

    public String getFlag() {
        return flag;
    }

    public String getMessage() {
        return message;
    }

    public String getOwner() {
        return owner;  // 新增getter方法
    }
}
