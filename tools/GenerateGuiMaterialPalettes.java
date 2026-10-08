import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

/** Ready16px metal/diagonal cloth-like patterns; exact layout and alpha, bijective palette only. */
public final class GenerateGuiMaterialPalettes {
    record Entry(String input,String hash,String output,int[] colors){}
    public static void main(String[] args)throws Exception{
        var entries=List.of(
                new Entry("steel_block.png","a6eada1cd49f59309f3edb55e725eb896d4c8e4a6f97c00c68e84ac7d945288c","retort_metal",new int[]{0x3e464b,0x4a5358,0x566065,0x626b70,0x6e777c,0x7a8388,0x868f94}),
                new Entry("linen_pattern.png","0be27d43f3a41aa9fed0e909affe1167ca7c746c270b757b23d6ec90826e161f","backpack_cloth",new int[]{0x4a4c40,0x626153}));
        var records=new ArrayList<String>();
        for(var e:entries){
            Path source=Path.of("art/sources/cc0/gui-materials",e.input);if(!sha(source).equals(e.hash))throw new IllegalStateException("Ready source hash changed: "+source);
            var original=ImageIO.read(source.toFile());if(original.getWidth()!=16||original.getHeight()!=16)throw new IllegalStateException("Whole ready16px surface required");
            var classes=new TreeSet<Integer>(Comparator.comparingDouble((Integer c)->brightness(c)).thenComparingInt(c->c));
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)classes.add(original.getRGB(x,y));
            if(classes.size()!=e.colors.length)throw new IllegalStateException("Declared palette no longer bijective: "+source);
            var palette=new HashMap<Integer,Integer>();var used=new HashSet<Integer>();int index=0;
            for(int color:classes){int target=(color&0xff000000)|e.colors[index++];if(!used.add(target))throw new IllegalStateException("Duplicate output color");palette.put(color,target);}
            var output=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)output.setRGB(x,y,palette.get(original.getRGB(x,y)));
            Path target=Path.of("src/main/resources/assets/interstice/textures/gui",e.output+".png");Files.createDirectories(target.getParent());ImageIO.write(output,"PNG",target.toFile());
            var stored=ImageIO.read(target.toFile());var forward=new HashMap<Integer,Integer>();var reverse=new HashMap<Integer,Integer>();
            for(int y=0;y<16;y++)for(int x=0;x<16;x++){
                int a=original.getRGB(x,y),b=stored.getRGB(x,y);
                if((a>>>24)!=(b>>>24)||(forward.containsKey(a)&&forward.get(a)!=b)||(reverse.containsKey(b)&&reverse.get(b)!=a))throw new IllegalStateException("Pattern or alpha changed: "+target);
                forward.put(a,b);reverse.put(b,a);
            }
            records.add("{\"source\":\""+e.input+"\",\"output\":\"gui/"+e.output+".png\",\"source_sha256\":\""+sha(source)+"\",\"output_sha256\":\""+sha(target)+"\",\"width\":16,\"height\":16,\"classes\":"+classes.size()+"}");
        }
        Path record=Path.of("src/main/resources/assets/interstice/provenance/gui-materials.json");Files.createDirectories(record.getParent());
        Files.writeString(record,"{\"license\":\"CC0-1.0\",\"sources\":\"art/sources/cc0/gui-materials/sources.json\",\"generator\":\"tools/GenerateGuiMaterialPalettes.java\",\"pattern_and_alpha_verified\":true,\"cloth_note\":\"Whole ready geometric diagonal pattern used as a stylized cloth-like material, not a fabric photograph\",\"operation\":\"Bijective palette substitution; whole source coordinates, resolution and alpha preserved\",\"outputs\":["+String.join(",",records)+"]}\n");
        System.out.println("Verified2 whole GUI material palettes; retort_metal and backpack_cloth16x16 ready");
    }
    static double brightness(int c){return ((c>>>16)&255)*.2126+((c>>>8)&255)*.7152+(c&255)*.0722;}
    static String sha(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
}
