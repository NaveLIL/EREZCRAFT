import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Standalone JDK21 candidate generator. Never writes runtime resources or invokes old art generators. */
public final class GenerateRealmV6Palettes {
    static final Path BASE=Path.of("art/sources/accepted-v5-palettes");
    static final Path MAIN=Path.of("src/main/resources/assets/interstice/textures");
    static final String COMMIT="d01c9f2d627ab2d00127613ded5e85d5e9f5936b";
    record Asset(String path,String sha,String family,String role,BufferedImage image) {}
    record Key(String family,String role) {}
    record Palette(String id,String name,int rock,int surface,int deposit,int wood,int leaf,int glow,int rust) {}
    static final List<Palette> PALETTES=List.of(
        new Palette("A","Mineral night",0x343B46,0x59616A,0xB7B9A9,0x30383B,0x647D79,0x81C9BE,0x87585B),
        new Palette("B","Chemotrophic malachite",0x394843,0x58695E,0xC1B99B,0x323B35,0x87927B,0x91C9AE,0x8E6267),
        new Palette("C","Salt void",0x4C4655,0x85838E,0xC5C3B7,0x34303C,0x7A8494,0xA1BCCB,0xA77361));
    static Set<Integer> host;
    static String hash(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static String quote(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    static double luma(int c){return ((c>>>16)&255)*.2126+((c>>>8)&255)*.7152+(c&255)*.0722;}
    static Set<Integer> colors(BufferedImage im){var c=new HashSet<Integer>();for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++)c.add(im.getRGB(x,y));return c;}
    static Key key(Asset a,int old){
        if(a.role.startsWith("COMPOSITE"))return host.contains(old)?new Key("rift-host","ROCK"):
            a.role.equals("COMPOSITE_SILVER")?new Key("rift-silver","SILVER"):new Key("rift-cover","SURFACE");
        if(a.role.equals("LEAF_GLOW"))return old==0xff55b3a6?new Key(a.family+"-sparkle","GLOW"):new Key(a.family,"LEAF");
        return new Key(a.family,a.role);
    }
    static int base(Palette p,String role){return switch(role){
        case "ROCK"->p.rock;case "SURFACE"->p.surface;case "DEPOSIT"->p.deposit;case "WOOD"->p.wood;
        case "LEAF"->p.leaf;case "GLOW"->p.glow;case "RUST"->p.rust;case "STRUCTURE"->blend(p.rock,p.surface,.48);
        case "WOOD_SMOOTH"->blend(p.wood,p.surface,.42);case "WOOD_END"->blend(p.wood,p.surface,.65);
        case "HAZARD"->blend(p.rust,0xC38D76,.30);case "FRUIT"->0xBC9564;case "UNRIPE"->blend(p.wood,p.leaf,.65);
        case "SILVER"->blend(p.deposit,0xD7DBDF,.55);case "COAL"->blend(p.rock,0x12151B,.62);case "AMBER"->0xBB935E;
        default->throw new IllegalStateException("Unknown material role "+role);};}
    static int blend(int a,int b,double w){int n=0;for(int shift:new int[]{16,8,0})n|=(int)Math.round(((a>>>shift)&255)*(1-w)+((b>>>shift)&255)*w)<<shift;return n;}
    static int channel(double n){return Math.max(0,Math.min(255,(int)Math.round(n)));}
    static int substitute(int old,int rgb,double mean){
        if((old>>>24)==0)return old;
        double factor=.34+.66*luma(old)/Math.max(mean,1);
        return (old&0xff000000)|(channel(((rgb>>>16)&255)*factor)<<16)|(channel(((rgb>>>8)&255)*factor)<<8)|channel((rgb&255)*factor);
    }
    /** Deterministic nearest unused RGB, retaining alpha; collisions never merge pattern classes. */
    static int unused(int ideal,Set<Integer> used){
        if(!used.contains(ideal))return ideal;
        int alpha=ideal&0xff000000,r=(ideal>>>16)&255,g=(ideal>>>8)&255,b=ideal&255;
        for(int radius=1;radius<=255;radius++)for(int dr=-radius;dr<=radius;dr++)for(int dg=-radius;dg<=radius;dg++)for(int db=-radius;db<=radius;db++){
            if(Math.max(Math.abs(dr),Math.max(Math.abs(dg),Math.abs(db)))!=radius)continue;
            int nr=r+dr,ng=g+dg,nb=b+db;if(nr<0||nr>255||ng<0||ng>255||nb<0||nb>255)continue;
            int next=alpha|(nr<<16)|(ng<<8)|nb;if(!used.contains(next))return next;
        }
        throw new IllegalStateException("RGB space exhausted");
    }
    static List<Asset> assets()throws Exception{
        String manifest=Files.readString(BASE.resolve("manifest.json"));
        require(manifest.contains(COMMIT),"Wrong immutable V5 commit");
        String recorded=Files.readString(BASE.resolve("manifest.sha256")).split(" ")[0];
        require(hash(BASE.resolve("manifest.json")).equals(recorded),"Baseline manifest changed");
        var pattern=Pattern.compile("\\{\\s*\"path\":\\s*\"([^\"]+)\",\\s*\"sha256\":\\s*\"([0-9a-f]{64})\",\\s*\"family\":\\s*\"([^\"]+)\",\\s*\"role\":\\s*\"([^\"]+)\"");
        var match=pattern.matcher(manifest);var result=new ArrayList<Asset>();
        while(match.find()){
            String name=match.group(1);require(name.startsWith("block/")&&!name.contains("..")&&!name.matches("block/(heavy|light)_.*"),"Forbidden overlay asset "+name);
            Path file=BASE.resolve(name);require(hash(file).equals(match.group(2)),"Immutable accepted PNG changed: "+name);
            var im=ImageIO.read(file.toFile());require(im!=null,"Invalid accepted PNG "+name);
            require((im.getWidth()==16&&(im.getHeight()==16||im.getHeight()==32))||(name.equals("block/cave/clingweed.png")&&im.getWidth()==32&&im.getHeight()==32),"Unaccepted dimensions "+name);
            result.add(new Asset(name,match.group(2),match.group(3),match.group(4),im));
        }
        require(!result.isEmpty(),"No accepted assets");host=colors(result.stream().filter(a->a.path.equals("block/riftstone.png")).findFirst().orElseThrow().image);
        return List.copyOf(result);
    }
    static Map<Key,Map<Integer,Integer>> mappings(List<Asset> assets,Palette p){
        var groups=new TreeMap<Key,Set<Integer>>(Comparator.comparing(Key::family).thenComparing(Key::role));
        for(var a:assets)for(int old:colors(a.image))groups.computeIfAbsent(key(a,old),k->new HashSet<>()).add(old);
        var result=new HashMap<Key,Map<Integer,Integer>>();
        for(var entry:groups.entrySet()){
            var ordered=new ArrayList<>(entry.getValue());ordered.sort(Comparator.comparingDouble((Integer c)->luma(c)).thenComparingInt(c->c));
            double mean=ordered.stream().filter(c->(c>>>24)!=0).mapToDouble(GenerateRealmV6Palettes::luma).average().orElse(1);
            var lut=new LinkedHashMap<Integer,Integer>();var used=new HashSet<Integer>();
            for(int old:ordered)if((old>>>24)==0){lut.put(old,old);used.add(old);}
            for(int old:ordered)if((old>>>24)!=0){int mapped=entry.getKey().role.equals("IDENTITY")?old:unused(substitute(old,base(p,entry.getKey().role),mean),used);lut.put(old,mapped);used.add(mapped);}
            result.put(entry.getKey(),lut);
        }
        return result;
    }
    static String generate(List<Asset> assets,Palette p,Path output)throws Exception{
        Path pack=output.resolve("v6-palette-"+p.id);Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":34,\"description\":\"EREZCRAFT V6 palette "+p.id+" - disposable comparison\"}}\n");
        var maps=mappings(assets,p);var rows=new ArrayList<String>();
        var sheet=new BufferedImage(8*144,((assets.size()+7)/8)*164,BufferedImage.TYPE_INT_ARGB);var graphics=sheet.createGraphics();
        graphics.setColor(new Color(0x121820));graphics.fillRect(0,0,sheet.getWidth(),sheet.getHeight());graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int index=0;
        for(var a:assets){
            var im=new BufferedImage(a.image.getWidth(),a.image.getHeight(),BufferedImage.TYPE_INT_ARGB);
            var forward=new TreeMap<Integer,Integer>();var reverse=new HashMap<Integer,Integer>();var used=new HashSet<Integer>();
            // Transparent original RGB is retained too, even though it is invisible in Minecraft.
            for(int old:colors(a.image))if((old>>>24)==0)used.add(old);
            // Mixed role textures still need one-to-one classes across all roles.
            var originals=new ArrayList<>(colors(a.image));originals.sort(Integer::compareUnsigned);
            for(int old:originals){int mapped=maps.get(key(a,old)).get(old);if((old>>>24)!=0&&used.contains(mapped))mapped=unused(mapped,used);forward.put(old,mapped);used.add(mapped);}
            for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++){
                int old=a.image.getRGB(x,y),mapped=forward.get(old);im.setRGB(x,y,mapped);
                require((old>>>24)==(mapped>>>24),"Alpha changed "+a.path);Integer previous=reverse.putIfAbsent(mapped,old);require(previous==null||previous==old,"Classes collapsed "+a.path);
            }
            Path target=pack.resolve("assets/interstice/textures").resolve(a.path);Files.createDirectories(target.getParent());
            if(a.role.equals("IDENTITY"))Files.copy(BASE.resolve(a.path),target,java.nio.file.StandardCopyOption.REPLACE_EXISTING);else ImageIO.write(im,"PNG",target.toFile());
            var stored=ImageIO.read(target.toFile());require(stored.getWidth()==a.image.getWidth()&&stored.getHeight()==a.image.getHeight(),"Stored size changed");
            for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++)require(stored.getRGB(x,y)==im.getRGB(x,y),"Stored PNG changed "+a.path);
            var lut=new ArrayList<String>();for(var entry:forward.entrySet())lut.add(quote(String.format("%08x",entry.getKey()))+":"+quote(String.format("%08x",entry.getValue())));
            rows.add("{\"path\":"+quote(a.path)+",\"family\":"+quote(a.family)+",\"role\":"+quote(a.role)+",\"input_sha256\":"+quote(a.sha)+",\"output_sha256\":"+quote(hash(target))+",\"width\":"+im.getWidth()+",\"height\":"+im.getHeight()+",\"classes\":"+forward.size()+",\"argb_lut\":{"+String.join(",",lut)+"}}");
            int sx=(index%8)*144,sy=(index/8)*164;graphics.drawImage(im,sx+8,sy+8,128,128,null);graphics.setColor(new Color(0xb6c2cc));graphics.drawString(Path.of(a.path).getFileName().toString().replace(".png",""),sx+4,sy+151);index++;
        }
        graphics.dispose();ImageIO.write(sheet,"PNG",output.resolve("palette-"+p.id+"-sheet.png").toFile());
        String report="{\"format\":1,\"palette\":"+quote(p.id)+",\"name\":"+quote(p.name)+",\"generator\":\"tools/GenerateRealmV6Palettes.java\",\"baseline_commit\":"+quote(COMMIT)+",\"baseline_manifest_sha256\":"+quote(hash(BASE.resolve("manifest.json")))+",\"operation\":\"Bijective RGB palette substitution only; accepted V5 coordinates, dimensions, alpha and pattern classes preserved\",\"runtime_resources_written\":false,\"oceans_excluded\":true,\"assets\":["+String.join(",\n",rows)+"]}\n";
        Files.writeString(output.resolve("palette-"+p.id+"-provenance.json"),report);return report;
    }
    static void sharedHosts(Path output,String id)throws Exception{
        Path root=output.resolve("v6-palette-"+id+"/assets/interstice/textures");
        var baseline=ImageIO.read(BASE.resolve("block/riftstone.png").toFile());var replacement=ImageIO.read(root.resolve("block/riftstone.png").toFile());
        var lut=new HashMap<Integer,Integer>();for(int y=0;y<16;y++)for(int x=0;x<16;x++)lut.put(baseline.getRGB(x,y),replacement.getRGB(x,y));
        for(String name:List.of("riftsilver_ore","rift_frame","abyssal_turf_side")){
            var left=ImageIO.read(BASE.resolve("block/"+name+".png").toFile());var right=ImageIO.read(root.resolve("block/"+name+".png").toFile());
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)if(lut.containsKey(left.getRGB(x,y)))require(right.getRGB(x,y)==lut.get(left.getRGB(x,y)),"Composite host LUT changed "+name);
        }
        for(String name:List.of("vaultstone","weathered_vaultstone")){
            var base=ImageIO.read(root.resolve("block/stone/"+name+".png").toFile());
            for(int n=1;n<=2;n++){var variant=ImageIO.read(root.resolve("block/stone/"+name+"_"+n+".png").toFile());for(int i=0;i<16;i++){
                require(base.getRGB(0,i)==variant.getRGB(0,i)&&base.getRGB(15,i)==variant.getRGB(15,i)&&base.getRGB(i,0)==variant.getRGB(i,0)&&base.getRGB(i,15)==variant.getRGB(i,15),"Variant seam changed");}}
        }
    }
    public static void main(String[] args)throws Exception{
        Path output=Path.of(args.length==0?".verification/v6-palette-candidates":args[0]).toAbsolutePath().normalize();
        require(output.startsWith(Path.of(".verification").toAbsolutePath().normalize()),"Candidates must stay in ignored .verification");Files.createDirectories(output);
        var before=new TreeMap<String,String>();try(var files=Files.walk(MAIN)){for(var p:files.filter(Files::isRegularFile).toList())before.put(p.toString(),hash(p));}
        var assets=assets();for(var p:PALETTES){generate(assets,p,output);sharedHosts(output,p.id);}
        for(var entry:before.entrySet())require(hash(Path.of(entry.getKey())).equals(entry.getValue()),"Runtime asset changed "+entry.getKey());
        Files.writeString(output.resolve("validation.json"),"{\"passed\":true,\"palette_count\":3,\"assets_per_palette\":"+assets.size()+",\"baseline_commit\":"+quote(COMMIT)+",\"same_coordinates_dimensions_alpha_classes\":true,\"shared_host_lut_verified\":true,\"variant_edges_verified\":true,\"runtime_texture_files_unchanged\":"+before.size()+",\"oceans_not_written\":true,\"native_gallery_performed\":false}\n");
        System.out.println("V6_PALETTE_CANDIDATES passed 3 x "+assets.size()+" PNGs; main textures and ocean bytes unchanged. Native review is still required.");
    }
}
