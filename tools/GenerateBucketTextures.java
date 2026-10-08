package tools;

import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import javax.imageio.ImageIO;

/** Reproducible 16px bucket icons using vanilla's mouth and the established metal silhouette. */
public final class GenerateBucketTextures {
    public static void main(String[] args) throws Exception {
        Path resources = args.length > 0 ? Path.of(args[0])
                : Path.of("build/moddev/artifacts/neoforge-21.1.252-client-extra-aka-minecraft-resources.jar");
        Path output = Path.of("src/main/resources/assets/interstice/textures/item");
        try (JarFile jar = new JarFile(resources.toFile())) {
            BufferedImage iron = read(jar, "bucket");
            BufferedImage water = read(jar, "water_bucket");
            BufferedImage lava = read(jar, "lava_bucket");
            BufferedImage silver = ImageIO.read(output.resolve("riftsilver_bucket.png").toFile());
            String[] names = {"riftsilver_bucket", "riftsilver_water_bucket", "riftsilver_lava_bucket",
                    "riftsilver_heavy_bucket", "riftsilver_inverted_bucket", "heavy_toxin_bucket", "light_toxin_bucket"};
            BufferedImage[] icons = {silver,
                    filled(iron, silver, water, false, false), filled(iron, silver, lava, false, false),
                    filled(iron, silver, water, true, false), filled(iron, silver, water, true, true),
                    filled(iron, iron, water, true, false), filled(iron, iron, water, true, true)};
            for (int i = 1; i < names.length; i++) ImageIO.write(icons[i], "PNG", output.resolve(names[i] + ".png").toFile());

            Path preview = Path.of(".verification/bucket-art/buckets-preview.png");
            Files.createDirectories(preview.getParent());
            String[] labels = {"Empty", "Water", "Lava", "Heavy toxin", "Light toxin", "Iron / heavy", "Iron / light"};
            BufferedImage sheet = new BufferedImage(icons.length * 112, 136, BufferedImage.TYPE_INT_RGB);
            var graphics = sheet.createGraphics();
            graphics.setColor(new Color(0xC6C6C6));
            graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            graphics.setColor(Color.BLACK);
            for (int i = 0; i < icons.length; i++) {
                graphics.drawImage(icons[i], i * 112 + 8, 4, 96, 96, null);
                graphics.drawString(labels[i], i * 112 + 8, 118);
            }
            graphics.dispose();
            ImageIO.write(sheet, "PNG", preview.toFile());
            System.out.println("Generated 6 filled bucket icons; preview: " + preview);
        }
    }

    private static BufferedImage read(JarFile jar, String name) throws Exception {
        try (var stream = jar.getInputStream(jar.getJarEntry("assets/minecraft/textures/item/" + name + ".png"))) {
            return ImageIO.read(stream);
        }
    }

    private static BufferedImage filled(BufferedImage emptyIron, BufferedImage metal, BufferedImage vanillaFilled,
                                        boolean toxin, boolean light) {
        BufferedImage result = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int base = metal.getRGB(x, y);
            int liquid = vanillaFilled.getRGB(x, y);
            // Fill the same mouth as vanilla; leave the metal body and handle intact.
            if (y <= 7 && (liquid >>> 24) != 0 && liquid != emptyIron.getRGB(x, y)) {
                if (toxin) {
                    float[] hsv = Color.RGBtoHSB((liquid >>> 16) & 255, (liquid >>> 8) & 255, liquid & 255, null);
                    float brightness = hsv[2];
                    liquid = light ? Color.HSBtoRGB(0.145F, 0.35F, 0.62F + 0.38F * brightness)
                            : Color.HSBtoRGB(0.965F, 0.83F, 0.20F + 0.65F * brightness);
                }
                base = liquid;
            }
            result.setRGB(x, y, base);
        }
        return result;
    }
}
