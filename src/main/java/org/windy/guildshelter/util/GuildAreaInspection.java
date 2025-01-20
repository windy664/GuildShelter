package org.windy.guildshelter.util;

import org.windy.guildshelter.database.PlotTable;
import org.windy.guildshelter.database.PlotData;

public class GuildAreaInspection {
    private final PlotTable plotTable;

    // 构造函数，接收 PlotTable 实例
    public GuildAreaInspection(PlotTable plotTable) {
        this.plotTable = plotTable;
    }

    // 修改后的 checkAreaConflict 方法，查询数据库中的冲突地块
    public boolean checkAreaConflict(int centerX, int centerY, int centerZ, int radius, int totalLength, int totalWidth, int roadWidth, int plotLength, int plotWidth, String world) {
        // 推演创建过程
        int x0 = centerX - (totalLength / 2);  // 初始 x 坐标
        int y0 = centerZ + radius + roadWidth;  // 初始 y 坐标
        int az = y0 - 2 * radius;  // az 是目标区域的终点坐标

        // 填充整个区域的地块坐标
        int[][] result = fillArea(totalLength, totalWidth, plotLength, plotWidth, roadWidth, x0, y0);
        int plotCount = countPlots(totalLength, totalWidth, plotLength, plotWidth, roadWidth);
        int i = 1;
        int[] lastPlot = null;  // 用来存储最后一个坐标

        // 打印所有地块坐标
        for (int[] plot : result) {
            System.out.println("(" + plot[0] + ", " + plot[1] + ") - (" + plot[2] + ", " + plot[3] + ")");
            lastPlot = plot;  // 更新 lastPlot 为当前坐标
            i++;
        }

        if (lastPlot != null) {
            System.out.println("Last plot coordinates: (" + lastPlot[0] + ", " + lastPlot[1] + ") - (" + lastPlot[2] + ", " + lastPlot[3] + ")");
        }

        // 使用 R-tree 查询是否有重叠地块
        PlotData plotData = plotTable.getPlotByRange(x0, az, lastPlot[2], lastPlot[3], world);
        return plotData != null;  // 如果找到了匹配的地块，则认为存在冲突
    }

    // 填充地块坐标
    public static int[][] fillArea(int totalLength, int totalWidth, int plotLength, int plotWidth, int roadWidth, int x0, int y0) {
        int xCount = (totalLength - plotLength) / (plotLength + roadWidth) + 1;
        int yCount = (totalWidth - plotWidth) / (plotWidth + roadWidth) + 1;
        int[][] plots = new int[xCount * yCount][4];
        int index = 0;

        // 按照给定的长度和宽度填充坐标
        for (int y = 0; y < yCount; y++) {
            for (int x = 0; x < xCount; x++) {
                int x1 = x0 + x * (plotLength + roadWidth);
                int y1 = y0 + y * (plotWidth + roadWidth);
                plots[index][0] = x1;
                plots[index][1] = y1;
                plots[index][2] = x1 + plotLength;
                plots[index][3] = y1 + plotWidth;
                index++;
            }
        }
        return plots;
    }

    // 计算需要的地块数量
    public static int countPlots(int totalLength, int totalWidth, int plotLength, int plotWidth, int roadWidth) {
        int xCount = (totalLength - plotLength) / (plotLength + roadWidth) + 1;
        int yCount = (totalWidth - plotWidth) / (plotWidth + roadWidth) + 1;
        return xCount * yCount;
    }
}
