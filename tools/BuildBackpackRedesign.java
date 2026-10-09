import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

public class BuildBackpackRedesign {
    public static void main(String[] args) throws Exception {
        File artifactDir = new File("/Users/dimon/.gemini/antigravity/brain/4bbdced6-5c2d-47fd-aa53-35a4407a6e0b");
        artifactDir.mkdirs();

        // 1. Load current textures
        BufferedImage curFieldItem = ImageIO.read(new File("src/main/resources/assets/interstice/textures/item/expedition/field_backpack.png"));
        BufferedImage curExpeditionItem = ImageIO.read(new File("src/main/resources/assets/interstice/textures/item/expedition/expedition_backpack.png"));
        BufferedImage curCloth = ImageIO.read(new File("src/main/resources/assets/interstice/textures/gui/backpack_cloth.png"));
        BufferedImage originalBag = ImageIO.read(new File("art/sources/cc0/backpacks/bag.png"));

        // 2. Generate Proposed Redesigned Items using semantic mapping
        // In original bag.png:
        // Color 1: Outline
        // Color 2: Body/Cloth base
        // Color 3: Straps/Highlights
        // Color 4: Shadows
        // Color 8: Buckle/Clasp

        // Option 1: «Экспедиционная Классика Междуморья»
        // Field Backpack (54 slots): Indigo hemotrophic canvas + saddle leather straps + riftsilver buckle
        BufferedImage newField1 = mapBagSemantic(originalBag,
                new Color(24, 20, 36),  // outline: dark charcoal-violet
                new Color(78, 68, 104), // body base: rich twilight-indigo canvas
                new Color(175, 105, 52),// straps: warm saddle expedition leather
                new Color(45, 38, 62),  // shadow: deep twilight shadow
                new Color(210, 240, 245)// buckle: gleaming riftsilver
        );

        // Expedition Backpack (72 slots): Abyssal teal reinforced canvas + riftsilver reinforced straps + vitriolite golden crystal buckle
        BufferedImage newExped1 = mapBagSemantic(originalBag,
                new Color(14, 28, 34),  // outline: dark abyss cyan-charcoal
                new Color(40, 95, 105), // body base: abyssal deep teal reinforced fabric
                new Color(150, 205, 215),// straps: riftsilver reinforced webbing
                new Color(22, 54, 60),  // shadow: dark deep teal
                new Color(255, 195, 45) // buckle: glowing vitriolite amber crystal!
        );

        // Option 2: «Разломный Кожевник / Разведчик»
        // Field: Deep graphite-slate fabric + dark amber leather straps + riftsilver
        BufferedImage newField2 = mapBagSemantic(originalBag,
                new Color(22, 22, 26),
                new Color(60, 62, 72),
                new Color(190, 120, 60),
                new Color(38, 40, 48),
                new Color(220, 235, 245)
        );
        // Expedition: Royal Amethyst fabric + burnished gold straps + riftsilver core
        BufferedImage newExped2 = mapBagSemantic(originalBag,
                new Color(26, 16, 34),
                new Color(90, 48, 110),
                new Color(210, 160, 70),
                new Color(52, 28, 65),
                new Color(140, 230, 240)
        );

        // 3. New fine cloth weave textures from Kenney pack
        ZipFile zip = new ZipFile("art/sources/cc0/gui-materials/kenney_pattern-pack-pixel.zip");
        // Tile 0 (fine micro-checkerboard canvas)
        BufferedImage tile0 = ImageIO.read(zip.getInputStream(zip.getEntry("Tiles (Grayscale)/tile_0000.png")));
        // Tile 26 (fine woven twill)
        BufferedImage tile26 = ImageIO.read(zip.getInputStream(zip.getEntry("Tiles (Grayscale)/tile_0026.png")));

        BufferedImage newClothFine = tintPattern(tile0, new Color(42, 45, 52), new Color(62, 66, 76));
        BufferedImage newClothWoven = tintPattern(tile26, new Color(38, 42, 48), new Color(58, 64, 72));

        // 4. Compose Master Showcase Canvas (960 x 620)
        BufferedImage canvas = new BufferedImage(960, 620, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(14, 16, 22));
        g.fillRect(0, 0, 960, 620);

        // Title
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(new Color(130, 240, 220));
        g.drawString("ДОРАБОТКА ВНЕШНЕГО ВИДА РЮКЗАКОВ МЕЖДУМОРЬЯ", 35, 36);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(160, 180, 200));
        g.drawString("Сравнение текущего технического вида с новыми вариантами дизайна (16x16 CC0)", 35, 56);

        // Card 1: ТЕКУЩИЙ ВИД (БЫЛО) - Слева
        drawComparisonCard(g, 35, 75, 270, 510,
                "Текущий технический вид",
                "(Блёклый монохром)",
                new Color(140, 150, 165),
                curFieldItem, curExpeditionItem, curCloth,
                "• Монохромный градиент на весь спрайт",
                "• Ремни, пряжка и ткань одного цвета",
                "• Блёклая сливово-болотная палитра",
                "• Диагональные полосы фанеры на спине",
                "• Нет явного отличия уровней",
                new Color(75, 70, 85), new Color(60, 75, 75)
        );

        // Card 2: ВАРИАНТ 1 (РЕКОМЕНДУЕМЫЙ) - Центр
        drawComparisonCard(g, 335, 75, 290, 510,
                "Вариант 1: «Экспедиционная Классика»",
                "(Рекомендуемый выбор)",
                new Color(30, 230, 195),
                newField1, newExped1, newClothFine,
                "• Полевой: сумеречное полотно + кожа + серебро",
                "• Экспедиционный: абиссальный бирюзовый цвет",
                "• Армированные серебряные стропы",
                "• Сияющая витриолитовая янтарная пряжка",
                "• Ткань: аккуратная плотная парусина",
                new Color(78, 68, 104), new Color(40, 95, 105)
        );

        // Card 3: ВАРИАНТ 2 - Справа
        drawComparisonCard(g, 655, 75, 270, 510,
                "Вариант 2: «Следопыт Разлома»",
                "(Тёмный контраст)",
                new Color(255, 180, 70),
                newField2, newExped2, newClothWoven,
                "• Полевой: графитовый сланец + тёмная кожа",
                "• Экспедиционный: аметистово-королевский тон",
                "• Золотисто-бронзовые экспедиционные ремни",
                "• Рифтосеребряный сияющий карабин",
                "• Ткань: фактурное походное плетение",
                new Color(60, 62, 72), new Color(90, 48, 110)
        );

        g.dispose();

        File outFile = new File(artifactDir, "backpack_proposals.png");
        ImageIO.write(canvas, "PNG", outFile);
        File repoOut = new File("art/proposals/backpack_proposals.png");
        repoOut.getParentFile().mkdirs();
        ImageIO.write(canvas, "PNG", repoOut);
        System.out.println("Backpack proposals saved to " + outFile.getAbsolutePath() + " and " + repoOut.getAbsolutePath());
    }

    static void drawComparisonCard(Graphics2D g, int x, int y, int w, int h,
                                  String title, String badge, Color accent,
                                  BufferedImage fieldItem, BufferedImage expedItem, BufferedImage cloth,
                                  String d1, String d2, String d3, String d4, String d5,
                                  Color field3dColor, Color exped3dColor) {
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

        // Row 1: Item Icons (Zoomed 4x -> 64x64)
        int s = 4;
        int r1Y = y + 50;

        // Field Item Icon
        drawSlot(g, x + 15, r1Y, fieldItem, s, "Полевой (54)");
        // Expedition Item Icon
        drawSlot(g, x + 100, r1Y, expedItem, s, "Экспедиционный (72)");
        // Cloth Fabric Texture (tiled 48x48)
        drawClothSample(g, x + 185, r1Y, cloth, "Ткань (GUI/Тело)");

        // Row 2: 3D Player Back Preview Simulation
        int r2Y = y + 155;
        g.setColor(new Color(16, 20, 28));
        g.fillRoundRect(x + 12, r2Y, w - 24, 160, 8, 8);
        g.setColor(new Color(38, 48, 65));
        g.drawRoundRect(x + 12, r2Y, w - 24, 160, 8, 8);

        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(new Color(200, 220, 245));
        g.drawString("Вид надетого рюкзака на спине:", x + 20, r2Y + 16);

        // Draw Player Torso with Worn Backpack (Field left, Expedition right)
        drawPlayerBackpackSimulation(g, x + 25, r2Y + 28, "Полевой", field3dColor, false);
        drawPlayerBackpackSimulation(g, x + 145, r2Y + 28, "Экспедиционный", exped3dColor, true);

        // Description bullets
        int textY = y + 340;
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(new Color(195, 205, 220));
        g.drawString(d1, x + 12, textY);
        g.drawString(d2, x + 12, textY + 18);
        g.drawString(d3, x + 12, textY + 36);
        g.drawString(d4, x + 12, textY + 54);
        g.drawString(d5, x + 12, textY + 72);

        // Feature Highlight
        int boxY = y + 435;
        g.setColor(new Color(14, 18, 26));
        g.fillRoundRect(x + 10, boxY, w - 20, 60, 8, 8);
        g.setColor(accent);
        g.drawRoundRect(x + 10, boxY, w - 20, 60, 8, 8);

        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(accent);
        g.drawString("✨ Результат:", x + 16, boxY + 18);
        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g.setColor(Color.WHITE);
        if (title.contains("Текущий")) {
            g.drawString("Блёклый плоский контур,", x + 16, boxY + 34);
            g.drawString("не передает материалы крафта.", x + 16, boxY + 48);
        } else if (title.contains("Вариант 1")) {
            g.drawString("Чёткие кожаные ремни, рифтосеребро", x + 16, boxY + 34);
            g.drawString("и янтарный замок. Дорогой походный вид!", x + 16, boxY + 48);
        } else {
            g.drawString("Контрастный строгий тёмный стиль", x + 16, boxY + 34);
            g.drawString("с драгоценными вставками.", x + 16, boxY + 48);
        }
    }

    static void drawSlot(Graphics2D g, int x, int y, BufferedImage img, int s, String label) {
        int w = 16 * s;
        g.setColor(new Color(30, 36, 48));
        g.fillRect(x - 2, y - 2, w + 4, w + 4);
        g.drawImage(img, x, y, w, w, null);
        g.setColor(new Color(55, 68, 90));
        g.drawRect(x - 2, y - 2, w + 4, w + 4);

        g.setFont(new Font("SansSerif", Font.PLAIN, 8));
        g.setColor(new Color(160, 180, 205));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, x + (w - fm.stringWidth(label)) / 2, y + w + 12);
    }

    static void drawClothSample(Graphics2D g, int x, int y, BufferedImage cloth, String label) {
        int w = 64;
        g.setColor(new Color(30, 36, 48));
        g.fillRect(x - 2, y - 2, w + 4, w + 4);
        // Tile 16x16 4 times
        for (int ty = 0; ty < w; ty += 16) {
            for (int tx = 0; tx < w; tx += 16) {
                g.drawImage(cloth, x + tx, y + ty, null);
            }
        }
        g.setColor(new Color(55, 68, 90));
        g.drawRect(x - 2, y - 2, w + 4, w + 4);

        g.setFont(new Font("SansSerif", Font.PLAIN, 8));
        g.setColor(new Color(160, 180, 205));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, x + (w - fm.stringWidth(label)) / 2, y + w + 12);
    }

    static void drawPlayerBackpackSimulation(Graphics2D g, int x, int y, String name, Color clothColor, boolean reinforced) {
        // Draw miniature player torso seen from behind
        int scale = 3;
        // Torso: 8 wide, 12 tall -> 24x36
        int tx = x + 25;
        int ty = y + 15;

        // Player Head
        g.setColor(new Color(140, 110, 160));
        g.fillRect(tx + 6, ty - 18, 12, 18);

        // Player Torso (Armor / Shirt)
        g.setColor(new Color(40, 150, 160)); // diamond armor
        g.fillRect(tx, ty, 24, 36);

        // Arms
        g.fillRect(tx - 10, ty, 10, 36);
        g.fillRect(tx + 24, ty, 10, 36);

        // Backpack on back:
        int bpW = reinforced ? 22 : 18;
        int bpH = 30;
        int bpX = tx + (24 - bpW) / 2;
        int bpY = ty + 3;

        // Main body
        g.setColor(clothColor);
        g.fillRect(bpX, bpY, bpW, bpH);
        g.setColor(clothColor.darker());
        g.drawRect(bpX, bpY, bpW, bpH);

        // Top Lid / Flap
        g.setColor(new Color((int)(clothColor.getRed() * 0.85), (int)(clothColor.getGreen() * 0.85), (int)(clothColor.getBlue() * 0.85)));
        g.fillRect(bpX - 1, bpY, bpW + 2, 7);

        // Outer Pocket
        int pW = 12;
        int pH = 14;
        int pX = bpX + (bpW - pW) / 2;
        int pY = bpY + 12;
        g.setColor(clothColor.brighter());
        g.fillRect(pX, pY, pW, pH);
        g.setColor(clothColor.darker());
        g.drawRect(pX, pY, pW, pH);

        // Straps / Buckles
        g.setColor(new Color(160, 95, 45)); // leather straps
        g.fillRect(pX + 2, bpY + 5, 2, bpH - 5);
        g.fillRect(pX + pW - 4, bpY + 5, 2, bpH - 5);

        // Central Buckle
        if (reinforced) {
            // Vitriolite gold / amber clasp
            g.setColor(new Color(255, 200, 50));
            g.fillRect(pX + (pW - 4) / 2, pY + 2, 4, 3);
            // Side pockets
            g.setColor(clothColor.darker());
            g.fillRect(bpX - 4, bpY + 8, 4, 18);
            g.fillRect(bpX + bpW, bpY + 8, 4, 18);
            // Riftsilver trim
            g.setColor(new Color(210, 240, 245));
            g.drawRect(bpX - 4, bpY + 8, 4, 18);
            g.drawRect(bpX + bpW, bpY + 8, 4, 18);
        } else {
            // Riftsilver clasp
            g.setColor(new Color(210, 240, 245));
            g.fillRect(pX + (pW - 4) / 2, pY + 2, 4, 3);
        }

        // Label
        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g.setColor(new Color(180, 195, 215));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(name, x + 37 - fm.stringWidth(name) / 2, y + 105);
    }

    static BufferedImage mapBagSemantic(BufferedImage original, Color outline, Color body, Color straps, Color shadow, Color buckle) {
        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = original.getRGB(x, y);
                int a = (c >> 24) & 0xff;
                if (a < 10) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                // Check original color by RGB
                int rgb = c & 0x00ffffff;
                Color target;
                if (rgb == 0x000000) {
                    target = outline;
                } else if (rgb == 0xa46422) {
                    target = body;
                } else if (rgb == 0xeb8931) {
                    target = straps;
                } else if (rgb == 0x493c2b) {
                    target = shadow;
                } else if (rgb == 0xf7e26b) {
                    target = buckle;
                } else {
                    target = body;
                }
                out.setRGB(x, y, (a << 24) | (target.getRed() << 16) | (target.getGreen() << 8) | target.getBlue());
            }
        }
        return out;
    }

    static BufferedImage tintPattern(BufferedImage src, Color dark, Color light) {
        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xff;
                if (a < 10) { out.setRGB(x, y, 0); continue; }
                int r = (argb >> 16) & 0xff;
                float b = r / 255.0f;
                int nr = (int)(dark.getRed() + b * (light.getRed() - dark.getRed()));
                int ng = (int)(dark.getGreen() + b * (light.getGreen() - dark.getGreen()));
                int nb = (int)(dark.getBlue() + b * (light.getBlue() - dark.getBlue()));
                out.setRGB(x, y, (a << 24) | (clamp(nr) << 16) | (clamp(ng) << 8) | clamp(nb));
            }
        }
        return out;
    }

    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
