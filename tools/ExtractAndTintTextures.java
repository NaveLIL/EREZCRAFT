import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Extracts 16x16 tiles from the CC0 stone/ore/gem tilesheet and plant tiles,
 * applies teal/purple hue-shift for the Interstice palette, and writes them
 * as block textures into src/main/resources.
 *
 * Tilesheet grid: 16x16 px per tile, tiles are 0-indexed (row, col).
 * Run from project root:  java tools/ExtractAndTintTextures.java
 */
public class ExtractAndTintTextures {
    static final int T = 16; // tile size

    public static void main(String[] args) throws IOException {
        String base = args.length > 0 ? args[0] : ".";
        Path out = Path.of(base, "src/main/resources/assets/interstice/textures/block");
        Files.createDirectories(out);

        // --- Tilesheet sources ---
        BufferedImage stone  = load("/tmp/stone_ore_gems/Stone_ore_gems/Stones_ores_gems_without_grass.png");
        BufferedImage grass  = load("/tmp/stone_ore_gems/Stone_ore_gems/Stone_ore_gems_grass.png");

        // Riftsilver ore  — row 7, col 0 of stone sheet (sinuous silver veins in dark rock)
        tile(stone, 7, 0).ifPresent(img -> save(out.resolve("riftsilver_ore.png"), tintHSB(img, 0.62f, 0.35f)));

        // Riftstone — row 4, col 0 (dark angular slate-like stone)
        tile(stone, 4, 0).ifPresent(img -> save(out.resolve("riftstone.png"), tintHSB(img, 0.68f, 0.18f)));

        // Abyssal turf top — row 8, col 0 of grass sheet (mossy cap)
        tile(grass, 8, 0).ifPresent(img -> save(out.resolve("abyssal_turf_top.png"), tintHSB(img, 0.50f, 0.55f)));

        // Abyssal turf side — row 7, col 0 of grass sheet (stone-side with top moss strip)
        tile(grass, 7, 0).ifPresent(img -> save(out.resolve("abyssal_turf_side.png"), tintHSB(img, 0.50f, 0.40f)));

        // --- Plant tiles ---
        // Each plant tile is already 16x16, just tint.
        String plantBase = "/tmp/plant_tiles/plant_individual_tiles/";
        tintAndSave(plantBase + "root1.png",          out, "tide_sprout_root.png",       0.51f, 0.50f);
        tintAndSave(plantBase + "stem1.png",          out, "tide_sprout_stem.png",        0.51f, 0.50f);
        tintAndSave(plantBase + "stem1_leaf_both.png",out, "tide_sprout_stem_leaf.png",   0.50f, 0.60f);
        tintAndSave(plantBase + "bud.png",            out, "tide_sprout_bud.png",         0.50f, 0.70f);
        tintAndSave(plantBase + "flower1.png",        out, "tide_sprout_flower.png",      0.82f, 0.60f);

        System.out.println("Done! Textures written to " + out.toAbsolutePath());
    }

    // --- helpers ---

    static java.util.Optional<BufferedImage> tile(BufferedImage sheet, int row, int col) {
        int x = col * T, y = row * T;
        if (x + T > sheet.getWidth() || y + T > sheet.getHeight()) {
            System.err.println("Tile [" + row + "," + col + "] out of bounds " + sheet.getWidth() + "x" + sheet.getHeight());
            return java.util.Optional.empty();
        }
        BufferedImage out = new BufferedImage(T, T, BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(sheet, 0, 0, T, T, x, y, x + T, y + T, null);
        return java.util.Optional.of(out);
    }

    /** Shift hue of all non-transparent pixels to targetHue [0..1], scale saturation. */
    static BufferedImage tintHSB(BufferedImage src, float targetHue, float saturationScale) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                if (alpha < 10) { out.setRGB(x, y, 0); continue; }
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                float[] hsb = Color.RGBtoHSB(r, g, b, null);
                hsb[0] = targetHue;
                hsb[1] = Math.min(1f, hsb[1] * saturationScale + 0.2f);
                int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
                out.setRGB(x, y, (alpha << 24) | (rgb & 0x00FFFFFF));
            }
        }
        return out;
    }

    static void tintAndSave(String srcPath, Path outDir, String outName, float hue, float satScale) throws IOException {
        File f = new File(srcPath);
        if (!f.exists()) { System.err.println("Missing plant tile: " + srcPath); return; }
        BufferedImage img = load(srcPath);
        // Scale to 16x16 if needed
        if (img.getWidth() != T || img.getHeight() != T) {
            BufferedImage scaled = new BufferedImage(T, T, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(img, 0, 0, T, T, null);
            g2.dispose();
            img = scaled;
        }
        save(outDir.resolve(outName), tintHSB(img, hue, satScale));
    }

    static BufferedImage load(String path) throws IOException {
        BufferedImage img = ImageIO.read(new File(path));
        if (img == null) throw new IOException("Cannot read image: " + path);
        // Ensure ARGB
        if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage converted = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            converted.getGraphics().drawImage(img, 0, 0, null);
            return converted;
        }
        return img;
    }

    static void save(Path path, BufferedImage img) {
        try { ImageIO.write(img, "PNG", path.toFile()); System.out.println("  -> " + path.getFileName()); }
        catch (IOException e) { System.err.println("Save failed: " + path + " — " + e.getMessage()); }
    }
}
