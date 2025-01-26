package org.windy.guildshelter.util;

import org.windy.guildshelter.database.mysql.DatabaseManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class RectangularArea {
    private int leftBottomX;
    private int leftBottomY;
    private int rightTopX;
    private int rightTopY;

    public RectangularArea(int leftBottomX, int leftBottomY, int rightTopX, int rightTopY) {
        this.leftBottomX = leftBottomX;
        this.leftBottomY = leftBottomY;
        this.rightTopX = rightTopX;
        this.rightTopY = rightTopY;
    }

    public int getLeftBottomX() {
        return leftBottomX;
    }

    public int getLeftBottomY() {
        return leftBottomY;
    }

    public int getRightTopX() {
        return rightTopX;
    }

    public int getRightTopY() {
        return rightTopY;
    }

    @Override
    public String toString() {
        return "左下角坐标: (" + leftBottomX + ", " + leftBottomY + "), 右上角坐标: (" + rightTopX + ", " + rightTopY + ")";
    }
}

abstract class CityArea {
    protected RectangularArea area;

    public CityArea(int leftBottomX, int leftBottomY, int length, int width) {
        // 通过左下角和总长度、总宽度计算右上角坐标
        this.area = new RectangularArea(leftBottomX, leftBottomY,
                leftBottomX + length, leftBottomY + width);
    }

    public RectangularArea getArea() {
        return area;
    }
}

class ImperialCity extends CityArea {
    public ImperialCity(int leftBottomX, int leftBottomY, int length, int width) {
        super(leftBottomX, leftBottomY, length, width);
    }
}

class PalaceCity extends CityArea {
    private List<RectangularArea> civilianAreas;

    public PalaceCity(int leftBottomX, int leftBottomY, int length, int width) {
        super(leftBottomX, leftBottomY, length, width);
        this.civilianAreas = new ArrayList<>();
    }

    public void addCivilianArea(RectangularArea area) {
        civilianAreas.add(area);
    }

    public List<RectangularArea> getCivilianAreas() {
        return civilianAreas;
    }

    public void fillCivilianAreas(int plotLength, int plotWidth, int roadWidth, RectangularArea imperialArea) {
        int totalLength = area.getRightTopX() - area.getLeftBottomX();
        int totalWidth = area.getRightTopY() - area.getLeftBottomY();
        int x0 = area.getLeftBottomX();
        int y0 = area.getLeftBottomY();

        // 可用地块的数量
        int xCount = (totalLength - plotLength) / (plotLength + roadWidth) + 1;
        int yCount = (totalWidth - plotWidth) / (plotWidth + roadWidth) + 1;

        // 填充区域
        for (int y = 0; y < yCount; y++) {
            for (int x = 0; x < xCount; x++) {
                int x1 = x0 + x * (plotLength + roadWidth);
                int y1 = y0 + y * (plotWidth + roadWidth);
                int x2 = x1 + plotLength;
                int y2 = y1 + plotWidth;

                // 检查该平民区域是否与皇城重叠
                if (x2 <= area.getRightTopX() && y2 <= area.getRightTopY() && !isOverlap(new RectangularArea(x1, y1, x2, y2), imperialArea)) {
                    addCivilianArea(new RectangularArea(x1, y1, x2, y2));
                }
            }
        }
    }

    // 判断两个区域是否重叠
    private boolean isOverlap(RectangularArea area1, RectangularArea area2) {
        return !(area1.getRightTopX() <= area2.getLeftBottomX() || area2.getRightTopX() <= area1.getLeftBottomX() ||
                area1.getRightTopY() <= area2.getLeftBottomY() || area2.getRightTopY() <= area1.getLeftBottomY());
    }
}

public class GeneraeInitialGuildBase {

    private DatabaseManager databaseManager;

    public GeneraeInitialGuildBase(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }
    public void create(int startX, int startZ, int totalLength, int totalWidth, int roadWidth, int plotLength, int plotWidth, String world, String guildName) {

        // 设置皇城的位置和大小
        int imperialLength = (int) (totalLength * 0.10); // 假设皇城占据10%区域
        int imperialWidth = (int) (totalWidth * 0.10);
        int imperialLeftBottomX = (totalLength - imperialLength) / 2;
        int imperialLeftBottomY = (totalWidth - imperialWidth) / 2;
        ImperialCity imperialCity = new ImperialCity(imperialLeftBottomX, imperialLeftBottomY,
                imperialLength, imperialWidth);

        // 宫城的灵活坐标设置
        int palaceLeftBottomX = 98;   // 灵活设置的左下角X
        int palaceLeftBottomY = 586;   // 灵活设置的左下角Y
        int palaceLength = totalLength;  // 宫城的总长度
        int palaceWidth = totalWidth;  // 宮城的总宽度
        PalaceCity palaceCity = new PalaceCity(palaceLeftBottomX, palaceLeftBottomY, palaceLength, palaceWidth);

        // 填充宫城中的平民区域，避免与皇城重叠
        palaceCity.fillCivilianAreas(plotLength, plotWidth, roadWidth, imperialCity.getArea());

        // 输出信息
        System.out.println("皇城信息: " + imperialCity.getArea());
        System.out.println("宫城信息: " + palaceCity.getArea());

        // 插入宫城信息到 GuildShelter_base
        databaseManager.insertGuildShelterBase(imperialCity.getArea().getLeftBottomX(), imperialCity.getArea().getLeftBottomY(),
                imperialCity.getArea().getRightTopX(), imperialCity.getArea().getRightTopY(), "Vespera", guildName, guildName,
                world, "flag", "欢迎来到我的公会小基地", "Vespera");

        // 插入平民居住区域信息到 GuildShelter_plot
        for (int i = 0; i < palaceCity.getCivilianAreas().size(); i++) {
            String title = "guildName" + (i + 1) + "号";

            RectangularArea civilianArea = palaceCity.getCivilianAreas().get(i);
            databaseManager.insertGuildShelterPlot(civilianArea.getLeftBottomX(), civilianArea.getLeftBottomY(),
                    civilianArea.getRightTopX(), civilianArea.getRightTopY(), "Vespera", title, guildName,
                    world, "flag", "您已进入" + title, "Vespera");
            System.out.println("宫城内平民居住区域 " + (i + 1) + " 信息: " + civilianArea);
        }
    }
}


