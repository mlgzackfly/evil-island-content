package tw.zack.evilisland.world;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class WorldMapRenderer {
    private static final int WIDTH = 1560;
    private static final int HEIGHT = 980;
    private static final int SCALE = 2;
    private static final int MIN_X = -3100 * SCALE;
    private static final int MAX_X = 4800 * SCALE;
    private static final int MIN_Z = -2500 * SCALE;
    private static final int MAX_Z = 2400 * SCALE;

    private WorldMapRenderer() {
    }

    public static void main(String[] args) throws IOException {
        File output = new File(args.length == 0 ? "build/reports/world-map.png" : args[0]);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawTerrain(image);
        drawOverlay(graphics);
        graphics.dispose();
        output.getParentFile().mkdirs();
        ImageIO.write(image, "png", output);
        System.out.println(output.getAbsolutePath());
    }

    private static void drawTerrain(BufferedImage image) {
        Color ocean = new Color(22, 65, 88);
        for (int py = 0; py < HEIGHT; py++) {
            int z = worldZ(py);
            for (int px = 0; px < WIDTH; px++) {
                int x = worldX(px);
                Color color = ocean;
                int modelX = Math.floorDiv(x, SCALE);
                int modelZ = Math.floorDiv(z, SCALE);
                if (EvilIslandShape.isMagicIsland(modelX, modelZ)) {
                    color = new Color(55, 128, 116);
                } else if (EvilIslandShape.isEastContinent(modelX, modelZ)) {
                    color = heightColor(modelX, modelZ, new Color(92, 132, 75));
                } else if (EvilIslandShape.isEvilIsland(modelX, modelZ)) {
                    Color base = modelX > 620 ? new Color(114, 112, 102) : new Color(73, 111, 67);
                    color = heightColor(modelX, modelZ, base);
                }
                image.setRGB(px, py, color.getRGB());
            }
        }
    }

    private static Color heightColor(int x, int z, Color base) {
        int height = EvilIslandShape.surfaceHeight(x, z);
        int shift = Math.max(-18, Math.min(34, (height - 70) / 3));
        return new Color(clamp(base.getRed() + shift), clamp(base.getGreen() + shift), clamp(base.getBlue() + shift));
    }

    private static void drawOverlay(Graphics2D g) {
        g.setColor(new Color(8, 25, 35, 225));
        g.fillRect(0, 0, WIDTH, 82);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 31));
        g.drawString("噩盡島世界圖譜", 34, 42);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.setColor(new Color(190, 211, 219));
        g.drawString("第二部結局後 · HD 2 倍尺度（約 15800 × 9800 方塊）· 東為右、北為上", 35, 67);

        g.setStroke(new BasicStroke(2.2f));
        drawRoute(g, 8600, 0, 8000, 0, new Color(225, 205, 124));
        drawRoute(g, 8000, 0, 840, 0, new Color(225, 205, 124));
        drawRoute(g, 840, 0, 1400, -290, new Color(225, 205, 124));

        label(g, "東大陸新城", 8600, 0, -125, -17);
        label(g, "歲安城", 1400, 0, -58, -22);
        label(g, "擎天塔", 1400, 0, -2, 38);
        label(g, "九回山／九回城", 2360, 0, 17, -25);
        label(g, "山口鎮", 3080, -160, 12, 42);
        label(g, "絨須洞", 2640, 1240, 16, 2);
        label(g, "魔法島", 6840, 3500, 15, -12);
        label(g, "龍宮", 5000, -3800, 15, -10);
        label(g, "西方荒野", -3000, 0, -20, -16);

        g.setColor(new Color(238, 245, 247));
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString("北", WIDTH - 62, 115);
        g.drawLine(WIDTH - 52, 127, WIDTH - 52, 174);
        g.drawLine(WIDTH - 52, 127, WIDTH - 59, 141);
        g.drawLine(WIDTH - 52, 127, WIDTH - 45, 141);

        g.setColor(new Color(8, 25, 35, 218));
        g.fillRoundRect(25, HEIGHT - 68, 565, 42, 6, 6);
        g.setColor(new Color(221, 232, 235));
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.drawString("實線為新城—港口—歲安河港主航線；實際地形與建築由插件按區塊生成。", 42, HEIGHT - 41);
    }

    private static void label(Graphics2D g, String text, int x, int z, int dx, int dy) {
        int px = pixelX(x);
        int py = pixelY(z);
        g.setColor(new Color(246, 202, 84));
        g.fillOval(px - 5, py - 5, 10, 10);
        g.setColor(new Color(5, 20, 28, 220));
        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        int width = g.getFontMetrics().stringWidth(text);
        g.fillRoundRect(px + dx - 5, py + dy - 18, width + 10, 24, 5, 5);
        g.setColor(Color.WHITE);
        g.drawString(text, px + dx, py + dy);
    }

    private static void drawRoute(Graphics2D g, int x1, int z1, int x2, int z2, Color color) {
        g.setColor(color);
        g.drawLine(pixelX(x1), pixelY(z1), pixelX(x2), pixelY(z2));
    }

    private static int worldX(int pixelX) {
        return MIN_X + (int) Math.round(pixelX * (MAX_X - MIN_X) / (double) WIDTH);
    }

    private static int worldZ(int pixelY) {
        return MIN_Z + (int) Math.round(pixelY * (MAX_Z - MIN_Z) / (double) HEIGHT);
    }

    private static int pixelX(int x) {
        return (int) Math.round((x - MIN_X) * WIDTH / (double) (MAX_X - MIN_X));
    }

    private static int pixelY(int z) {
        return (int) Math.round((z - MIN_Z) * HEIGHT / (double) (MAX_Z - MIN_Z));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
