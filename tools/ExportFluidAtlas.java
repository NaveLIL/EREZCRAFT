import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Format conversion only: export an ImageGen 4x4 frame atlas to Minecraft's vertical-strip layout. */
public class ExportFluidAtlas {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("input-atlas output-strip frame-size");
        BufferedImage atlas = ImageIO.read(Path.of(args[0]).toFile());
        if (atlas == null) throw new IllegalArgumentException("Not a readable raster image");
        int size = Integer.parseInt(args[2]);
        BufferedImage strip = new BufferedImage(size, size * 16, BufferedImage.TYPE_INT_ARGB);
        for (int frame=0; frame<16; frame++) {
            int col=frame%4, row=frame/4;
            for (int y=0; y<size; y++) for (int x=0; x<size; x++) {
                // Nearest-neighbor sampling; no filters or new painted detail.
                int sx = Math.min(atlas.getWidth()-1, (int)((col+(x+0.5)/size)*atlas.getWidth()/4.0));
                int sy = Math.min(atlas.getHeight()-1, (int)((row+(y+0.5)/size)*atlas.getHeight()/4.0));
                strip.setRGB(x, frame*size+y, atlas.getRGB(sx,sy));
            }
        }
        Path output=Path.of(args[1]);Files.createDirectories(output.getParent());
        ImageIO.write(strip,"PNG",output.toFile());
        Files.writeString(Path.of(args[1]+".mcmeta"),"{\n  \"animation\": {\n    \"frametime\": 6,\n    \"interpolate\": true,\n    \"width\": "+size+",\n    \"height\": "+size+"\n  }\n}\n");
        System.out.println(output+": "+size+"x"+(16*size)+", 16 frames");
    }
}
