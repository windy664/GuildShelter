package org.windy.guildshelter.database.sqlite;

public class GuildRegionData {

    private final int id;          // 区域的唯一标识符
    private final int x1;          // 区域左下角的 x 坐标
    private final int z1;          // 区域左下角的 z 坐标
    private final int x2;          // 区域右上角的 x 坐标
    private final int z2;          // 区域右上角的 z 坐标
    private final String guild;    // 公会名称
    private final String world;    // 所属世界

    // 构造方法
    public GuildRegionData(int id, int x1, int z1, int x2, int z2, String guild, String world) {
        this.id = id;
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
        this.guild = guild;
        this.world = world;
    }

    // Getter 方法
    public int getId() {
        return id;
    }

    public int getX1() {
        return x1;
    }

    public int getZ1() {
        return z1;
    }

    public int getX2() {
        return x2;
    }

    public int getZ2() {
        return z2;
    }

    public String getGuild() {
        return guild;
    }

    public String getWorld() {
        return world;
    }

    // toString 方法方便输出
    @Override
    public String toString() {
        return "GuildRegionData{" +
                "id=" + id +
                ", x1=" + x1 +
                ", z1=" + z1 +
                ", x2=" + x2 +
                ", z2=" + z2 +
                ", guild='" + guild + '\'' +
                ", world='" + world + '\'' +
                '}';
    }
}
