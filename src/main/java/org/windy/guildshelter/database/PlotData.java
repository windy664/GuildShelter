package org.windy.guildshelter.database;

public class PlotData {
    private int x1, z1, x2, z2;
    private String owner, member, guild, state;
    private String world;  // 修改字段名为 world

    // 修改构造方法，接受正确的参数类型
    public PlotData(int x1, int z1, int x2, int z2, String owner, String member, String world, String guild, String state) {
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
        this.owner = owner;
        this.member = member;
        this.world = world;  // 将 levels 修改为 world
        this.guild = guild;
        this.state = state;
    }

    // 其他 getter 和 setter 方法
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

    public String getOwner() {
        return owner;
    }

    public String getMember() {
        return member;
    }

    public String getWorld() {  // 修改 getter 方法为 world
        return world;
    }

    public String getGuild() {
        return guild;
    }

    public String getState() {
        return state;
    }

    // 如果你有需要，可以添加其他方法
}
