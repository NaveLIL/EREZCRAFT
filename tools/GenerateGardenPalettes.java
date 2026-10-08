import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.HashSet;
import javax.imageio.ImageIO;

/** Only palette substitution: no drawing, resampling, rotation, mirroring or alpha edits. */
public final class GenerateGardenPalettes {
    static final Path SOURCE=Path.of("art/sources/cc0/pale-gardens"),OUTPUT=Path.of("src/main/resources/assets/interstice/textures/block/garden");
    static final String[][] ASSETS={
        {"paleheart_log","eucalyptus_log_side","48","43","57"},
        {"paleheart_log_top","eucalyptus_log_top","105","88","103"},
        {"stripped_paleheart_log","beech_log_side","83","67","85"},
        {"stripped_paleheart_log_top","eucalyptus_log_top","125","106","118"},
        {"paleheart_planks","beech_planks","77","65","81"},
        {"paleheart_leaves","beech_leaves","143","121","139"},
        {"paleheart_sapling","sapling_beech","122","99","119"},
        {"pale_fern","fern2","112","93","111"},
        {"pale_litter","grass_top","108","95","109"},
        {"palestone","limestone","107","97","117"},
        {"palestone_bricks","limestone_bricks","110","99","119"},
        {"pale_vine","stem2_leaf_both","111","87","108"},
        {"pale_vine_tip","tip2","120","96","114"},
        {"tide_heart","bud","184","139","94"},
        {"tide_heart_unripe","bud","98","82","105"},
        {"crown_sapling","sapling_beech","148","114","121"}
    };
    static String hash(Path path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    static int clamp(double value){return Math.max(0,Math.min(255,(int)Math.round(value)));}
    public static void main(String[] args)throws Exception{
        Files.createDirectories(OUTPUT);StringBuilder entries=new StringBuilder();
        BufferedImage sheet=new BufferedImage(704,384,BufferedImage.TYPE_INT_ARGB);var g=sheet.createGraphics();
        g.setColor(new Color(0x211b25));g.fillRect(0,0,704,384);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int index=0;
        for(String[] entry:ASSETS){
            Path input=SOURCE.resolve(entry[1]+".png");BufferedImage src=ImageIO.read(input.toFile());
            boolean vine=entry[0].startsWith("pale_vine"),fruit=entry[1].equals("bud");
            var sourceHash=java.util.regex.Pattern.compile("\"(?:plants/)?"+entry[1]+"(?:\\.png)?\"\\s*:\\s*\"([0-9a-f]{64})\"").matcher(Files.readString(SOURCE.resolve(fruit?"fruit-sources.json":vine?"vine-sources.json":"sources.json")));
            if(!sourceHash.find() || !hash(input).equals(sourceHash.group(1)))throw new IllegalStateException("Ready-made source pattern changed: "+input);
            if(src.getWidth()!=16||(src.getHeight()!=16&&src.getHeight()!=32))throw new IllegalStateException("Expected unmodified 16x16/16x32 input "+input);
            double mean=0;int count=0;var colors=new LinkedHashMap<Integer,Integer>();
            for(int y=0;y<src.getHeight();y++)for(int x=0;x<16;x++){int c=src.getRGB(x,y);colors.put(c,c);if((c>>>24)!=0){mean+=((c>>16)&255)*.2126+((c>>8)&255)*.7152+(c&255)*.0722;count++;}}
            mean/=Math.max(1,count);var used=new HashSet<Integer>();int r=Integer.parseInt(entry[2]),gr=Integer.parseInt(entry[3]),b=Integer.parseInt(entry[4]);
            for(var old:new java.util.ArrayList<>(colors.keySet())){
                int alpha=old>>>24;if(alpha==0){used.add(old);continue;}
                double light=.35+.65*(((old>>16)&255)*.2126+((old>>8)&255)*.7152+(old&255)*.0722)/mean;
                int rgb=(alpha<<24)|(clamp(r*light)<<16)|(clamp(gr*light)<<8)|clamp(b*light);
                while(used.contains(rgb))rgb=(rgb&0xffffff00)|(((rgb&255)+1)&255);
                colors.put(old,rgb);used.add(rgb);
            }
            BufferedImage out=new BufferedImage(16,src.getHeight(),BufferedImage.TYPE_INT_ARGB);
            var reverse=new LinkedHashMap<Integer,Integer>();
            for(int y=0;y<src.getHeight();y++)for(int x=0;x<16;x++){
                int original=src.getRGB(x,y),replacement=colors.get(original);out.setRGB(x,y,replacement);
                if((replacement>>>24)!=(original>>>24))throw new IllegalStateException("Alpha changed");
                Integer previous=reverse.putIfAbsent(replacement,original);if(previous!=null&&previous!=original)throw new IllegalStateException("Palette collapsed original pattern classes");
            }
            Path output=OUTPUT.resolve(entry[0]+".png");ImageIO.write(out,"PNG",output.toFile());
            if(index>0)entries.append(",\n");
            entries.append("    {\"output\":\"block/garden/").append(entry[0]).append(".png\",\"source\":\"").append(entry[1]).append(".png\",\"source_url\":\"https://opengameart.org/content/").append(vine||fruit?"plant-tileset":"16x16-block-texture-set").append("\",\"source_sha256\":\"").append(hash(input)).append("\",\"output_sha256\":\"").append(hash(output)).append("\",\"palette_colors\":").append(colors.size()).append("}");
            int xx=(index%6)*112,yy=(index/6)*128;g.drawImage(src,xx+8,yy+12,48,48,null);g.drawImage(out,xx+8,yy+68,48,48,null);index++;
        }
        g.dispose();Path preview=Path.of("art/generated/pale-gardens/palette-only.png");Files.createDirectories(preview.getParent());ImageIO.write(sheet,"PNG",preview.toFile());
        Path registry=Path.of("src/main/resources/assets/interstice/provenance/pale-gardens.json");
        Files.writeString(registry,"{\n  \"license\":\"CC0-1.0\",\n  \"author\":\"ARoachIFoundOnMyPillow\",\n  \"source_url\":\"https://opengameart.org/content/16x16-block-texture-set\",\n  \"generator\":\"tools/GenerateGardenPalettes.java\",\n  \"operation\":\"One-to-one palette substitution only; pixel positions, original pattern, size and alpha preserved\",\n  \"pattern_and_alpha_verified\":true,\n  \"outputs\":[\n"+entries+"\n  ]\n}\n");
        Path icon=Path.of("src/main/resources/assets/interstice/textures/mob_effect/tide_heart_reaction.png");Files.createDirectories(icon.getParent());Files.copy(OUTPUT.resolve("tide_heart.png"),icon,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.out.println("GARDEN_PALETTES passed "+ASSETS.length+" ready-made textures; pattern classes and alpha unchanged.");
    }
}
