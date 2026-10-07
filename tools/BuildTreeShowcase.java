import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class BuildTreeShowcase {
    static final int T = 16;

    public static void main(String[] args) throws Exception {
        File artifactDir = new File("/Users/dimon/.gemini/antigravity/brain/4bbdced6-5c2d-47fd-aa53-35a4407a6e0b");
        artifactDir.mkdirs();

        String blockDir = "src/main/resources/assets/interstice/textures/block/";
        BufferedImage riftstone = ImageIO.read(new File(blockDir + "riftstone.png"));
        BufferedImage turfTop = ImageIO.read(new File(blockDir + "abyssal_turf_top.png"));
        BufferedImage turfSide = ImageIO.read(new File(blockDir + "abyssal_turf_side.png"));
        BufferedImage ore = ImageIO.read(new File(blockDir + "riftsilver_ore.png"));
        BufferedImage sproutBud = ImageIO.read(new File(blockDir + "tide_sprout_bud.png"));

        String cc0Dir = "/tmp/cc0_blocks/extracted/blocks/";
        BufferedImage pineLogSide = ImageIO.read(new File(cc0Dir + "pine_log_side.png"));
        BufferedImage pineLogTop = ImageIO.read(new File(cc0Dir + "pine_log_top.png"));
        BufferedImage pinePlanks = ImageIO.read(new File(cc0Dir + "pine_planks.png"));
        BufferedImage beechLeaves = ImageIO.read(new File(cc0Dir + "beech_leaves.png"));
        BufferedImage pineLeaves = ImageIO.read(new File(cc0Dir + "pine_leaves.png"));
        BufferedImage sapling = ImageIO.read(new File(cc0Dir + "plants/sapling_pine.png"));
        BufferedImage fern = ImageIO.read(new File(cc0Dir + "plants/fern.png"));

        // Option A: Абиссальный Спрутовик (Abyssal Tendril) - Midnight Indigo & Bioluminescent Teal
        BufferedImage aLogSide = tintColor(pineLogSide, new Color(26, 22, 42), new Color(75, 68, 115), true);
        addBioluminescentVeins(aLogSide, new Color(30, 235, 195));
        BufferedImage aLogTop = tintLogTop(pineLogTop, new Color(26, 22, 42), new Color(25, 215, 185));
        BufferedImage aPlanks = tintPlanks(pinePlanks, new Color(36, 30, 54), new Color(68, 60, 95));
        BufferedImage aLeaves = tintLeaves(beechLeaves, 0.49f, 0.80f, 0.95f);
        BufferedImage aSapling = tintSapling(sapling, 0.75f, 0.50f);
        BufferedImage aVines = tintVines(fern, 0.49f, 0.85f);

        // Option B: Костяной Эфирник (Aetheric Ashenwood) - Ghost White Bark, Violet Heartwood & Crystal Leaves
        BufferedImage bLogSide = tintBoneBark(pineLogSide);
        BufferedImage bLogTop = tintLogTop(pineLogTop, new Color(180, 185, 195), new Color(130, 70, 180));
        BufferedImage bPlanks = tintPlanks(pinePlanks, new Color(175, 180, 190), new Color(225, 230, 238));
        BufferedImage bLeaves = tintLeaves(pineLeaves, 0.78f, 0.65f, 0.90f);
        BufferedImage bSapling = tintSapling(sapling, 0.80f, 0.30f);

        // Option C: Разломный Кордицепс (Fungal Spire) - Spore-Mycelium & Glowing Fungal Brackets
        BufferedImage cLogSide = tintColor(pineLogSide, new Color(45, 30, 35), new Color(110, 75, 80), false);
        BufferedImage cLogTop = tintLogTop(pineLogTop, new Color(45, 30, 35), new Color(220, 130, 40));
        BufferedImage cPlanks = tintPlanks(pinePlanks, new Color(90, 60, 55), new Color(150, 110, 95));
        BufferedImage cLeaves = tintLeaves(beechLeaves, 0.08f, 0.85f, 1.0f);
        BufferedImage cSapling = tintSapling(sapling, 0.08f, 0.70f);

        // Canvas: 960 x 780 (extra space for Island in-situ mockups)
        BufferedImage canvas = new BufferedImage(960, 780, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(11, 13, 19));
        g.fillRect(0, 0, 960, 780);

        // Title
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(new Color(130, 240, 220));
        g.drawString("ПОТУСТОРОННИЕ ДЕРЕВЬЯ МЕЖДУМОРЬЯ: КОНЦЕПТЫ И ТЕКСТУРЫ", 35, 36);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(160, 180, 200));
        g.drawString("Все текстуры строго 16x16, квадратные и бесшовные, CC0 открытые ассеты в палитре измерения", 35, 56);

        // Render Option Cards
        drawConceptCard(g, 35, 75, 280, 440,
                "Вариант А: «Абиссальный Спрутовик»",
                "(Рекомендуемый фаворит)",
                "Витой ствол, тянущийся к верхнему морю",
                "Кора: Индиго со светящимися бирюзовыми жилами",
                "Спил: Сияющий циановый сок в сердцевине",
                "Крона: Биолюминесцентная листва (свет 7)",
                "Доски: Глубокий благородный сумеречный индиго",
                aLogSide, aLogTop, aPlanks, aLeaves, aSapling, aVines,
                new Color(30, 230, 195),
                "Лозы дают Slow Falling (парение в бездне)!");

        drawConceptCard(g, 340, 75, 280, 440,
                "Вариант B: «Костяной Эфирник»",
                "(Призрачное окаменевшее древо)",
                "Древо из пепельных глубин разлома",
                "Кора: Пепельно-белая костяная текстура",
                "Спил: Контрастная аметистовая сердцевина",
                "Крона: Кристаллические сиреневые листья",
                "Доски: Светлый серебристо-пепельный планкен",
                bLogSide, bLogTop, bPlanks, bLeaves, bSapling, null,
                new Color(210, 140, 255),
                "При ударе издает хрустальный перезвон");

        drawConceptCard(g, 645, 75, 280, 440,
                "Вариант C: «Разломный Кордицепс»",
                "(Споровый гриб-великан)",
                "Мицелиальный ствол с грибными полками",
                "Кора: Тёмный волокнистый споровый мицелий",
                "Спил: Пористая янтарная сердцевина",
                "Крона: Споровая губка золотисто-янтарного цвета",
                "Доски: Тёплый охристо-древесный планкен",
                cLogSide, cLogTop, cPlanks, cLeaves, cSapling, null,
                new Color(255, 175, 60),
                "Грибные полки для паркура на скалы островов");

        // Bottom panel: In-situ world preview (How each looks on the Interstice island)
        int panelY = 530;
        int panelH = 225;
        g.setColor(new Color(18, 22, 32));
        g.fillRoundRect(35, panelY, 890, panelH, 12, 12);
        g.setColor(new Color(40, 52, 75));
        g.drawRoundRect(35, panelY, 890, panelH, 12, 12);

        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(new Color(255, 225, 140));
        g.drawString("КАК ДЕРЕВЬЯ СМОТРЯТСЯ НА ОСТРОВАХ МЕЖДУМОРЬЯ (Симуляция в игре):", 55, panelY + 24);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(150, 170, 195));
        g.drawString("Фон: бездна и светящееся Верхнее Море • Поверхность: Абиссальный дёрн • Недра: Разломный сланец", 55, panelY + 40);

        // Draw 3 island dioramas for A, B, C
        drawIslandDiorama(g, 60, panelY + 52, "Вариант А (Спрутовик)", aLogSide, aLeaves, aVines, turfSide, riftstone, ore, sproutBud);
        drawIslandDiorama(g, 365, panelY + 52, "Вариант B (Костяной Эфирник)", bLogSide, bLeaves, null, turfSide, riftstone, ore, sproutBud);
        drawIslandDiorama(g, 670, panelY + 52, "Вариант C (Кордицепс)", cLogSide, cLeaves, null, turfSide, riftstone, ore, sproutBud);

        g.dispose();
        File outFile = new File(artifactDir, "tree_proposals.png");
        ImageIO.write(canvas, "PNG", outFile);
        File repoOut = new File("art/proposals/tree_proposals.png");
        repoOut.getParentFile().mkdirs();
        ImageIO.write(canvas, "PNG", repoOut);
        System.out.println("Enhanced tree showcase saved to " + outFile.getAbsolutePath() + " and " + repoOut.getAbsolutePath());
    }

    static void drawIslandDiorama(Graphics2D g, int x, int y, String label,
                                  BufferedImage log, BufferedImage leaves, BufferedImage vines,
                                  BufferedImage turf, BufferedImage stone, BufferedImage ore, BufferedImage bud) {
        // Diorama box: 260 x 165
        int dw = 260, dh = 165;
        g.setColor(new Color(8, 10, 15));
        g.fillRect(x, y, dw, dh);
        g.setColor(new Color(30, 40, 55));
        g.drawRect(x, y, dw, dh);

        // Upper Sea glow on top
        GradientPaint seaGlow = new GradientPaint(x, y, new Color(6, 182, 212, 60), x, y + 30, new Color(6, 182, 212, 0));
        g.setPaint(seaGlow);
        g.fillRect(x, y, dw, 30);

        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(new Color(200, 220, 245));
        g.drawString(label, x + 8, y + 16);

        int b = 14; // block pixel size in diorama
        int groundY = y + 120;

        // Draw island cross-section (cols: 16)
        for (int c = 0; c < 16; c++) {
            int bx = x + 15 + c * b;
            // Turf
            g.drawImage(turf, bx, groundY, b, b, null);
            // Stone layer 1
            g.drawImage(stone, bx, groundY + b, b, b, null);
            // Stone layer 2
            BufferedImage sub = (c == 4 || c == 5) ? ore : stone;
            g.drawImage(sub, bx, groundY + b * 2, b, b, null);
        }

        // Tide sprout
        g.drawImage(bud, x + 15 + 2 * b, groundY - b, b, b, null);
        g.drawImage(bud, x + 15 + 13 * b, groundY - b, b, b, null);

        // Draw Tree (Trunk + Canopy)
        int tx = x + 15 + 7 * b;
        int ty = groundY;

        // Trunk (curving slightly upward towards Upper Sea)
        g.drawImage(log, tx, ty - b, b, b, null);
        g.drawImage(log, tx, ty - b * 2, b, b, null);
        g.drawImage(log, tx + 2, ty - b * 3, b, b, null);
        g.drawImage(log, tx + 4, ty - b * 4, b, b, null);

        // Canopy (leaves cluster)
        int cx = tx - b;
        int cy = ty - b * 6;
        for (int lx = -2; lx <= 2; lx++) {
            for (int ly = 0; ly <= 2; ly++) {
                if (Math.abs(lx) == 2 && ly == 0) continue;
                g.drawImage(leaves, cx + lx * b + 4, cy + ly * b, b, b, null);
            }
        }
        g.drawImage(leaves, cx + 4, cy - b, b, b, null);

        // Vines hanging
        if (vines != null) {
            g.drawImage(vines, cx - b + 4, cy + 3 * b, b, b, null);
            g.drawImage(vines, cx + 2 * b + 4, cy + 3 * b, b, b, null);
        }
    }

    static void drawConceptCard(Graphics2D g, int x, int y, int w, int h,
                                String title, String badge, String desc1,
                                String desc2, String desc3, String desc4, String desc5,
                                BufferedImage logSide, BufferedImage logTop, BufferedImage planks,
                                BufferedImage leaves, BufferedImage sapling, BufferedImage vines,
                                Color accent, String featureNote) {
        g.setColor(new Color(22, 26, 36));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(new Color(45, 55, 75));
        g.drawRoundRect(x, y, w, h, 12, 12);

        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(Color.WHITE);
        g.drawString(title, x + 12, y + 22);

        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(accent);
        g.drawString(badge, x + 12, y + 36);

        // Row 1: Log side, Log top, Planks, Leaves (3x scale: 48x48)
        int s = 3;
        int r1Y = y + 50;
        drawTileWithLabel(g, x + 14, r1Y, logSide, s, "Кора");
        drawTileWithLabel(g, x + 76, r1Y, logTop, s, "Спил");
        drawTileWithLabel(g, x + 138, r1Y, planks, s, "Доски");
        drawTileWithLabel(g, x + 200, r1Y, leaves, s, "Листва");

        // Row 2: Sapling & Vine (if any)
        int r2Y = y + 125;
        drawTileWithLabel(g, x + 50, r2Y, sapling, s, "Саженец");
        if (vines != null) {
            drawTileWithLabel(g, x + 150, r2Y, vines, s, "Лоза");
        }

        // Description lines
        int textY = y + 205;
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(new Color(195, 205, 220));
        g.drawString("• " + desc1, x + 12, textY);
        g.drawString("• " + desc2, x + 12, textY + 17);
        g.drawString("• " + desc3, x + 12, textY + 34);
        g.drawString("• " + desc4, x + 12, textY + 51);
        g.drawString("• " + desc5, x + 12, textY + 68);

        // Feature Highlight Box
        int boxY = y + 375;
        g.setColor(new Color(14, 18, 26));
        g.fillRoundRect(x + 10, boxY, w - 20, 52, 8, 8);
        g.setColor(accent);
        g.drawRoundRect(x + 10, boxY, w - 20, 52, 8, 8);

        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(accent);
        g.drawString("✨ Механика:", x + 16, boxY + 18);
        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g.setColor(Color.WHITE);
        g.drawString(featureNote, x + 16, boxY + 36);
    }

    static void drawTileWithLabel(Graphics2D g, int x, int y, BufferedImage img, int s, String label) {
        g.setColor(new Color(32, 38, 52));
        g.fillRect(x - 2, y - 2, 16 * s + 4, 16 * s + 4);
        g.drawImage(img, x, y, 16 * s, 16 * s, null);
        g.setColor(new Color(55, 68, 90));
        g.drawRect(x - 2, y - 2, 16 * s + 4, 16 * s + 4);

        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g.setColor(new Color(155, 175, 195));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, x + (16 * s - fm.stringWidth(label)) / 2, y + 16 * s + 12);
    }

    // --- Tinting Utilities ---

    static BufferedImage tintColor(BufferedImage src, Color dark, Color light, boolean addNoise) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a < 10) { out.setRGB(x, y, 0); continue; }
                int r = (argb >> 16) & 0xFF;
                float brightness = r / 255.0f;

                int nr = (int) (dark.getRed() + brightness * (light.getRed() - dark.getRed()));
                int ng = (int) (dark.getGreen() + brightness * (light.getGreen() - dark.getGreen()));
                int nb = (int) (dark.getBlue() + brightness * (light.getBlue() - dark.getBlue()));
                out.setRGB(x, y, (a << 24) | (clamp(nr) << 16) | (clamp(ng) << 8) | clamp(nb));
            }
        }
        return out;
    }

    static void addBioluminescentVeins(BufferedImage img, Color glow) {
        int[][] pattern = {
                {4, 1}, {4, 2}, {5, 3}, {5, 4}, {5, 5}, {4, 6}, {4, 7}, {4, 8}, {5, 9}, {5, 10}, {5, 11}, {4, 12}, {4, 13}, {4, 14},
                {11, 2}, {11, 3}, {10, 4}, {10, 5}, {10, 6}, {11, 7}, {11, 8}, {10, 9}, {10, 10}, {10, 11}, {11, 12}, {11, 13}
        };
        for (int[] p : pattern) {
            int px = p[0] % img.getWidth();
            int py = p[1] % img.getHeight();
            img.setRGB(px, py, 0xFF000000 | (glow.getRed() << 16) | (glow.getGreen() << 8) | glow.getBlue());
        }
    }

    static BufferedImage tintLogTop(BufferedImage src, Color barkColor, Color coreColor) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int cx = 7, cy = 7;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a < 10) { out.setRGB(x, y, 0); continue; }
                int r = (argb >> 16) & 0xFF;
                float b = r / 255.0f;

                double dist = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                Color base = (dist > 5.5) ? barkColor : coreColor;
                int nr = (int) (base.getRed() * (0.6f + 0.4f * b));
                int ng = (int) (base.getGreen() * (0.6f + 0.4f * b));
                int nb = (int) (base.getBlue() * (0.6f + 0.4f * b));
                out.setRGB(x, y, (a << 24) | (clamp(nr) << 16) | (clamp(ng) << 8) | clamp(nb));
            }
        }
        return out;
    }

    static BufferedImage tintPlanks(BufferedImage src, Color dark, Color light) {
        return tintColor(src, dark, light, false);
    }

    static BufferedImage tintLeaves(BufferedImage src, float hue, float sat, float val) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a < 50) { out.setRGB(x, y, 0); continue; }
                int r = (argb >> 16) & 0xFF;
                float brightness = (r / 255.0f) * val;
                int rgb = Color.HSBtoRGB(hue, sat, Math.min(1.0f, brightness));
                out.setRGB(x, y, (a << 24) | (rgb & 0x00FFFFFF));
            }
        }
        return out;
    }

    static BufferedImage tintSapling(BufferedImage src, float hue, float sat) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a < 10) { out.setRGB(x, y, 0); continue; }
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                float[] hsb = Color.RGBtoHSB(r, g, b, null);
                hsb[0] = hue;
                hsb[1] = Math.min(1.0f, hsb[1] * 1.2f + 0.2f);
                int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
                out.setRGB(x, y, (a << 24) | (rgb & 0x00FFFFFF));
            }
        }
        return out;
    }

    static BufferedImage tintVines(BufferedImage src, float hue, float sat) {
        return tintSapling(src, hue, sat);
    }

    static BufferedImage tintBoneBark(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a < 10) { out.setRGB(x, y, 0); continue; }
                int r = (argb >> 16) & 0xFF;
                float b = r / 255.0f;
                int grey = (int) (140 + b * 95);
                int cr = clamp(grey + 5);
                int cg = clamp(grey + 5);
                int cb = clamp(grey + 15);
                out.setRGB(x, y, (a << 24) | (cr << 16) | (cg << 8) | cb);
            }
        }
        return out;
    }

    static int clamp(int val) {
        return Math.max(0, Math.min(255, val));
    }
}
