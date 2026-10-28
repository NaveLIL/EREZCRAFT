package pro.erez.interstice.client;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.geometry.*;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.joml.Vector3f;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.gear.*;

/** Exact derived armor meshes, baked once by NeoForge and rendered as ordinary inventory items. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ArmorIconModels {
    private static final Map<String,int[]> PALETTES=new HashMap<>();
    private ArmorIconModels(){}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(Interstice.ID,path);}
    @SubscribeEvent public static void geometry(ModelEvent.RegisterGeometryLoaders event){event.register(id("armor_icon"),(IGeometryLoader<Mesh>)(json,context)->read(json));}
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)manager->PALETTES.clear());}
    @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Item event){
        for(String tier:List.of("riftsilver","riftsteel","aegis","kotomyaf"))for(String part:List.of("helmet","chestplate","leggings","boots")){
            String name=tier+"_"+part;
            var item=tier.equals("aegis")?AegisArmor.item(name):tier.equals("kotomyaf")?KotomyafArmor.item(name):RealmArmaments.item(name);
            event.register((stack,tint)->color(name,stack,tint),item);
        }
        for(String name:List.of("field_backpack","expedition_backpack","rift_backpack")){
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id(name));event.register((stack,tint)->color(name,stack,tint),item);
        }
    }
    private static int color(String name,ItemStack stack,int tint){
        if(tint<0||tint>=7)return -1;
        var dye=stack.get(DataComponents.DYED_COLOR);if(tint==0&&dye!=null)return 0xff000000|dye.rgb();
        return PALETTES.computeIfAbsent(name,ArmorIconModels::palette)[tint];
    }
    private static int[] palette(String name){
        int[] colors=new int[7];Arrays.fill(colors,-1);
        try(var reader=Minecraft.getInstance().getResourceManager().openAsReader(id("models/item/"+name+".json"))){
            var json=JsonParser.parseReader(reader).getAsJsonObject();var array=json.getAsJsonArray("palette");
            // A resource pack can replace our model with a conventional item without tint data.
            if(array!=null&&array.size()==7)for(int i=0;i<7;i++)colors[i]=0xff000000|Integer.parseInt(array.get(i).getAsString().substring(1),16);
        }catch(IOException|RuntimeException ignored){/* Conventional replacement packs retain their own colors. */}
        return colors;
    }
    private record Face(int role,List<Vector3f> points,String material,float[][] uv){}
    private static Mesh read(JsonObject json){
        var faces=new ArrayList<Face>();var array=json.getAsJsonArray("quads");
        if(array==null||array.isEmpty()||array.size()>12000)throw new JsonParseException("Invalid armor icon face count");
        for(var entry:array){
            var face=entry.getAsJsonObject();int role=face.get("role").getAsInt();var vertices=face.getAsJsonArray("vertices");
            if(role<0||role>6||vertices.size()!=4)throw new JsonParseException("Invalid armor icon face");
            var points=new ArrayList<Vector3f>();
            for(var value:vertices){var p=value.getAsJsonArray();if(p.size()!=3)throw new JsonParseException("Expected XYZ vertex");
                var v=new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat());
                if(!v.isFinite())throw new JsonParseException("Non-finite icon vertex");points.add(v);
            }
            String material=face.has("material")?face.get("material").getAsString():role==4?"strap":"armor";
            float[][] uv=new float[][]{{8F/64,8F/32},{8F/64,0},{16F/64,0},{16F/64,8F/32}};
            if(face.has("uv"))for(int i=0;i<4;i++){var value=face.getAsJsonArray("uv").get(i).getAsJsonArray();uv[i]=new float[]{value.get(0).getAsFloat(),value.get(1).getAsFloat()};}
            faces.add(new Face(role,List.copyOf(points),material,uv));
        }
        return new Mesh(List.copyOf(faces));
    }
    private record Mesh(List<Face> faces) implements IUnbakedGeometry<Mesh> {
        @Override public BakedModel bake(IGeometryBakingContext context,ModelBaker baker,Function<Material,TextureAtlasSprite> sprites,ModelState state,ItemOverrides overrides){
            var builder=new SimpleBakedModel.Builder(false,context.useBlockLight(),true,context.getTransforms(),overrides)
                    .particle(sprites.apply(context.getMaterial("particle")));
            for(var face:faces){
                var sprite=sprites.apply(context.getMaterial(face.material));
                var points=face.points;var a=new Vector3f(points.get(1)).sub(points.get(0));var b=new Vector3f(points.get(2)).sub(points.get(0));
                var normal=a.cross(b).normalize();if(!normal.isFinite())throw new IllegalStateException("Degenerate armor icon face");
                var quad=new QuadBakingVertexConsumer();quad.setTintIndex(face.role);quad.setSprite(sprite);quad.setShade(true);quad.setHasAmbientOcclusion(false);
                quad.setDirection(Direction.getNearest(normal.x,normal.y,normal.z));
                for(int i=0;i<4;i++){
                    var p=points.get(i);
                    // Same opaque 8x8 region on the native 64x32 armor texture as the wearable mesh.
                    float u=face.uv[i][0],v=face.uv[i][1];
                    quad.addVertex(p.x,p.y,p.z).setColor(255,255,255,255).setUv(sprite.getU(u),sprite.getV(v)).setUv2(0,0).setNormal(normal.x,normal.y,normal.z);
                }
                builder.addUnculledFace(quad.bakeQuad());
            }
            return builder.build(net.neoforged.neoforge.client.RenderTypeGroup.EMPTY);
        }
    }
}
