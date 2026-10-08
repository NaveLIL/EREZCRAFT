import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Mechanical Minecraft resolution preparation of original built-in imagegen artwork. No repainting. */
public final class PrepareClingweedTexture {
    public static void main(String[] args)throws Exception {
        Path source=Path.of("art/sources/original/clingweed-sprite-master.png");
        var input=ImageIO.read(source.toFile());
        var texture=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);var g=texture.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(input,0,0,32,32,null);g.dispose();
        Path out=Path.of("src/main/resources/assets/interstice/textures/block/cave/clingweed.png");Files.createDirectories(out.getParent());ImageIO.write(texture,"PNG",out.toFile());
        long transparent=0;for(int y=0;y<32;y++)for(int x=0;x<32;x++)if((texture.getRGB(x,y)>>>24)==0)transparent++;
        if(transparent<400)throw new IllegalStateException("Clingweed sprite needs transparent spaces between stems");
        var preview=new BufferedImage(384,384,BufferedImage.TYPE_INT_ARGB);g=preview.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);g.drawImage(texture,0,0,384,384,null);g.dispose();
        Path sheet=Path.of("art/generated/living-realm/clingweed-preview.png");Files.createDirectories(sheet.getParent());ImageIO.write(preview,"PNG",sheet.toFile());
        System.out.println("Original generated clingweed prepared at32x32: "+out);
    }
}
