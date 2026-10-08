import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Ready CC0 patterns only. Bijective palette substitution preserves every pixel and alpha. */
public final class GenerateCrownPalettes {
    static final Path SOURCE=Path.of("art/sources/cc0/crown-tree"),OUT=GenerateGardenPalettes.OUTPUT;
    static final String[][] ASSETS={
        {"crown_log","maple_log_side","78","100","117"},
        {"crown_log_top","maple_log_top","124","139","147"},
        {"stripped_crown_log","maple_log_side","110","127","139"},
        {"stripped_crown_log_top","maple_log_top","142","153","159"},
        {"crown_planks","maple_planks","107","123","135"},
        {"crown_leaves","oak_leaves_2","132","166","156"},
        {"crown_leaves_2","oak_leaves_3","145","178","167"},
        {"crown_sapling","sapling_maple","128","163","157"}
    };
    public static void main(String[] args)throws Exception {
        Files.createDirectories(OUT);String manifest=Files.readString(SOURCE.resolve("sources.json"));
        StringBuilder entries=new StringBuilder();
        var sheet=new BufferedImage(640,240,BufferedImage.TYPE_INT_ARGB);var g=sheet.createGraphics();
        g.setColor(new Color(0x211b25));g.fillRect(0,0,640,240);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int index=0;
        for(var a:ASSETS){
            Path input=SOURCE.resolve(a[1]+".png");String sourceHash=GenerateGardenPalettes.hash(input);
            var match=java.util.regex.Pattern.compile("\""+a[1]+"\\.png\"\\s*:\\s*\\{\\s*\"sha256\"\\s*:\\s*\"([0-9a-f]{64})\"").matcher(manifest);
            if(!match.find()||!match.group(1).equals(sourceHash))throw new IllegalStateException("Source hash changed: "+input);
            var src=ImageIO.read(input.toFile());if(src.getWidth()!=16||src.getHeight()!=16)throw new IllegalStateException("Expected ready 16x16 image");
            var colors=new LinkedHashMap<Integer,Integer>();double mean=0;int count=0;
            for(int y=0;y<16;y++)for(int x=0;x<16;x++){int c=src.getRGB(x,y);colors.put(c,c);if((c>>>24)!=0){mean+=luma(c);count++;}}
            mean/=Math.max(1,count);var used=new HashSet<Integer>();
            for(int old:new ArrayList<>(colors.keySet())){
                int alpha=old>>>24;if(alpha==0){used.add(old);continue;}
                double light=.25+.75*luma(old)/mean;
                int rgb=(alpha<<24)|(GenerateGardenPalettes.clamp(Integer.parseInt(a[2])*light)<<16)|(GenerateGardenPalettes.clamp(Integer.parseInt(a[3])*light)<<8)|GenerateGardenPalettes.clamp(Integer.parseInt(a[4])*light);
                while(used.contains(rgb))rgb=(rgb&0xffffff00)|(((rgb&255)+1)&255);
                colors.put(old,rgb);used.add(rgb);
            }
            var out=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);var reverse=new HashMap<Integer,Integer>();
            for(int y=0;y<16;y++)for(int x=0;x<16;x++){
                int old=src.getRGB(x,y),rgb=colors.get(old);out.setRGB(x,y,rgb);
                Integer previous=reverse.putIfAbsent(rgb,old);
                if((old>>>24)!=(rgb>>>24)||(previous!=null&&previous!=old))throw new IllegalStateException("Pattern/alpha changed");
            }
            Path output=OUT.resolve(a[0]+".png");ImageIO.write(out,"PNG",output.toFile());
            if(index>0)entries.append(",\n");
            entries.append("    {\"output\":\"block/garden/").append(a[0]).append(".png\",\"source\":\"").append(a[1]).append(".png\",\"source_url\":\"https://opengameart.org/content/").append(a[1].startsWith("oak_leaves")?"more-blocks":"16x16-block-texture-set").append("\",\"source_sha256\":\"").append(sourceHash).append("\",\"output_sha256\":\"").append(GenerateGardenPalettes.hash(output)).append("\"}");
            int xx=index%8*80;g.drawImage(src,xx+8,8,64,64,null);g.drawImage(out,xx+8,88,64,64,null);index++;
        }
        g.dispose();Path preview=Path.of("art/generated/crown-tree/palette-only.png");Files.createDirectories(preview.getParent());ImageIO.write(sheet,"PNG",preview.toFile());
        Files.writeString(Path.of("src/main/resources/assets/interstice/provenance/crown-tree.json"),"{\n  \"license\":\"CC0-1.0\",\n  \"author\":\"ARoachIFoundOnMyPillow\",\n  \"generator\":\"tools/GenerateCrownPalettes.java\",\n  \"operation\":\"One-to-one palette substitution only; original coordinates, size, pattern classes and alpha preserved\",\n  \"pattern_and_alpha_verified\":true,\n  \"outputs\":[\n"+entries+"\n  ]\n}\n");
        System.out.println("CROWN_PALETTES passed "+ASSETS.length+" ready-made patterns and alpha unchanged");
    }
    static double luma(int c){return ((c>>16)&255)*.2126+((c>>8)&255)*.7152+(c&255)*.0722;}
}
