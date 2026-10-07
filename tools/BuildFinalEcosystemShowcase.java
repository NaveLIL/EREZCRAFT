import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class BuildFinalEcosystemShowcase {
    public static void main(String[] args) throws Exception {
        File artifactDir = new File("/Users/dimon/.gemini/antigravity/brain/4bbdced6-5c2d-47fd-aa53-35a4407a6e0b");
        artifactDir.mkdirs();

        String blockDir = "src/main/resources/assets/interstice/textures/block/";
        BufferedImage riftstone = ImageIO.read(new File(blockDir + "riftstone.png"));
        BufferedImage turfTop = ImageIO.read(new File(blockDir + "abyssal_turf_top.png"));
        BufferedImage turfSide = ImageIO.read(new File(blockDir + "abyssal_turf_side.png"));
        BufferedImage ore = ImageIO.read(new File(blockDir + "riftsilver_ore.png"));

        BufferedImage sproutBud = ImageIO.read(new File(blockDir + "tide_sprout_bud.png"));
        BufferedImage sproutRoot = ImageIO.read(new File(blockDir + "tide_sprout_root.png"));
        BufferedImage sproutStem = ImageIO.read(new File(blockDir + "tide_sprout_stem.png"));
        BufferedImage sproutStemLeaf = ImageIO.read(new File(blockDir + "tide_sprout_stem_leaf.png"));
        BufferedImage sproutFlower = ImageIO.read(new File(blockDir + "tide_sprout_flower.png"));

        // Canvas: 880 x 440
        BufferedImage canvas = new BufferedImage(880, 440, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(15, 17, 24));
        g.fillRect(0, 0, 880, 440);

        // Header
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(new Color(130, 240, 220));
        g.drawString("ЭКОСИСТЕМА МЕЖДУМОРЬЯ: РЕАЛИЗОВАННЫЕ БЛОКИ И ФЛОРА", 35, 38);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(160, 180, 200));
        g.drawString("Все текстуры строго квадратные 16x16, CC0 открытые ассеты, тинтованные в палитру измерения", 35, 58);

        int scale = 4; // 16x16 -> 64x64

        // --- БЛОКИ ТЕРРЕЙНА И РУДЫ (Слева) ---
        drawCard(g, 35, 80, "Разломный сланец", "Тело островов (вместо камня)", riftstone, scale);
        drawCard(g, 175, 80, "Абиссальный дёрн", "Покров поверхности (верх)", turfTop, scale);
        drawCard(g, 315, 80, "Абиссальный дёрн (бок)", "Боковая грань острова", turfSide, scale);
        drawCard(g, 455, 80, "Руда серебра", "Новая рудная жила в сланце", ore, scale);

        // --- ДИНАМИЧЕСКИЙ ПРИЛИВНОЙ ПОБЕГ (Справа) ---
        int rightX = 610;
        g.setColor(new Color(25, 30, 42));
        g.fillRoundRect(rightX, 75, 235, 335, 12, 12);
        g.setColor(new Color(60, 80, 110));
        g.drawRoundRect(rightX, 75, 235, 335, 12, 12);

        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(new Color(140, 255, 210));
        g.drawString("Приливной побег (Tide Sprout)", rightX + 15, 100);

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(180, 200, 220));
        g.drawString("Штиль (CALM):", rightX + 15, 125);
        g.drawString("Высота: 1 блок (бутон)", rightX + 15, 140);

        // Draw calm state on turf
        int plantScale = 3; // 48x48
        int calmX = rightX + 25;
        int baseY = 215;
        // Turf below
        g.drawImage(turfSide, calmX, baseY, 16 * plantScale, 16 * plantScale, null);
        // Sprout bud on top
        g.drawImage(sproutBud, calmX, baseY - 16 * plantScale, 16 * plantScale, 16 * plantScale, null);

        // Surge state
        int surgeX = rightX + 125;
        g.drawString("Прилив (SURGE):", surgeX, 125);
        g.drawString("Высота: 3-7 блоков", surgeX, 140);
        g.drawString("+ свечение цветка (7)", surgeX, 155);

        int surgeBaseY = 340;
        int s = 2; // scale 2 for tall stack (32x32)
        // Turf
        g.drawImage(turfSide, surgeX + 10, surgeBaseY, 16 * s, 16 * s, null);
        // Root
        g.drawImage(sproutRoot, surgeX + 10, surgeBaseY - 16 * s, 16 * s, 16 * s, null);
        // Stem
        g.drawImage(sproutStem, surgeX + 10, surgeBaseY - 32 * s, 16 * s, 16 * s, null);
        // Stem leaf
        g.drawImage(sproutStemLeaf, surgeX + 10, surgeBaseY - 48 * s, 16 * s, 16 * s, null);
        // Stem
        g.drawImage(sproutStem, surgeX + 10, surgeBaseY - 64 * s, 16 * s, 16 * s, null);
        // Flower top (bloomed!)
        g.drawImage(sproutFlower, surgeX + 10, surgeBaseY - 80 * s, 16 * s, 16 * s, null);

        // Mini preview of terrain composite (Slice of Island)
        drawIslandSlice(g, 35, 235, riftstone, turfSide, ore, sproutBud);

        g.dispose();
        File outFile = new File(artifactDir, "ecosystem_showcase.png");
        ImageIO.write(canvas, "PNG", outFile);
        System.out.println("Saved showcase to " + outFile.getAbsolutePath());
    }

    static void drawCard(Graphics2D g, int x, int y, String title, String subtitle, BufferedImage img, int scale) {
        g.setColor(new Color(25, 28, 38));
        g.fillRoundRect(x, y, 125, 135, 10, 10);
        g.setColor(new Color(50, 60, 80));
        g.drawRoundRect(x, y, 125, 135, 10, 10);

        // 64x64 texture
        g.drawImage(img, x + (125 - 16 * scale) / 2, y + 12, 16 * scale, 16 * scale, null);

        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(new Color(230, 240, 255));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, x + (125 - fm.stringWidth(title)) / 2, y + 95);

        g.setFont(new Font("SansSerif", Font.PLAIN, 8));
        g.setColor(new Color(140, 160, 180));
        FontMetrics fms = g.getFontMetrics();
        g.drawString(subtitle, x + (125 - fms.stringWidth(subtitle)) / 2, y + 115);
    }

    static void drawIslandSlice(Graphics2D g, int x, int y, BufferedImage riftstone, BufferedImage turfSide, BufferedImage ore, BufferedImage bud) {
        g.setColor(new Color(25, 28, 38));
        g.fillRoundRect(x, y, 545, 175, 10, 10);
        g.setColor(new Color(50, 60, 80));
        g.drawRoundRect(x, y, 545, 175, 10, 10);

        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(new Color(255, 230, 160));
        g.drawString("Срез острова Междуморья в игре (Генерация ландшафта):", x + 15, y + 22);

        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(new Color(160, 180, 200));
        g.drawString("Поверхность: Абиссальный дёрн с побегами • Недра: Разломный сланец с жилами Серебра", x + 15, y + 38);

        // Draw a grid of blocks representing terrain cross-section
        int bs = 24; // block size
        int startX = x + 20;
        int startY = y + 50;

        int cols = 20;
        int rows = 4;
        for (int c = 0; c < cols; c++) {
            // Surface turf
            g.drawImage(turfSide, startX + c * bs, startY, bs, bs, null);
            // Bud occasionally on top
            if (c == 3 || c == 9 || c == 16) {
                g.drawImage(bud, startX + c * bs, startY - bs, bs, bs, null);
            }
            // Sub-surface riftstone layers
            for (int r = 1; r < rows; r++) {
                BufferedImage block = riftstone;
                // Ore vein around col 6-7 row 2, and col 13-14 row 3
                if ((c == 6 && r == 2) || (c == 7 && r == 2) || (c == 7 && r == 1) || (c == 13 && r == 2) || (c == 14 && r == 3)) {
                    block = ore;
                }
                g.drawImage(block, startX + c * bs, startY + r * bs, bs, bs, null);
            }
        }
    }
}
