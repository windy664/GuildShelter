package org.windy.guildshelter.util;

public class PlotArea {
    private final int x1, z1, x2, z2;
    private final String guildName;
    private final String world;

    public PlotArea(int x1, int z1, int x2, int z2, String guildName, String world) {
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
        this.guildName = guildName;
        this.world = world;
    }

    // Getter 和 Setter 方法
    public int getX1() { return x1; }
    public int getZ1() { return z1; }
    public int getX2() { return x2; }
    public int getZ2() { return z2; }
    public String getGuildName() { return guildName; }
    public String getWorld() { return world; }
}
