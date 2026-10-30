import java.io.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.awt.image.BufferedImage;
import com.tom.cpm.shared.*;
import com.tom.cpm.shared.editor.*;
import com.tom.cpm.shared.editor.elements.*;
import com.tom.cpm.shared.editor.project.*;
import com.tom.cpm.shared.model.*;
import com.tom.cpm.shared.io.*;
import com.tom.cpm.shared.parts.*;
import com.tom.cpl.util.Image;

/** Official CPM parser/exporter in a bounded non-game JVM. No Minecraft client is launched. */
public final class CompileSkin {
    static Path root;
    static Object defaults(Method m) {return m.getReturnType()==boolean.class?false:m.getReturnType()==int.class?0:null;}
    static final com.tom.cpl.util.ImageIO.IImageIO images=new com.tom.cpl.util.ImageIO.IImageIO(){
        Image convert(BufferedImage a){Image b=new Image(a.getWidth(),a.getHeight());for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)b.setRGB(x,y,a.getRGB(x,y));return b;}
        BufferedImage convert(Image a){BufferedImage b=new BufferedImage(a.getWidth(),a.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)b.setRGB(x,y,a.getRGB(x,y));return b;}
        public Image read(File f)throws IOException{return convert(javax.imageio.ImageIO.read(f));}
        public Image read(InputStream f)throws IOException{return convert(javax.imageio.ImageIO.read(f));}
        public void write(Image a,File f)throws IOException{javax.imageio.ImageIO.write(convert(a),"PNG",f);}
        public void write(Image a,OutputStream f)throws IOException{javax.imageio.ImageIO.write(convert(a),"PNG",f);}
        public com.tom.cpl.math.Vec2i getSize(InputStream f)throws IOException{BufferedImage a=javax.imageio.ImageIO.read(f);return new com.tom.cpl.math.Vec2i(a.getWidth(),a.getHeight());}
    };
    public static void main(String[] args)throws Exception{
        root=Path.of(args[0]);var logger=(com.tom.cpl.util.ILogger)Proxy.newProxyInstance(CompileSkin.class.getClassLoader(),new Class[]{com.tom.cpl.util.ILogger.class},(p,m,a)->{if(a!=null)System.out.println("CPM "+m.getName()+" "+Arrays.toString(a));return defaults(m);});
        var config=new com.tom.cpl.config.ModConfigFile(root.resolve("reports/headless-config.json").toFile());
        var common=(MinecraftCommonAccess)Proxy.newProxyInstance(CompileSkin.class.getClassLoader(),new Class[]{MinecraftCommonAccess.class},(p,m,a)->switch(m.getName()){
            case "getConfig"->config;case "getLogger"->logger;case "getSupportedFeatures"->EnumSet.allOf(PlatformFeature.class);case "getMCVersion"->"1.21.1";case "getMCBrand"->"isolated-model-export";case "getModVersion"->"0.6.27a";
            case "getBlockStateHandler"->new HeadlessBlockHandler();case "getItemStackHandler"->new HeadlessItemHandler();case "getEntityTypeHandler"->new HeadlessEntityHandler();default->defaults(m);});
        MinecraftObjectHolder.setCommonObject(common);
        var client=(MinecraftClientAccess)Proxy.newProxyInstance(CompileSkin.class.getClassLoader(),new Class[]{MinecraftClientAccess.class},(p,m,a)->switch(m.getName()){
            case "getImageIO"->images;case "getGameDir"->root.resolve("reports").toFile();case "getSkinType"->SkinType.SLIM;
            case "getBuiltinTags"->new com.tom.cpl.tag.AllTagManagers();
            case "getBiomeHandler"->new HeadlessBiomeHandler();
            case "getProxy"->java.net.Proxy.NO_PROXY;
            case "createTexture"->new com.tom.cpl.util.DynamicTexture.ITexture(){public void bind(){}public void load(Image im){}public void free(){}};
            case "executeOnGameThread","executeLater","executeNextFrame"->{((Runnable)a[0]).run();yield null;}
            default->defaults(m);
        });MinecraftObjectHolder.setClientObject(client);
        var editor=new Editor();editor.ui=(com.tom.cpl.gui.UI)Proxy.newProxyInstance(CompileSkin.class.getClassLoader(),new Class[]{com.tom.cpl.gui.UI.class},(p,m,a)->{
            if(m.getName().equals("i18nFormat"))return a[0].toString();if(m.getName().equals("executeLater")){((Runnable)a[0]).run();return null;}return defaults(m);});
        editor.skinType=SkinType.SLIM;
        for(var part:PlayerModelParts.VALUES)if(part!=PlayerModelParts.CUSTOM_PART)editor.elements.add(new ModelElement(editor,ElementType.ROOT_PART,part));
        var project=new ProjectFile();project.load(root.resolve("exports/Kizya_Pink_PK02.cpmproject").toFile()).join();ProjectIO.loadProject(editor,project);editor.restitchTextures();
        var prepare=Exporter.class.getDeclaredMethod("prepareDefinition",Editor.class);prepare.setAccessible(true);var definition=(ModelPartDefinition)prepare.invoke(null,editor);
        int[] count={0};var ids=new LinkedHashMap<String,Integer>();Editor.walkElements(editor.elements,e->{if(e.type==ElementType.NORMAL){count[0]++;ids.put(e.name,e.id);}});
        var bytes=new ByteArrayOutputStream();bytes.write(com.tom.cpm.shared.definition.ModelDefinitionLoader.HEADER);
        try(var io=new IOHelper(new ChecksumOutputStream(bytes))){io.writeObjectBlock(new ModelPartSkinType(editor.skinType));definition.writeBlocks(io);io.writeObjectBlock(ModelPartEnd.END);}
        byte[] model=bytes.toByteArray();Path out=root.resolve("exports/Kizya_Pink_PK02.cpmmodel");
        try(var stream=Files.newOutputStream(out)){stream.write(com.tom.cpm.shared.definition.ModelDefinitionLoader.HEADER);try(var io=new IOHelper(new ChecksumOutputStream(stream))){io.writeUTF("Kizya / Pink PK02");io.writeUTF("Painted personal avatar; slim; equipment-aware hair");io.writeByteArray(model);io.writeVarInt(0);io.writeVarInt(0);}}
        var parsed=ModelFile.load(out.toFile());if(!Arrays.equals(parsed.getDataBlock(),model))throw new IOException("Official CPM model round trip mismatch");
        var report=new LinkedHashMap<String,Object>();report.put("revision","PK02");report.put("official_project_parser",true);report.put("official_model_exporter",true);report.put("official_model_reader_round_trip",true);report.put("parts",count[0]);report.put("skin_type",editor.skinType.getName());report.put("packet_bytes",model.length);report.put("self_contained_local_file",true);report.put("non_game_jvm_heap_limit_mb",384);report.put("minecraft_renderer_verified",false);report.put("account_skin_changed",false);
        report.put("runtime_element_ids",ids);Files.writeString(root.resolve("reports/CPM_FORMAT_CHECK.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println("KIZYA_CPM_EXPORTED parts="+count[0]+" bytes="+model.length);
    }
}
