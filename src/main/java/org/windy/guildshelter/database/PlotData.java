package org.windy.guildshelter.database;

// PlotData 类（用于存储 Plot 信息）
public class PlotData {
    private int id;
    private int x1, z1, x2, z2;
    private String owner, member, title, state, guild, world, flag, doorplate;

    // 构造函数，getters 和 setters
    public PlotData(int id, int x1, int z1, int x2, int z2, String owner, String member, String title, String state, String guild, String world, String flag, String doorplate) {
        this.id = id;
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
        this.owner = owner;
        this.member = member;
        this.title = title;
        this.state = state;
        this.guild = guild;
        this.world = world;
        this.flag = flag;
        this.doorplate = doorplate;
    }
}
