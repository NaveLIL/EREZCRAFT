import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class GenerateSpiderTextures {
    public static void main(String[] args) throws Exception {
        File srcFile = new File("art/sources/cc0/sea_spider/sea_spider.png");
        if (!srcFile.exists()) {
            throw new IllegalStateException("Source not found: " + srcFile);
        }
        BufferedImage src = ImageIO.read(srcFile);
        int w = src.getWidth();
        int h = src.getHeight();

        File outDir = new File("src/main/resources/assets/interstice/textures/entity/spider");
        outDir.mkdirs();

        // 1. Skirmisher (charcoal chitin & amber/rust joints)
        BufferedImage skirmisher = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xff;
                if (a == 0) continue;
                int r = (argb >> 16) & 0xff;
                int g = (argb >> 8) & 0xff;
                int b = argb & 0xff;
                float lum = (r * 0.299F + g * 0.587F + b * 0.114F) / 255.0F;

                int nr, ng, nb;
                if (lum > 0.62F) {
                    nr = Math.min(255, (int) (210 * lum));
                    ng = Math.min(255, (int) (105 * lum));
                    nb = Math.min(255, (int) (45 * lum));
                } else {
                    nr = Math.min(255, (int) (55 * lum * 1.4F + 15));
                    ng = Math.min(255, (int) (50 * lum * 1.4F + 15));
                    nb = Math.min(255, (int) (62 * lum * 1.4F + 18));
                }
                skirmisher.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        ImageIO.write(skirmisher, "PNG", new File(outDir, "cave_rift_spider_skirmisher.png"));
        System.out.println("Saved cave_rift_spider_skirmisher.png");

        // 2. Lurker (vitriolite slate camouflage: grey stone body, dull mossy/vitriol plates)
        BufferedImage lurker = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xff;
                if (a == 0) continue;
                int r = (argb >> 16) & 0xff;
                int g = (argb >> 8) & 0xff;
                int b = argb & 0xff;
                float lum = (r * 0.299F + g * 0.587F + b * 0.114F) / 255.0F;

                int nr = Math.min(255, (int) (65 * lum + 32));
                int ng = Math.min(255, (int) (75 * lum + 38));
                int nb = Math.min(255, (int) (80 * lum + 42));
                if (lum > 0.68F) {
                    nr = Math.min(255, (int) (nr * 1.35F));
                    ng = Math.min(255, (int) (ng * 1.45F));
                    nb = Math.min(255, (int) (nb * 1.55F));
                }
                lurker.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        ImageIO.write(lurker, "PNG", new File(outDir, "cave_rift_spider_lurker.png"));
        System.out.println("Saved cave_rift_spider_lurker.png");

        // 3. Spitter (rift violet chitin with glowing cyan toxin glands)
        BufferedImage spitter = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xff;
                if (a == 0) continue;
                int r = (argb >> 16) & 0xff;
                int g = (argb >> 8) & 0xff;
                int b = argb & 0xff;
                float lum = (r * 0.299F + g * 0.587F + b * 0.114F) / 255.0F;

                int nr, ng, nb;
                if (lum > 0.52F) {
                    // Glowing cyan phosphor
                    nr = Math.min(255, (int) (35 * lum));
                    ng = Math.min(255, (int) (225 * lum));
                    nb = Math.min(255, (int) (205 * lum));
                } else {
                    // Deep rift violet stone
                    nr = Math.min(255, (int) (48 * lum + 20));
                    ng = Math.min(255, (int) (32 * lum + 15));
                    nb = Math.min(255, (int) (68 * lum + 30));
                }
                spitter.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        ImageIO.write(spitter, "PNG", new File(outDir, "cave_rift_spider_spitter.png"));
        System.out.println("Saved cave_rift_spider_spitter.png");

        // 4. Habitat: Spider Egg Sac (CC0 schist re-paletted into solid silken pulsating egg cocoon)
        File blockDir = new File("src/main/resources/assets/interstice/textures/block");
        blockDir.mkdirs();

        File schistFile = new File("art/sources/cc0/block-texture-set/schist.png");
        if (schistFile.exists()) {
            BufferedImage schist = ImageIO.read(schistFile);
            int sw = schist.getWidth();
            int sh = schist.getHeight();
            BufferedImage eggSac = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < sh; y++) {
                for (int x = 0; x < sw; x++) {
                    int argb = schist.getRGB(x, y);
                    int r = (argb >> 16) & 0xff;
                    int g = (argb >> 8) & 0xff;
                    int b = argb & 0xff;
                    float lum = (r * 0.299F + g * 0.587F + b * 0.114F) / 255.0F;

                    int nr, ng, nb;
                    if (lum > 0.42F) {
                        // Silken pearlescent highlight
                        nr = Math.min(255, (int) (225 + 30 * lum));
                        ng = Math.min(255, (int) (220 + 35 * lum));
                        nb = Math.min(255, (int) (235 + 20 * lum));
                    } else if (lum > 0.32F) {
                        // Translucent lavender-white silk weave
                        nr = Math.min(255, (int) (185 * lum + 115));
                        ng = Math.min(255, (int) (175 * lum + 110));
                        nb = Math.min(255, (int) (205 * lum + 125));
                    } else if (lum > 0.22F) {
                        // Mid-tone cocoon flesh
                        nr = Math.min(255, (int) (140 * lum + 80));
                        ng = Math.min(255, (int) (125 * lum + 75));
                        nb = Math.min(255, (int) (165 * lum + 90));
                    } else {
                        // Shadow crevice in silken folds
                        nr = Math.min(255, (int) (80 * lum + 45));
                        ng = Math.min(255, (int) (65 * lum + 35));
                        nb = Math.min(255, (int) (105 * lum + 60));
                    }

                    // Embryo glow veins
                    if ((x == 4 && y == 5) || (x == 5 && y == 5) ||
                        (x == 11 && y == 10) || (x == 10 && y == 11) ||
                        (x == 7 && y == 12) || (x == 8 && y == 12)) {
                        nr = 45; ng = 235; nb = 205;
                    }
                    eggSac.setRGB(x, y, (255 << 24) | (nr << 16) | (ng << 8) | nb);
                }
            }
            ImageIO.write(eggSac, "PNG", new File(blockDir, "spider_egg_sac.png"));
            System.out.println("Saved spider_egg_sac.png");
        }

        // 5. Habitat: Rift Cobweb (eerie violet-cyan silk threads)
        BufferedImage web = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        // Diagonal spokes
        for (int i = 0; i < 16; i++) {
            web.setRGB(i, i, 0xccb8a4cc);
            web.setRGB(15 - i, i, 0xccb8a4cc);
            if (i % 2 == 0) {
                web.setRGB(7, i, 0xdda490ba);
                web.setRGB(8, i, 0xdda490ba);
                web.setRGB(i, 7, 0xdda490ba);
                web.setRGB(i, 8, 0xdda490ba);
            }
        }
        // Concentric connecting strands
        int[][] ring1 = {{3,5},{4,4},{5,3},{10,3},{11,4},{12,5},{12,10},{11,11},{10,12},{5,12},{4,11},{3,10}};
        for (int[] p : ring1) web.setRGB(p[0], p[1], 0xee9f8cb5);
        int[][] ring2 = {{1,6},{2,3},{3,2},{6,1},{9,1},{12,2},{13,3},{14,6},{14,9},{13,12},{12,13},{9,14},{6,14},{3,13},{2,12},{1,9}};
        for (int[] p : ring2) web.setRGB(p[0], p[1], 0xcc8976a0);
        // Dew / toxin droplets
        web.setRGB(5, 5, 0xff2cf5c0);
        web.setRGB(10, 10, 0xff2cf5c0);
        web.setRGB(11, 4, 0xff3bf4c8);
        web.setRGB(4, 11, 0xff3bf4c8);

        ImageIO.write(web, "PNG", new File(blockDir, "rift_cobweb.png"));
        System.out.println("Saved rift_cobweb.png");
    }
}
