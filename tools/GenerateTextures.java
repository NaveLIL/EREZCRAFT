package tools;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GenerateTextures {
    public static void main(String[] args) throws Exception {
        File assetsDir = new File("src/main/resources/assets/interstice/textures");
        File itemDir = new File(assetsDir, "item");
        File blockDir = new File(assetsDir, "block");
        itemDir.mkdirs();
        blockDir.mkdirs();

        BufferedImage vanillaIronIngot = ImageIO.read(new File("/tmp/vanilla_iron_ingot.png"));
        BufferedImage vanillaRawIron = ImageIO.read(new File("/tmp/vanilla_raw_iron.png"));
        BufferedImage vanillaStone = ImageIO.read(new File("/tmp/vanilla_stone.png"));
        BufferedImage vanillaIronOre = ImageIO.read(new File("/tmp/vanilla_iron_ore.png"));
        BufferedImage vanillaBucket = ImageIO.read(new File("/tmp/vanilla_bucket.png"));

        // 1. Riftsilver Ingot (Sleek silvery-violet lustrous sheen)
        BufferedImage riftsilverIngot = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int argb = vanillaIronIngot.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a == 0) continue;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                double lum = (r * 0.299 + g * 0.587 + b * 0.114) / 255.0;

                int nr, ng, nb;
                if (lum > 0.82) {
                    nr = (int) (245 * lum); ng = (int) (238 * lum); nb = (int) (255 * lum);
                } else if (lum > 0.6) {
                    nr = (int) (215 * lum); ng = (int) (200 * lum); nb = (int) (240 * lum);
                } else if (lum > 0.4) {
                    nr = (int) (170 * lum); ng = (int) (150 * lum); nb = (int) (205 * lum);
                } else if (lum > 0.22) {
                    nr = (int) (120 * lum); ng = (int) (105 * lum); nb = (int) (160 * lum);
                } else {
                    nr = (int) (70 * lum); ng = (int) (60 * lum); nb = (int) (100 * lum);
                }
                riftsilverIngot.setRGB(x, y, (a << 24) | (clamp(nr) << 16) | (clamp(ng) << 8) | clamp(nb));
            }
        }
        ImageIO.write(riftsilverIngot, "PNG", new File(itemDir, "riftsilver_ingot.png"));

        // 2. Raw Riftsilver (Raw crystalline cluster with violet-silver metallic luster)
        BufferedImage rawRiftsilver = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int argb = vanillaRawIron.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a == 0) continue;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                double lum = (r * 0.299 + g * 0.587 + b * 0.114) / 255.0;

                int nr, ng, nb;
                if (lum > 0.78) {
                    nr = (int) (250 * lum); ng = (int) (235 * lum); nb = (int) (255 * lum);
                } else if (lum > 0.52) {
                    nr = (int) (210 * lum); ng = (int) (185 * lum); nb = (int) (245 * lum);
                } else if (lum > 0.32) {
                    nr = (int) (160 * lum); ng = (int) (130 * lum); nb = (int) (205 * lum);
                } else {
                    nr = (int) (95 * lum); ng = (int) (75 * lum); nb = (int) (140 * lum);
                }
                rawRiftsilver.setRGB(x, y, (a << 24) | (clamp(nr) << 16) | (clamp(ng) << 8) | clamp(nb));
            }
        }
        ImageIO.write(rawRiftsilver, "PNG", new File(itemDir, "raw_riftsilver.png"));

        // 3. Riftsilver Ore (Natural stone base with shimmering rift crystals)
        BufferedImage riftsilverOre = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int stoneRgb = vanillaStone.getRGB(x, y);
                int oreRgb = vanillaIronOre.getRGB(x, y);

                int sr = (stoneRgb >> 16) & 0xFF;
                int sg = (stoneRgb >> 8) & 0xFF;
                int sb = stoneRgb & 0xFF;

                int or = (oreRgb >> 16) & 0xFF;
                int og = (oreRgb >> 8) & 0xFF;
                int ob = oreRgb & 0xFF;

                int diff = Math.abs(or - sr) + Math.abs(og - sg) + Math.abs(ob - sb);
                if (diff > 18) {
                    double lum = (or * 0.299 + og * 0.587 + ob * 0.114) / 255.0;
                    int nr, ng, nb;
                    if (lum > 0.7) {
                        nr = 240; ng = 230; nb = 255;
                    } else if (lum > 0.5) {
                        nr = 195; ng = 175; nb = 240;
                    } else if (lum > 0.3) {
                        nr = 145; ng = 120; nb = 200;
                    } else {
                        nr = 90; ng = 70; nb = 140;
                    }
                    riftsilverOre.setRGB(x, y, 0xFF000000 | (nr << 16) | (ng << 8) | nb);
                } else {
                    riftsilverOre.setRGB(x, y, stoneRgb);
                }
            }
        }
        ImageIO.write(riftsilverOre, "PNG", new File(blockDir, "riftsilver_ore.png"));

        // 4. Riftsilver Bucket (Empty bucket forged from riftsilver)
        BufferedImage riftsilverBucket = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int argb = vanillaBucket.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a == 0) continue;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                double lum = (r * 0.299 + g * 0.587 + b * 0.114) / 255.0;

                int nr, ng, nb;
                if (lum > 0.8) {
                    nr = (int) (245 * lum); ng = (int) (235 * lum); nb = (int) (255 * lum);
                } else if (lum > 0.58) {
                    nr = (int) (210 * lum); ng = (int) (195 * lum); nb = (int) (235 * lum);
                } else if (lum > 0.38) {
                    nr = (int) (165 * lum); ng = (int) (145 * lum); nb = (int) (195 * lum);
                } else if (lum > 0.18) {
                    nr = (int) (115 * lum); ng = (int) (100 * lum); nb = (int) (150 * lum);
                } else {
                    nr = (int) (65 * lum); ng = (int) (55 * lum); nb = (int) (95 * lum);
                }
                riftsilverBucket.setRGB(x, y, (a << 24) | (clamp(nr) << 16) | (clamp(ng) << 8) | clamp(nb));
            }
        }
        ImageIO.write(riftsilverBucket, "PNG", new File(itemDir, "riftsilver_bucket.png"));

        // 5. Riftsilver Heavy Bucket (Contains deep crimson heavy toxin)
        BufferedImage riftsilverHeavy = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                riftsilverHeavy.setRGB(x, y, riftsilverBucket.getRGB(x, y));
            }
        }
        // Fill liquid interior
        int[][] heavyFluidMap = {
            // y = 8
            {5, 0xFFB31B32}, {6, 0xFFC7243D}, {7, 0xFFB31B32}, {8, 0xFF9E1428}, {9, 0xFF870D1E}, {10, 0xFF730816},
            // y = 9
            {5, 0xFF9E1428}, {6, 0xFFB31B32}, {7, 0xFF9E1428}, {8, 0xFF870D1E}, {9, 0xFF730816}, {10, 0xFF5E0510},
            // y = 10
            {5, 0xFF870D1E}, {6, 0xFF9E1428}, {7, 0xFF870D1E}, {8, 0xFF730816}, {9, 0xFF5E0510}, {10, 0xFF4A020B},
            // y = 11
            {6, 0xFF730816}, {7, 0xFF870D1E}, {8, 0xFF730816}, {9, 0xFF5E0510}
        };
        int curY = 8;
        int idxInRow = 0;
        for (int[] p : heavyFluidMap) {
            riftsilverHeavy.setRGB(p[0], curY, p[1]);
            idxInRow++;
            if ((curY == 8 && idxInRow == 6) || (curY == 9 && idxInRow == 12) || (curY == 10 && idxInRow == 18)) {
                curY++;
            }
        }
        ImageIO.write(riftsilverHeavy, "PNG", new File(itemDir, "riftsilver_heavy_bucket.png"));

        // 6. Inverted Riftsilver Bucket (Upside-down bucket containing glowing cyan/violet light toxin)
        BufferedImage riftsilverInverted = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                // Invert vertically: y -> 15 - y
                int rgb = riftsilverBucket.getRGB(x, y);
                riftsilverInverted.setRGB(x, 15 - y, rgb);
            }
        }
        // Fill inverted liquid chamber at the top/center (y: 4..7, x: 5..10)
        // With glowing turquoise/cyan light toxin and violet rim
        int[][] lightFluidMap = {
            // y = 4 (top surface of inverted fluid)
            {6, 0xFF38F2E2}, {7, 0xFF52FBEB}, {8, 0xFF38F2E2}, {9, 0xFF24D9C8},
            // y = 5
            {5, 0xFF24D9C8}, {6, 0xFF38F2E2}, {7, 0xFF24D9C8}, {8, 0xFF17B8A8}, {9, 0xFF2F8FA8}, {10, 0xFF5E65B5},
            // y = 6
            {5, 0xFF17B8A8}, {6, 0xFF24D9C8}, {7, 0xFF17B8A8}, {8, 0xFF386CB0}, {9, 0xFF6C4FA8}, {10, 0xFF7B3B9B},
            // y = 7
            {5, 0xFF2A5FA0}, {6, 0xFF4D4E9F}, {7, 0xFF653D94}, {8, 0xFF772F88}, {9, 0xFF83227C}, {10, 0xFF6B1B66}
        };
        curY = 4;
        idxInRow = 0;
        for (int[] p : lightFluidMap) {
            riftsilverInverted.setRGB(p[0], curY, p[1]);
            idxInRow++;
            if ((curY == 4 && idxInRow == 4) || (curY == 5 && idxInRow == 10) || (curY == 6 && idxInRow == 16)) {
                curY++;
            }
        }
        ImageIO.write(riftsilverInverted, "PNG", new File(itemDir, "riftsilver_inverted_bucket.png"));

        System.out.println("All 6 Riftsilver textures generated successfully!");
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
