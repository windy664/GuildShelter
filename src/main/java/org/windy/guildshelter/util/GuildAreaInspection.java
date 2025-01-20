package org.windy.guildshelter.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.windy.guildshelter.database.SqLiteDatabase;

public class GuildAreaInspection {
    private final SqLiteDatabase sqLiteDatabase;

    public GuildAreaInspection(SqLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    public boolean checkAreaConflict(int centerX, int centerY, int centerZ, int radius,int Total_length,int Total_width,int Road_width,int Plot_length,int Plot_width) {
        //推演创建过程
        int x0 = centerX - (Total_length / 2);  // 初始 x 坐标
        int y0 = centerZ + radius + Road_width;  // 初始 y 坐标
        int Az = y0 - 2*radius;
        int[][] result = fillArea(Total_length, Total_width, Plot_length, Plot_width, Road_width, x0, y0);
        int plotCount = countPlots(Total_length, Total_width, Plot_length, Plot_width, Road_width);
        int i = 1;
        int[] lastPlot = null;  // 用来存储最后一个坐标

        for (int[] plot : result) {
            System.out.println("(" + plot[0] + ", " + plot[1] + ") - (" + plot[2] + ", " + plot[3] + ")");
            sqLiteDatabase.insertPlot(plot[0], plot[1], plot[2], plot[3], "Vespea", "", "world", "愿听风止", "private");
            lastPlot = plot;  // 更新 lastPlot 为当前坐标
            i++;
        }
        if (lastPlot != null) {
            System.out.println("Last plot coordinates: (" + lastPlot[0] + ", " + lastPlot[1] + ") - (" + lastPlot[2] + ", " + lastPlot[3] + ")");
            sqLiteDatabase.insertGuildShelterArea(x0,Az,lastPlot[2],lastPlot[3],"愿听风止");
        }
        //看看最后是否冲突
        return sqLiteDatabase.isConflictWithExistingArea(x0, Az, lastPlot[2], lastPlot[3], world);
    }
    public static int[][] fillArea(int A, int B, int l_a, int l_b, int d, int x0, int y0) {
        int xCount = (A - l_a) / (l_a + d) + 1;
        int yCount = (B - l_b) / (l_b + d) + 1;
        int[][] plots = new int[xCount * yCount][4];
        int index = 0;
        for (int y = 0; y < yCount; y++) {
            for (int x = 0; x < xCount; x++) {
                int x1 = x0 + x * (l_a + d);
                int y1 = y0 + y * (l_b + d);
                plots[index][0] = x1;
                plots[index][1] = y1;
                plots[index][2] = x1 + l_a;
                plots[index][3] = y1 + l_b;
                index++;
            }
        }
        return plots;
    }

    public static int countPlots(int A, int B, int l_a, int l_b, int d) {
        int xCount = (A - l_a) / (l_a + d) + 1;
        int yCount = (B - l_b) / (l_b + d) + 1;
        return xCount * yCount;
    }
}
