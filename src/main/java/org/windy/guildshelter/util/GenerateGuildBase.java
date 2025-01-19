package org.windy.guildshelter.util;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ResidenceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.windy.guildshelter.database.SqLiteDatabase;

public class GenerateGuildBase {

    private final JavaPlugin plugin;
    public static SqLiteDatabase sqLiteDatabase;  // 将 sqLiteDatabase 设置为 static
    public static final Logger LOGGER = LogManager.getLogger();


    public GenerateGuildBase(JavaPlugin plugin) {
        this.plugin = plugin;
        sqLiteDatabase = new SqLiteDatabase();  // 初始化静态字段
    }
    public static int centerX;
    public static int centerZ;
    public static int radius;
    private ResidenceManager residenceManager;

    public void createPlatform(int centerX, int centerY, int centerZ, int radius,int Total_length,int Total_width,int Road_width,int Plot_length,int Plot_width) {
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
            int A = Total_length;
            int B = Total_width;
            int l_a = Plot_length;
            int l_b = Plot_width;
            int d = Road_width;
            int x0 = centerX-(Total_length/2);  // 初始 x 坐标
            int y0 = centerZ+radius+Road_width;  // 初始 y 坐标
            int[][] result = fillArea(A, B, l_a, l_b, d, x0, y0);
            int plotCount = countPlots(A, B, l_a, l_b, d);
            System.out.println("Total number of plots: " + plotCount);
            int i=1;
            for (int[] plot : result) {
                System.out.println("(" + plot[0] + ", " + plot[1] + ") - (" + plot[2] + ", " + plot[3] + ")");
                createResidence(plot[0], plot[1], plot[2], plot[3], "Vespera","world",i,"愿听风止");
                i++;
            }
        });
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
    public void createResidence(int x1, int z1, int x2, int z2, String playerName,String worldname,int i,String guildName) {
        // 获取 Residence 插件实例
        Residence residencePlugin = (Residence) Bukkit.getServer().getPluginManager().getPlugin("Residence");

        // 获取 ResidenceManager 实例
        residenceManager = residencePlugin.getResidenceManager();
        // 获取目标世界
        World world = Bukkit.getWorld(worldname); // 假设世界名称是 "world"
        if (world == null) {
            LOGGER.error("Not world found");
            return;
        }

        // 定义两个对角点
        Location loc1 = new Location(world, x1, 0, z1);  // 第一个坐标点
        Location loc2 = new Location(world, x2, 256, z2);  // 第二个坐标点

        // 获取玩家对象
        Player player = Bukkit.getPlayer(playerName); // 获取玩家 "verpa"
        if (player == null) {
            LOGGER.error("Player not found.");
            return;
        }
        String House_Number_Name = guildName+i+"号";
        LOGGER.info(House_Number_Name);
        // 使用 addResidence 方法创建地块
        boolean success = residenceManager.addResidence(player, playerName, House_Number_Name, loc1, loc2, true);
        if (success) {
            LOGGER.info("Residence created successfully!");
        } else {
            LOGGER.error("Failed to create residence.");
        }
    }
}
