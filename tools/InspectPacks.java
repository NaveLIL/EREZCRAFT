package tools;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class InspectPacks {
    public static void main(String[] args) throws Exception {
        File grassFile = new File("/tmp/stone_ore_gems/Stone_ore_gems/Stone_ore_gems_grass.png");
        if (grassFile.exists()) {
            BufferedImage grass = ImageIO.read(grassFile);
            int cols = grass.getWidth() / 16;
            int rows = grass.getHeight() / 16;
            System.out.println("Stone_ore_gems_grass: " + cols + "x" + rows);
            for (int r = 0; r < rows; r++) {
                System.out.printf("Row %d: ", r);
                for (int c = 0; c < Math.min(cols, 5); c++) {
                    BufferedImage tile = grass.getSubimage(c * 16, r * 16, 16, 16);
                    int rSum = 0, gSum = 0, bSum = 0, count = 0;
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            int argb = tile.getRGB(x, y);
                            if (((argb >> 24) & 0xFF) > 128) {
                                rSum += (argb >> 16) & 0xFF;
                                gSum += (argb >> 8) & 0xFF;
                                bSum += argb & 0xFF;
                                count++;
                            }
                        }
                    }
                    if (count > 0) {
                        System.out.printf("[%d,%d,%d] ", rSum / count, gSum / count, bSum / count);
                    } else {
                        System.out.print("[empty] ");
                    }
                }
                System.out.println();
            }
        }
    }
}
