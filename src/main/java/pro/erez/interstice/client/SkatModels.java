package pro.erez.interstice.client;
import com.google.gson.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.geometry.*;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.joml.Vector3f;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.ship.SkatLampBlock;

/** Exact saved Blender polygons, tiled UVs and authored textures baked once into ordinary quads. */
public final class SkatModels {
    public static ModelResourceLocation id(String name){return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"block/skat/"+name));}
    public static void geometry(ModelEvent.RegisterGeometryLoaders event){event.register(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"skat"),(IGeometryLoader<Mesh>)(json,context)->new Mesh(json.getAsJsonArray("quads")));}
    public static void additional(ModelEvent.RegisterAdditional event){for(String name:List.of("rotor","door_port","door_starboard","hatch"))event.register(id(name));}
    private record Mesh(JsonArray faces) implements IUnbakedGeometry<Mesh> {
        @Override public BakedModel bake(IGeometryBakingContext context,ModelBaker baker,Function<Material,TextureAtlasSprite> sprites,ModelState state,ItemOverrides overrides){
            var builder=new SimpleBakedModel.Builder(false,true,false,context.getTransforms(),overrides).particle(sprites.apply(context.getMaterial("particle")));
            var dark=new SimpleBakedModel.Builder(false,true,false,context.getTransforms(),overrides).particle(sprites.apply(context.getMaterial("particle")));
            var glassBuilder=new SimpleBakedModel.Builder(false,true,false,context.getTransforms(),overrides).particle(sprites.apply(context.getMaterial("particle")));
            boolean glass=false;for(var value:faces){var face=value.getAsJsonObject();var points=new ArrayList<Vector3f>();
                for(var v:face.getAsJsonArray("vertices")){var a=v.getAsJsonArray();points.add(new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()));}
                var normal=new Vector3f(points.get(1)).sub(points.get(0)).cross(new Vector3f(points.get(2)).sub(points.get(0))).normalize();if(!normal.isFinite())throw new JsonParseException("Invalid SK02 face");
                String material=face.get("material").getAsString();boolean transparent=material.equals("glass");glass|=transparent;
                var sprite=sprites.apply(context.getMaterial(material));boolean emits=face.has("light_channel");
                var lit=quad(points,face,sprite,normal,transparent,emits);if(transparent)glassBuilder.addUnculledFace(lit);else{builder.addUnculledFace(lit);dark.addUnculledFace(emits?quad(points,face,sprite,normal,false,false):lit);}
            }
            return new Switchable(builder.build(),dark.build(),glass?glassBuilder.build():null);
        }
    }
    private static net.minecraft.client.renderer.block.model.BakedQuad quad(List<Vector3f> points,JsonObject face,TextureAtlasSprite sprite,Vector3f normal,boolean transparent,boolean emits){
        var quad=new QuadBakingVertexConsumer();quad.setSprite(sprite);quad.setTintIndex(-1);quad.setShade(!emits);quad.setHasAmbientOcclusion(false);quad.setDirection(Direction.getNearest(normal.x,normal.y,normal.z));
        for(int i=0;i<4;i++){var p=points.get(i);var uv=face.getAsJsonArray("uv").get(i).getAsJsonArray();quad.addVertex(p.x,p.y,p.z).setColor(255,255,255,255).setUv(sprite.getU(uv.get(0).getAsFloat()),sprite.getV(uv.get(1).getAsFloat())).setUv2(emits?240:0,emits?240:0).setNormal(normal.x,normal.y,normal.z);}return quad.bakeQuad();
    }
    public static boolean hasGlass(BakedModel model){return model instanceof Switchable s&&s.glass!=null;}
    private static final class Switchable extends net.neoforged.neoforge.client.model.BakedModelWrapper<BakedModel>{
        private final BakedModel dark,glass;Switchable(BakedModel lit,BakedModel dark,BakedModel glass){super(lit);this.dark=dark;this.glass=glass;}
        private BakedModel selected(net.minecraft.world.level.block.state.BlockState s){return s!=null&&s.hasProperty(SkatLampBlock.LIT)&&!s.getValue(SkatLampBlock.LIT)?dark:originalModel;}
        @Override public net.neoforged.neoforge.client.ChunkRenderTypeSet getRenderTypes(net.minecraft.world.level.block.state.BlockState s,net.minecraft.util.RandomSource random,net.neoforged.neoforge.client.model.data.ModelData data){return glass==null?net.neoforged.neoforge.client.ChunkRenderTypeSet.of(net.minecraft.client.renderer.RenderType.solid()):net.neoforged.neoforge.client.ChunkRenderTypeSet.of(net.minecraft.client.renderer.RenderType.solid(),net.minecraft.client.renderer.RenderType.translucent());}
        @Override public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState s,Direction d,net.minecraft.util.RandomSource r){return selected(s).getQuads(s,d,r);}
        @Override public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState s,Direction d,net.minecraft.util.RandomSource r,net.neoforged.neoforge.client.model.data.ModelData data,net.minecraft.client.renderer.RenderType type){return type==net.minecraft.client.renderer.RenderType.translucent()?(glass==null?List.of():glass.getQuads(s,d,r)):selected(s).getQuads(s,d,r);}
    }
    private SkatModels(){}
}
