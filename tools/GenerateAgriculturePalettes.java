import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

/** Whole ready sprites; exact alpha and bijective pixel-class palette substitution, never repainting. */
public final class GenerateAgriculturePalettes {
    record Entry(String input,String output,int rgb){}
    public static void main(String[] args)throws Exception {
        var entries=new ArrayList<Entry>();
        for(int i=1;i<=5;i++)entries.add(new Entry("wheat_"+i+".png","grain_stage_"+(i-1),0xaaa19a));
        for(int i=1;i<=4;i++)entries.add(new Entry("carrots_"+i+".png","root_stage_"+(i-1),0x936783));
        entries.addAll(List.of(new Entry("farmland.png","farmland_dry",0x847075),new Entry("farmland.png","farmland_wet",0x56424f),
            new Entry("grain.png","ash_grain",0xb5a68a),new Entry("root.png","crimson_root",0xb67488),new Entry("root.png","purified_root",0xca9892),new Entry("bread.png","purified_bread",0xbca58b),
            new Entry("stick.png","riftsilver_wire",0x9b9ea7),new Entry("stick.png","riftsilver_mesh",0xc2bccc),new Entry("coal.png","umbral_sorbent",0x5c5a64),new Entry("crystal.png","phosphorite_paste",0x86b7aa),
            new Entry("shard.png","vitriolite_lining",0x8a849c),new Entry("shard.png","pure_vitriolite_lining",0xb8b1d1),new Entry("coal.png","ash_flour",0xb7aca6),new Entry("stick.png","plant_fiber",0xbca7a8),new Entry("coal.png","mineral_fertilizer",0x947a96)));
        var records=new ArrayList<String>();
        for(var e:entries){Path source=Path.of("art/sources/cc0/agriculture",e.input);var original=ImageIO.read(source.toFile());var colors=new TreeSet<Integer>(Comparator.comparingDouble((Integer c)->brightness(c)).thenComparingInt(c->c));
            for(int y=0;y<original.getHeight();y++)for(int x=0;x<original.getWidth();x++)colors.add(original.getRGB(x,y));
            var palette=new HashMap<Integer,Integer>();var used=new HashSet<Integer>();int index=0;
            for(int c:colors){int alpha=c>>>24;double scale=.35+.85*(index++/(double)Math.max(1,colors.size()-1));int r=(int)Math.min(255,((e.rgb>>16)&255)*scale),g=(int)Math.min(255,((e.rgb>>8)&255)*scale),b=(int)Math.min(255,(e.rgb&255)*scale);
                int result=(alpha<<24)|(r<<16)|(g<<8)|b;while(!used.add(result))result=(alpha<<24)|((result+1)&0xffffff);palette.put(c,result);}
            var output=new BufferedImage(original.getWidth(),original.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<original.getHeight();y++)for(int x=0;x<original.getWidth();x++)output.setRGB(x,y,palette.get(original.getRGB(x,y)));
            Path target=Path.of("src/main/resources/assets/interstice/textures/block/agriculture",e.output+".png");Files.createDirectories(target.getParent());ImageIO.write(output,"PNG",target.toFile());
            var stored=ImageIO.read(target.toFile());var forward=new HashMap<Integer,Integer>();var reverse=new HashMap<Integer,Integer>();
            for(int y=0;y<original.getHeight();y++)for(int x=0;x<original.getWidth();x++){int a=original.getRGB(x,y),b=stored.getRGB(x,y);if((a>>>24)!=(b>>>24)||forward.containsKey(a)&&forward.get(a)!=b||reverse.containsKey(b)&&reverse.get(b)!=a)throw new IllegalStateException("Pattern/alpha changed "+target);forward.put(a,b);reverse.put(b,a);}
            records.add("{\"source\":\""+e.input+"\",\"output\":\"block/agriculture/"+e.output+".png\",\"source_sha256\":\""+sha(source)+"\",\"output_sha256\":\""+sha(target)+"\",\"classes\":"+colors.size()+"}");
        }
        Path record=Path.of("src/main/resources/assets/interstice/provenance/agriculture.json");Files.writeString(record,"{\"license\":\"CC0-1.0\",\"sources\":\"art/sources/cc0/agriculture/sources.json\",\"generator\":\"tools/GenerateAgriculturePalettes.java\",\"pattern_and_alpha_verified\":true,\"operation\":\"Bijective palette substitution; whole source coordinates, resolution and alpha preserved\",\"outputs\":["+String.join(",",records)+"]}\n");
        System.out.println("Verified"+entries.size()+" whole agriculture palettes");
    }
    static double brightness(int c){return ((c>>16)&255)*.2126+((c>>8)&255)*.7152+(c&255)*.0722;}
    static String sha(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
}
