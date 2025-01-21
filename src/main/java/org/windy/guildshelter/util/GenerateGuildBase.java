package org.windy.guildshelter.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.database.CenterTable;
import org.windy.guildshelter.database.GuildRegionTable;
import org.windy.guildshelter.database.PlotTable;

public class GenerateGuildBase {

    private final JavaPlugin plugin;
    private final PlotTable plotTable;  // 添加 PlotTable 字段
    private final CenterTable centerTable;  // 添加 PlotTable 字段
    private final GuildRegionTable guildRegionTable;  // GuildRegionTable 实例


    public GenerateGuildBase(JavaPlugin plugin, PlotTable plotTable, CenterTable centerTable, GuildRegionTable guildRegionTable) {
        this.plugin = plugin;
        this.plotTable = plotTable;  // 初始化 PlotTable
        this.centerTable = centerTable;  // 初始化 PlotTable
        this.guildRegionTable = guildRegionTable;  // 初始化 GuildRegionTable
    }
    public static final Logger LOGGER = LogManager.getLogger();



    public void createPlatform(int centerX, int centerY, int centerZ, int radius, int totalLength, int totalWidth, int roadWidth, int plotLength, int plotWidth, String world, String guildName) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            // 创建平台的代码不变，使用石砖填充指定范围
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    if (Math.abs(x - centerX) <= radius && Math.abs(z - centerZ) <= radius) {
                        Block block = Bukkit.getWorld(world).getBlockAt(x, centerY, z);
                        block.setType(Material.STONE_BRICKS);
                    }
                }
            }
            // 保存中心坐标到数据库
            centerTable.insertCenter(centerX - radius, centerZ - radius, centerX + radius, centerX + radius,"","",guildName,world,"");
        });

        Bukkit.getScheduler().runTask(plugin, () -> {
            // 计算填充区域并检查地块是否与其他公会的地块冲突
            int x0 = centerX - (totalLength / 2);  // 初始 x 坐标
            int y0 = centerZ + radius + roadWidth;  // 初始 y 坐标

            int az = y0 - 2 * radius;

            int[][] result = fillArea(totalLength, totalWidth, plotLength, plotWidth, roadWidth, x0, y0);
            int plotCount = countPlots(totalLength, totalWidth, plotLength, plotWidth, roadWidth);
            int i = 1;
            System.out.println("Total number of plots: " + plotCount);
            String doorplate = guildName+" "+i+"号";
            int[] lastPlot = null;

            for (int[] plot : result) {
                System.out.println("(" + plot[0] + ", " + plot[1] + ") - (" + plot[2] + ", " + plot[3] + ")");
                plotTable.insertPlot(plot[0], plot[1], plot[2], plot[3], "Vespea", "", "","private", guildName,world, "",doorplate );
                lastPlot = plot;
                i++;
            }
            if (lastPlot != null) {
                System.out.println("Last plot coordinates: (" + lastPlot[0] + ", " + lastPlot[1] + ") - (" + lastPlot[2] + ", " + lastPlot[3] + ")");
                guildRegionTable.insertGuildRegion(x0, az, lastPlot[2], lastPlot[3], guildName, world);
            }
        });
    }

    public static int[][] fillArea(int totalLength, int totalWidth, int plotLength, int plotWidth, int roadWidth, int x0, int y0) {
        int xCount = (totalLength - plotLength) / (plotLength + roadWidth) + 1;
        int yCount = (totalWidth - plotWidth) / (plotWidth + roadWidth) + 1;
        int[][] plots = new int[xCount * yCount][4];
        int index = 0;
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

    public static int countPlots(int totalLength, int totalWidth, int plotLength, int plotWidth, int roadWidth) {
        int xCount = (totalLength - plotLength) / (plotLength + roadWidth) + 1;
        int yCount = (totalWidth - plotWidth) / (plotWidth + roadWidth) + 1;
        return xCount * yCount;
    }

}
