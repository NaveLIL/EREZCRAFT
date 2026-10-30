package pro.erez.interstice.client;

import com.google.gson.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.*;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.joml.Vector3f;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.vehicle.EitanPreviewData;

/** Palette meshes bake once into normal chunk geometry, with no per-part tick. */
public final class EitanModels {
    public static void geometry(ModelEvent.RegisterGeometryLoaders event){event.register(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"eitan_preview"),(IGeometryLoader<Mesh>)(json,context)->new Mesh(json));}
    static List<BakedQuad> quads(JsonArray faces,IGeometryBakingContext context,Function<Material,TextureAtlasSprite> sprites){var out=new ArrayList<BakedQuad>();for(var value:faces){var f=value.getAsJsonObject();var points=new ArrayList<Vector3f>();for(var p:f.getAsJsonArray("vertices")){var a=p.getAsJsonArray();points.add(new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()));}var n=new Vector3f(points.get(1)).sub(points.get(0)).cross(new Vector3f(points.get(2)).sub(points.get(0))).normalize();if(!n.isFinite())throw new JsonParseException("Degenerate E01 quad");String material=f.get("material").getAsString();var sprite=sprites.apply(context.getMaterial(material));boolean emits=material.equals("lamp")||material.equals("lens")||material.equals("screen");var q=new QuadBakingVertexConsumer();q.setSprite(sprite);q.setTintIndex(-1);q.setShade(!emits);q.setHasAmbientOcclusion(false);q.setDirection(Direction.getNearest(n.x,n.y,n.z));for(var p:points)q.addVertex(p.x,p.y,p.z).setColor(255,255,255,255).setUv(sprite.getU(.5f),sprite.getV(.5f)).setUv2(emits?240:0,emits?240:0).setNormal(n.x,n.y,n.z);out.add(q.bakeQuad());}return List.copyOf(out);}
    private record Mesh(JsonObject json) implements IUnbakedGeometry<Mesh>{
        @Override public BakedModel bake(IGeometryBakingContext context,ModelBaker baker,Function<Material,TextureAtlasSprite> sprites,ModelState state,ItemOverrides overrides){var base=quads(json.getAsJsonArray("base"),context,sprites);var poses=new ArrayList<List<BakedQuad>>();for(var a:json.getAsJsonArray("poses")){var p=new ArrayList<>(base);p.addAll(quads(a.getAsJsonArray(),context,sprites));poses.add(List.copyOf(p));}if(poses.size()!=4)throw new JsonParseException("Expected E01 poses");return new Model(new SimpleBakedModel.Builder(false,true,false,context.getTransforms(),overrides).particle(sprites.apply(context.getMaterial("particle"))).build(),List.copyOf(poses));}
    }
    private static final class Model extends BakedModelWrapper<BakedModel>{
        private final List<List<BakedQuad>> poses;
        Model(BakedModel base,List<List<BakedQuad>> p){super(base);poses=p;}
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data){return ChunkRenderTypeSet.of(RenderType.solid());}
        @Override public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource random,ModelData data,RenderType type){return side!=null||type!=null&&type!=RenderType.solid()?List.of():poses.get(s==null?0:s.getValue(EitanPreviewData.POSE));}
        @Override public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource random){return getQuads(s,side,random,ModelData.EMPTY,null);}
    }
    private EitanModels(){}
}
