package pro.erez.interstice.client;
import com.google.gson.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.vehicle.EitanData;

public final class EitanVehicleModels {
    private static final Map<String,ModelResourceLocation> BONES=new HashMap<>();
    public static void geometry(ModelEvent.RegisterGeometryLoaders e){e.register(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"eitan_vehicle"),(IGeometryLoader<Mesh>)(json,context)->new Mesh(json));}
    public static void additional(ModelEvent.RegisterAdditional e){for(var value:EitanData.metadata("bones")){var b=value.getAsJsonObject();var model=ModelResourceLocation.standalone(ResourceLocation.parse(b.get("model").getAsString()));BONES.put(b.get("name").getAsString(),model);e.register(model);}}
    public static BakedModel bone(String name){return Minecraft.getInstance().getModelManager().getModel(BONES.get(name));}
    private record Mesh(JsonObject json) implements IUnbakedGeometry<Mesh>{public BakedModel bake(IGeometryBakingContext c,ModelBaker b,Function<Material,TextureAtlasSprite> sprites,ModelState state,ItemOverrides o){return new Model(new SimpleBakedModel.Builder(false,true,false,c.getTransforms(),o).particle(sprites.apply(c.getMaterial("particle"))).build(),EitanModels.quads(json.getAsJsonArray("quads"),c,sprites));}}
    private static final class Model extends BakedModelWrapper<BakedModel>{private final List<BakedQuad> quads;Model(BakedModel b,List<BakedQuad> q){super(b);quads=q;}@Override public ChunkRenderTypeSet getRenderTypes(BlockState s,RandomSource r,ModelData d){return ChunkRenderTypeSet.of(RenderType.solid());}@Override public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource r,ModelData d,RenderType type){return side==null&&(type==null||type==RenderType.solid())?quads:List.of();}@Override public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource r){return side==null?quads:List.of();}}
    private EitanVehicleModels(){}
}
