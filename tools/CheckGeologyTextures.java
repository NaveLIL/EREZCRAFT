import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** Standalone JDK21 audit of tile seams, variant edges and the accepted pre-M13 boundaries. */
public final class CheckGeologyTextures {
    static final Path ROOT=Path.of("src/main/resources/assets/interstice/textures");
    static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    static BufferedImage read(String name)throws Exception{
        var im=ImageIO.read(ROOT.resolve(name).toFile());require(im!=null&&im.getWidth()==16&&im.getHeight()==16,"Expected 16x16: "+name);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)require((im.getRGB(x,y)>>>24)==255,"Unexpected transparency: "+name);return im;
    }
    static int[] edge(BufferedImage im){int[] result=new int[64];for(int i=0;i<16;i++){
        result[i]=im.getRGB(0,i);result[16+i]=im.getRGB(15,i);result[32+i]=im.getRGB(i,0);result[48+i]=im.getRGB(i,15);
    }return result;}
    public static void main(String[] args)throws Exception{
        boolean remapped=args.length>1&&args[1].equals("--v6-palette");
        int checked=0;
        for(String name:new String[]{"riftstone","riftsilver_ore","rift_frame","abyssal_turf_side"}){
            var current=read("block/"+name+".png");
            var process=new ProcessBuilder("git","show","ec32d8eec2cfa903979d03ce94a2e84f7b437941:src/main/resources/assets/interstice/textures/block/"+name+".png").start();
            byte[] png=process.getInputStream().readAllBytes();require(process.waitFor()==0,"Missing accepted artwork in Git history");
            var original=ImageIO.read(new ByteArrayInputStream(png));
            if(remapped){
                var baseline=ImageIO.read(Path.of("art/sources/accepted-v5-palettes/block/"+name+".png").toFile());
                require(Arrays.equals(edge(baseline),edge(original)),"Accepted baseline boundaries differ from original art: "+name);
                var forward=new java.util.HashMap<Integer,Integer>();var reverse=new java.util.HashMap<Integer,Integer>();
                for(int y=0;y<16;y++)for(int x=0;x<16;x++){
                    int a=baseline.getRGB(x,y),b=current.getRGB(x,y);require((a>>>24)==(b>>>24),"Palette alpha changed: "+name);
                    require(forward.getOrDefault(a,b)==b&&reverse.getOrDefault(b,a)==a,"Palette changed pixel classes: "+name);forward.put(a,b);reverse.put(b,a);
                }
                int[] accepted=edge(baseline),now=edge(current);for(int i=0;i<accepted.length;i++)require(forward.get(accepted[i])==now[i],"Remapped boundary mismatch: "+name);
            }else require(Arrays.equals(edge(current),edge(original)),"Accepted boundaries changed: "+name);
            checked++;
        }
        for(String name:new String[]{"vaultstone","weathered_vaultstone","polished_vaultstone","vaultstone_bricks"}){
            var base=read("block/stone/"+name+".png");
            for(int i=0;i<16;i++)require(base.getRGB(0,i)==base.getRGB(15,i)&&base.getRGB(i,0)==base.getRGB(i,15),"Tile edges disagree: "+name);
            if(name.equals("vaultstone")||name.equals("weathered_vaultstone"))for(int n=1;n<=2;n++){
                var variant=read("block/stone/"+name+"_"+n+".png");require(Arrays.equals(edge(base),edge(variant)),"Variant edges differ: "+name);
                boolean different=false;for(int y=1;y<15;y++)for(int x=1;x<15;x++)different|=base.getRGB(x,y)!=variant.getRGB(x,y);
                require(different,"Variant merely duplicates the base: "+name);checked++;
            }checked++;
        }
        Path result=Path.of(args.length==0?".verification/geology-textures-validation.json":args[0]);Files.createDirectories(result.toAbsolutePath().getParent());
        Files.writeString(result,"{\"passed\":true,\"checked_tiles\":"+checked+",\"original_edges_preserved\":true,\"palette_remapping_verified\":"+remapped+",\"natural_variants_share_edges\":true}\n");
        System.out.println("GEOLOGY_TEXTURES passed tiles="+checked);
    }
}
