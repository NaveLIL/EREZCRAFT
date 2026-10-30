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
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.*;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.joml.Vector3f;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.ship.MarineBlock;

/** Each cell keeps solid and translucent quads in their proper render passes. */
public final class MarineModels {
    public static void geometry(ModelEvent.RegisterGeometryLoaders event){event.register(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"ne01"),(IGeometryLoader<Mesh>)(json,context)->new Mesh(json));}
    private record Layers(List<BakedQuad> opaque,List<BakedQuad> glass){}
    private static Layers layer(JsonObject json,IGeometryBakingContext context,Function<Material,TextureAtlasSprite> sprites){
        return new Layers(quads(json.getAsJsonArray("opaque"),context,sprites),quads(json.getAsJsonArray("glass"),context,sprites));
    }
    private static List<BakedQuad> quads(JsonArray faces,IGeometryBakingContext context,Function<Material,TextureAtlasSprite> sprites){
        var out=new ArrayList<BakedQuad>();
        for(var value:faces){var face=value.getAsJsonObject();var points=new ArrayList<Vector3f>();
            for(var p:face.getAsJsonArray("vertices")){var a=p.getAsJsonArray();points.add(new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()));}
            var normal=new Vector3f(points.get(1)).sub(points.get(0)).cross(new Vector3f(points.get(2)).sub(points.get(0))).normalize();
            if(!normal.isFinite())throw new JsonParseException("Degenerate NE01 quad");
            String material=face.get("material").getAsString();var sprite=sprites.apply(context.getMaterial(material));
            boolean emits=material.equals("lamp")||material.endsWith("_lamp")||material.equals("me_display")||material.equals("display");
            var q=new QuadBakingVertexConsumer();q.setSprite(sprite);q.setTintIndex(-1);q.setShade(!emits);q.setHasAmbientOcclusion(false);q.setDirection(Direction.getNearest(normal.x,normal.y,normal.z));
            for(int i=0;i<4;i++){var p=points.get(i);var uv=face.getAsJsonArray("uv").get(i).getAsJsonArray();q.addVertex(p.x,p.y,p.z).setColor(255,255,255,255).setUv(sprite.getU(uv.get(0).getAsFloat()),sprite.getV(uv.get(1).getAsFloat())).setUv2(emits?240:0,emits?240:0).setNormal(normal.x,normal.y,normal.z);}
            out.add(q.bakeQuad());
        }return List.copyOf(out);
    }
    private record Mesh(JsonObject json) implements IUnbakedGeometry<Mesh>{
        @Override public BakedModel bake(IGeometryBakingContext context,ModelBaker baker,Function<Material,TextureAtlasSprite> sprites,ModelState state,ItemOverrides overrides){
            var base=layer(json,context,sprites);var closed=new ArrayList<Layers>();var open=new ArrayList<Layers>();
            for(var value:json.getAsJsonArray("mechanisms")){var g=value.getAsJsonObject();closed.add(layer(g.getAsJsonObject("closed"),context,sprites));open.add(layer(g.getAsJsonObject("open"),context,sprites));}
            var builder=new SimpleBakedModel.Builder(false,true,false,context.getTransforms(),overrides).particle(sprites.apply(context.getMaterial("particle")));
            return new Split(builder.build(),base,closed,open);
        }
    }
    private static final class Split extends BakedModelWrapper<BakedModel>{
        private final Layers[] poses;private final ChunkRenderTypeSet types;
        Split(BakedModel original,Layers base,List<Layers> closed,List<Layers> open){
            super(original);poses=new Layers[1<<closed.size()];boolean solid=false,glass=false;
            for(int mask=0;mask<poses.length;mask++){
                var a=new ArrayList<>(base.opaque);var b=new ArrayList<>(base.glass);
                for(int i=0;i<closed.size();i++){var v=(mask&(1<<i))==0?closed.get(i):open.get(i);a.addAll(v.opaque);b.addAll(v.glass);}
                poses[mask]=new Layers(List.copyOf(a),List.copyOf(b));solid|=!a.isEmpty();glass|=!b.isEmpty();
            }
            types=solid&&glass?ChunkRenderTypeSet.of(RenderType.solid(),RenderType.translucent()):ChunkRenderTypeSet.of(glass?RenderType.translucent():RenderType.solid());
        }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data){return types;}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType type){
            if(side!=null)return List.of();int value=state!=null&&state.hasProperty(MarineBlock.POSE)?state.getValue(MarineBlock.POSE):0;var pose=poses[value&(poses.length-1)];
            if(type==RenderType.translucent())return pose.glass;if(type!=null)return pose.opaque;var all=new ArrayList<>(pose.opaque);all.addAll(pose.glass);return all;
        }
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
    }
    private MarineModels(){}
}
