package org.windy.guildshelter.database;

public class CenterData {
    private int id;
    private int x1, z1, x2, z2;  // 坐标范围
    private String member;         // 成员
    private String title;          // 欢迎语
    private String guild;          // 公会名称
    private String world;          // 所属世界
    private String flag;           // 标志

    // 构造函数，初始化所有字段
    public CenterData(int id, int x1, int z1, int x2, int z2, String member, String title, String guild, String world, String flag) {
        this.id = id;
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
        this.member = member;
        this.title = title;
        this.guild = guild;
        this.world = world;
        this.flag = flag;
    }

    // 获取 id
    public int getId() {
        return id;
    }

    // 设置 id
    public void setId(int id) {
        this.id = id;
    }

    // 获取 x1 坐标
    public int getX1() {
        return x1;
    }

    // 设置 x1 坐标
    public void setX1(int x1) {
        this.x1 = x1;
    }

    // 获取 z1 坐标
    public int getZ1() {
        return z1;
    }

    // 设置 z1 坐标
    public void setZ1(int z1) {
        this.z1 = z1;
    }

    // 获取 x2 坐标
    public int getX2() {
        return x2;
    }

    // 设置 x2 坐标
    public void setX2(int x2) {
        this.x2 = x2;
    }

    // 获取 z2 坐标
    public int getZ2() {
        return z2;
    }

    // 设置 z2 坐标
    public void setZ2(int z2) {
        this.z2 = z2;
    }

    // 获取成员
    public String getMember() {
        return member;
    }

    // 设置成员
    public void setMember(String member) {
        this.member = member;
    }

    // 获取欢迎语
    public String getTitle() {
        return title;
    }

    // 设置欢迎语
    public void setTitle(String title) {
        this.title = title;
    }

    // 获取公会名称
    public String getGuild() {
        return guild;
    }

    // 设置公会名称
    public void setGuild(String guild) {
        this.guild = guild;
    }

    // 获取世界名称
    public String getWorld() {
        return world;
    }

    // 设置世界名称
    public void setWorld(String world) {
        this.world = world;
    }

    // 获取标志
    public String getFlag() {
        return flag;
    }

    // 设置标志
    public void setFlag(String flag) {
        this.flag = flag;
    }

    // 输出 CenterData 的详细信息
    @Override
    public String toString() {
        return "CenterData{" +
                "id=" + id +
                ", x1=" + x1 +
                ", z1=" + z1 +
                ", x2=" + x2 +
                ", z2=" + z2 +
                ", member='" + member + '\'' +
                ", title='" + title + '\'' +
                ", guild='" + guild + '\'' +
                ", world='" + world + '\'' +
                ", flag='" + flag + '\'' +
                '}';
    }
}
