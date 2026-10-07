package tools;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class BuildTerrainShowcase {
    public static void main(String[] args) throws Exception {
        File artifactDir = new File("/Users/dimon/.gemini/antigravity/brain/4bbdced6-5c2d-47fd-aa53-35a4407a6e0b");
        artifactDir.mkdirs();

        File stoneOresFile = new File("/tmp/stone_ore_gems/Stone_ore_gems/Stones_ores_gems_without_grass.png");
        File stoneGrassFile = new File("/tmp/stone_ore_gems/Stone_ore_gems/Stone_ore_gems_grass.png");
        BufferedImage stoneOres = ImageIO.read(stoneOresFile);
        BufferedImage stoneGrass = ImageIO.read(stoneGrassFile);

        // Canvas: 880 x 520 (wide enough for 3 terrain options + dynamic plant demo on the right)
        BufferedImage canvas = new BufferedImage(880, 520, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(new Color(20, 22, 28));
        g.fillRect(0, 0, 880, 520);

        // Header
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(new Color(255, 230, 160));
        g.drawString("ЭКОСИСТЕМА МЕЖДУМОРЬЯ: ВАРИАНТЫ ТЕКСТУР И ФЛОРЫ", 35, 38);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(170, 180, 200));
        g.drawString("Готовые CC0 открытые ассеты: порода (вместо камня), покров (вместо дёрна), руда и тянущееся растение", 35, 58);

        int scale = 4; // 16x16 -> 64x64

        // --- ВАРИАНТ 1: Абиссальный Разлом ---
        drawOption(g, 35, 80, "Вариант 1: Абиссальный Разлом (Рекомендуется)",
                "Тёмно-графитовый сланец + бирюзовый дёрн + сияющие кристаллы",
                stoneOres.getSubimage(0, 4 * 16, 16, 16),
                stoneGrass.getSubimage(0, 8 * 16, 16, 16),
                stoneOres.getSubimage(0, 7 * 16, 16, 16),
                scale);

        // --- ВАРИАНТ 2: Эфирный Пепел ---
        drawOption(g, 35, 220, "Вариант 2: Эфирный Пепел",
                "Слоистый пепельный камень + индиго-лавандовый дёрн + чистое серебро",
                stoneOres.getSubimage(0, 0, 16, 16),
                stoneGrass.getSubimage(0, 7 * 16, 16, 16),
                stoneOres.getSubimage(0, 2 * 16, 16, 16),
                scale);

        // --- ВАРИАНТ 3: Токсичная Пучина ---
        drawOption(g, 35, 360, "Вариант 3: Токсичная Пучина",
                "Пористый базальт + яркий люминесцентный мох + циановая руда",
                stoneOres.getSubimage(16, 4 * 16, 16, 16),
                stoneGrass.getSubimage(16, 5 * 16, 16, 16),
                stoneOres.getSubimage(0, 8 * 16, 16, 16),
                scale);

        // --- ПРАВАЯ КОЛОНКА: ДИНАМИЧЕСКОЕ РАСТЕНИЕ ПРИЛИВА ---
        int plantX = 570;
        int plantY = 80;

        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.setColor(new Color(130, 240, 220));
        g.drawString("Растение прилива (Tide Sprout)", plantX, plantY + 15);

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(170, 180, 195));
        g.drawString("Реагирует на гравитацию: вытягивается к верхнему морю!", plantX, plantY + 32);

        // Load plant tiles
        File plantDir = new File("/tmp/plant_tiles/plant_individual_tiles");
        BufferedImage root = ImageIO.read(new File(plantDir, "root1.png"));
        BufferedImage stem1 = ImageIO.read(new File(plantDir, "stem1.png"));
        BufferedImage stemBoth = ImageIO.read(new File(plantDir, "stem1_leaf_both.png"));
        BufferedImage flower = ImageIO.read(new File(plantDir, "flower1.png"));
        BufferedImage bud = ImageIO.read(new File(plantDir, "bud.png"));

        int pScale = 3; // 48px per block

        // State A: Штиль (Calm) — 1 блок (высота 48px)
        int calmX = plantX + 15;
        int calmY = plantY + 310;
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(new Color(180, 210, 255));
        g.drawString("В штиль: 1 блок", calmX, calmY - 10);

        // Draw ground under calm
        g.setColor(new Color(55, 65, 80));
        g.fillRect(calmX - 5, calmY + 48, 58, 10);
        // Draw 1-block bud/flower
        g.drawImage(root, calmX, calmY, 16 * pScale, 16 * pScale, null);
        g.drawImage(bud, calmX, calmY, 16 * pScale, 16 * pScale, null);

        // State B: Прилив (Surge) — вытягивается вверх (4-5 блоков!)
        int surgeX = plantX + 160;
        int surgeBaseY = plantY + 310;
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(new Color(255, 180, 90));
        g.drawString("При приливе: до 7 блоков!", surgeX, plantY + 55);

        // Draw ground under surge
        g.setColor(new Color(55, 65, 80));
        g.fillRect(surgeX - 5, surgeBaseY + 48, 58, 10);

        // Draw 5-block vertical stack
        int bY = surgeBaseY;
        // Block 1 (bottom): root
        g.drawImage(root, surgeX, bY, 16 * pScale, 16 * pScale, null);
        bY -= 16 * pScale;
        // Block 2: stem
        g.drawImage(stem1, surgeX, bY, 16 * pScale, 16 * pScale, null);
        bY -= 16 * pScale;
        // Block 3: stem with leaves
        g.drawImage(stemBoth, surgeX, bY, 16 * pScale, 16 * pScale, null);
        bY -= 16 * pScale;
        // Block 4: stem
        g.drawImage(stem1, surgeX, bY, 16 * pScale, 16 * pScale, null);
        bY -= 16 * pScale;
        // Block 5 (top): blooming flower pointing up
        g.drawImage(flower, surgeX, bY, 16 * pScale, 16 * pScale, null);

        g.dispose();

        File outputFile = new File(artifactDir, "terrain_showcase.png");
        ImageIO.write(canvas, "PNG", outputFile);
        System.out.println("Enhanced showcase saved to: " + outputFile.getAbsolutePath());
    }

    private static void drawOption(Graphics2D g, int x, int y, String title, String subtitle,
                                   BufferedImage rock, BufferedImage turf, BufferedImage ore, int scale) {
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(new Color(255, 215, 0));
        g.drawString(title, x, y + 15);

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(160, 165, 180));
        g.drawString(subtitle, x, y + 32);

        int imgY = y + 40;
        int imgSize = 16 * scale; // 64

        // Draw rock
        drawScaled(g, rock, x, imgY, scale);
        drawLabel(g, "Порода (вместо камня)", x, imgY + imgSize + 14);

        // Draw turf
        drawScaled(g, turf, x + 95, imgY, scale);
        drawLabel(g, "Дёрн/Мох (вместо земли)", x + 95, imgY + imgSize + 14);

        // Draw ore
        drawScaled(g, ore, x + 190, imgY, scale);
        drawLabel(g, "Руда серебра", x + 190, imgY + imgSize + 14);
    }

    private static void drawScaled(Graphics2D g, BufferedImage img, int x, int y, int scale) {
        g.setColor(new Color(15, 15, 20));
        g.fillRect(x - 2, y - 2, 16 * scale + 4, 16 * scale + 4);
        g.drawImage(img, x, y, 16 * scale, 16 * scale, null);
    }

    private static void drawLabel(Graphics2D g, String text, int x, int y) {
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(new Color(200, 205, 220));
        g.drawString(text, x, y);
    }
}
