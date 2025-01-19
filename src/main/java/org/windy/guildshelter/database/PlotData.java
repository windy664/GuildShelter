package org.windy.guildshelter.database;

public class PlotData {
    private int x;
    private int y;
    private String player;
    private String truster;
    private String guild;

    public PlotData(int x, int y, String player, String truster, String guild) {
        this.x = x;
        this.y = y;
        this.player = player;
        this.truster = truster;
        this.guild = guild;
    }

    // Getter 和 Setter 方法
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public String getPlayer() {
        return player;
    }

    public String getTruster() {
        return truster;
    }

    public String getGuild() {
        return guild;
    }

    public void setTruster(String truster) {
        this.truster = truster;
    }
}

