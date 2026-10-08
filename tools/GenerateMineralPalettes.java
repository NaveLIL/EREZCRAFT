import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Whole ready sprites, with a reversible palette substitution and unchanged positions/alpha. */
public final class GenerateMineralPalettes {
    private static final Path SOURCE=Path.of("art/sources/cc0/minerals");
    private static final Path OUTPUT=Path.of("src/main/resources/assets/interstice/textures/block/minerals");
    private record Asset(String output,String source,int red,int green,int blue,boolean copy) {}
    private static final Asset[] ASSETS={
        new Asset("umbral_coal","coal_lump.png",27,25,33,false),
        new Asset("riftsilver","tin_nugget.png",198,195,211,false),
        new Asset("phosphorite","lexxite_shard.png",122,199,174,false),
        new Asset("vitriolite","ausene_shard.png",195,146,58,false),
        new Asset("world_stick","stick.png",84,63,82,false),
        new Asset("luminous_bud","flower2.png",83,196,207,false),
        new Asset("coal_torch","torch.png",0,0,0,true),
        new Asset("root_loam","dirt.png",93,74,85,false),
        new Asset("rift_shale","slate.png",68,62,79,false)
    };
    private static String hash(Path path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    private static int channel(double value){return Math.max(0,Math.min(255,(int)Math.round(value)));}
    private static String field(String json,String name){var matcher=Pattern.compile("\""+name+"\"\\s*:\\s*\"([^\"]*)\"").matcher(json);if(!matcher.find())throw new IllegalStateException("Missing source "+name);return matcher.group(1);}
    public static void main(String[] args)throws Exception{
        Files.createDirectories(OUTPUT);String sourceRegistry=Files.readString(SOURCE.resolve("sources.json"));StringBuilder outputs=new StringBuilder();
        BufferedImage sheet=new BufferedImage(ASSETS.length*144,320,BufferedImage.TYPE_INT_ARGB);var g=sheet.createGraphics();
        g.setColor(new Color(0x211b25));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for(int index=0;index<ASSETS.length;index++){
            var asset=ASSETS[index];Path input=SOURCE.resolve(asset.source),output=OUTPUT.resolve(asset.output+".png");
            var entry=Pattern.compile("\\{[^{}]*\"file\"\\s*:\\s*\""+Pattern.quote(asset.source)+"\"[^{}]*\\}",Pattern.DOTALL).matcher(sourceRegistry);
            if(!entry.find())throw new IllegalStateException("Unregistered ready source "+input);String record=entry.group();
            if(!hash(input).equals(field(record,"source_sha256")))throw new IllegalStateException("Source pattern changed "+input);
            BufferedImage source=ImageIO.read(input.toFile());if(source.getWidth()!=16||source.getHeight()!=16)throw new IllegalStateException("Expected ready16x16 source "+input);
            var palette=new LinkedHashMap<Integer,Integer>();double mean=0;int count=0;
            for(int y=0;y<16;y++)for(int x=0;x<16;x++){int c=source.getRGB(x,y);palette.put(c,c);if((c>>>24)!=0){mean+=((c>>16)&255)*.2126+((c>>8)&255)*.7152+(c&255)*.0722;count++;}}
            mean/=Math.max(1,count);var used=new HashSet<Integer>();
            if(!asset.copy)for(int old:new ArrayList<>(palette.keySet())){
                int alpha=old>>>24;if(alpha==0){used.add(old);continue;}
                double light=.35+.65*(((old>>16)&255)*.2126+((old>>8)&255)*.7152+(old&255)*.0722)/Math.max(1,mean);
                int replacement=(alpha<<24)|(channel(asset.red*light)<<16)|(channel(asset.green*light)<<8)|channel(asset.blue*light);
                while(used.contains(replacement))replacement=(replacement&0xffffff00)|(((replacement&255)+1)&255);
                palette.put(old,replacement);used.add(replacement);
            }
            BufferedImage result=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);var reverse=new LinkedHashMap<Integer,Integer>();
            for(int y=0;y<16;y++)for(int x=0;x<16;x++){
                int original=source.getRGB(x,y),replacement=palette.get(original);result.setRGB(x,y,replacement);
                if((original>>>24)!=(replacement>>>24))throw new IllegalStateException("Alpha changed "+asset.output);
                var previous=reverse.putIfAbsent(replacement,original);if(previous!=null&&previous!=original)throw new IllegalStateException("Palette collapsed source classes "+asset.output);
            }
            if(asset.copy)Files.copy(input,output,StandardCopyOption.REPLACE_EXISTING);else ImageIO.write(result,"PNG",output.toFile());
            BufferedImage stored=ImageIO.read(output.toFile());
            if(stored.getWidth()!=16||stored.getHeight()!=16)throw new IllegalStateException("Stored PNG dimensions changed");
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)if(stored.getRGB(x,y)!=result.getRGB(x,y))
                throw new IllegalStateException("Stored PNG changed the verified palette/alpha at "+x+","+y);
            if(index>0)outputs.append(",\n");
            outputs.append("    {\"output\":\"block/minerals/").append(asset.output).append(".png\",\"source\":\"").append(asset.source).append("\",\"author\":\"").append(field(record,"author"))
                    .append("\",\"source_url\":\"").append(field(record,"source_url")).append("\",\"source_sha256\":\"").append(hash(input)).append("\",\"output_sha256\":\"").append(hash(output))
                    .append("\",\"palette_classes\":").append(palette.size()).append(",\"operation\":\"").append(asset.copy?"Unchanged ready source PNG":"Bijective palette substitution only").append("\"}");
            int xx=index*144;g.drawImage(source,xx+8,16,128,128,null);g.drawImage(result,xx+8,168,128,128,null);
        }
        g.dispose();Path preview=Path.of("art/generated/minerals/palette-only.png");Files.createDirectories(preview.getParent());ImageIO.write(sheet,"PNG",preview.toFile());
        Path registry=Path.of("src/main/resources/assets/interstice/provenance/minerals.json");Files.createDirectories(registry.getParent());
        Files.writeString(registry,"{\n  \"license\":\"CC0-1.0\",\n  \"generator\":\"tools/GenerateMineralPalettes.java\",\n  \"pattern_and_alpha_verified\":true,\n  \"stored_png_verified\":true,\n  \"operation\":\"Whole16x16 ready sprites; one-to-one palette substitution, original coordinates and alpha unchanged; torch copied unchanged\",\n  \"outputs\":[\n"+outputs+"\n  ]\n}\n");
        System.out.println("MINERAL_PALETTES passed "+ASSETS.length+" sources; original pattern classes, resolution and alpha preserved.");
    }
}
