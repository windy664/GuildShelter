package org.windy.guildshelter.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.List;

public class GenerateGuildBase {

    private final JavaPlugin plugin;

    public GenerateGuildBase(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private int Total_length;
    private int Total_width;
    private int Plot_radius;
    private static int Road_width;
    public static int centerX;
    public static int centerZ;
    private static int radius;

    public void createPlatform(int centerX, int centerY, int centerZ, int radius) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    // 计算当前 (x, z) 是否在正方形平台的范围内
                    if (Math.abs(x - centerX) <= radius && Math.abs(z - centerZ) <= radius) {
                        Block block = Bukkit.getWorld("world").getBlockAt(x, centerY, z);
                        block.setType(Material.STONE_BRICKS);
                    }
                }
            }
        });
        Bukkit.getScheduler().runTask(plugin, () -> {
            int plot_count = fill_area(Total_length, Total_width, Plot_radius, Road_width);
            System.out.println("Total number of plots: " + plot_count);
        });
    }

    public static int fill_area(int width, int height, int r, int d) {
        int step = 2 * r + d;  // 计算步长
        int x = centerX-radius;  // 初始化横坐标
        int y = centerZ+radius+d;  // 初始化纵坐标
        int plot_count = 0;  // 用于计数 plot 的数量
        while (y < height) {
            while (x < width) {
                System.out.println("Point: (" + x + ", " + y + ")");  // 打印每个点的信息到控制台
                plot_count++;  // 每生成一个点，计数器加 1
                x += step;  // 横坐标增加步长
            }
            x = 0;  // 横坐标重置为 0
            y += step;  // 纵坐标增加步长
        }
        return plot_count;
    }
}
