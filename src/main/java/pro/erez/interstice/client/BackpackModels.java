package pro.erez.interstice.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.equipment.BackpackStorage;
import pro.erez.interstice.gear.RealmArmor;

/** Cached source meshes, batched by three materials; no per-frame resource reads or mesh creation. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class BackpackModels {
    private record Face(float[][] points,float[][] uv,float[] normal,int role,String anchor,boolean sternum){}
    private record Pack(int[] palette,Map<String,List<Face>> batches){}
    private enum Profile {
        BARE(2.22F,-2.56F,false),ARMOR(3.1F,-3.1F,false),REALM(4.2F,-4.6F,false),MAGEN(5.7F,-5.7F,true),ELYTRA(4.15F,-2.56F,false);
        final float back,front;final boolean magen;Profile(float b,float f,boolean m){back=b;front=f;magen=m;}
    }
    private record DrawFace(float[][] points,float[][] uv,float[] normal,int color,boolean glowing){}
    private record DrawBatch(ResourceLocation texture,List<DrawFace> faces){}
    private static final Map<String,EnumMap<Profile,List<DrawBatch>>> PREPARED=new HashMap<>();
    private static final Map<String,Pack> PACKS=new HashMap<>();private static final Map<String,ResourceLocation> TEXTURES=new HashMap<>();
    public static long renderedQuads;private BackpackModels(){}
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)m->{PACKS.clear();TEXTURES.clear();PREPARED.clear();});}
    private static float[][] matrix(JsonArray array){float[][] out=new float[array.size()][];for(int i=0;i<out.length;i++){var v=array.get(i).getAsJsonArray();out[i]=new float[v.size()];for(int j=0;j<out[i].length;j++)out[i][j]=v.get(j).getAsFloat();}return out;}
    private static void load(){
        if(!PACKS.isEmpty())return;
        try(var reader=Minecraft.getInstance().getResourceManager().openAsReader(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"equipment/backpacks.json"))){
            var json=JsonParser.parseReader(reader).getAsJsonObject();json.getAsJsonObject("textures").entrySet().forEach(e->TEXTURES.put(e.getKey(),ResourceLocation.parse(e.getValue().getAsString())));
            for(var entry:json.getAsJsonObject("sets").entrySet()){
                var data=entry.getValue().getAsJsonObject();int[] palette=new int[7];for(int i=0;i<7;i++)palette[i]=Integer.parseInt(data.getAsJsonArray("palette").get(i).getAsString().substring(1),16);
                var batches=new LinkedHashMap<String,List<Face>>();
                for(var row:data.getAsJsonArray("quads")){
                    var q=row.getAsJsonObject();var n=q.getAsJsonArray("normal");var normal=new float[]{n.get(0).getAsFloat(),n.get(1).getAsFloat(),n.get(2).getAsFloat()};
                    var f=new Face(matrix(q.getAsJsonArray("vertices")),matrix(q.getAsJsonArray("uv")),normal,q.get("role").getAsInt(),q.get("anchor").getAsString(),q.get("name").getAsString().startsWith("sternum_"));
                    batches.computeIfAbsent(q.get("material").getAsString(),k->new ArrayList<>()).add(f);
                }PACKS.put(entry.getKey(),new Pack(palette,batches));
            }
        }catch(Exception e){throw new IllegalStateException("Cannot load backpack geometry",e);}
    }
    public static void render(PoseStack poses,MultiBufferSource buffers,int light,AbstractClientPlayer player,PlayerModel<AbstractClientPlayer> model,ItemStack worn){
        load();String tier=BackpackStorage.capacity(worn)>=84?"rift_backpack":BackpackStorage.capacity(worn)>54?"expedition_backpack":"field_backpack";var pack=PACKS.get(tier);
        var chest=player.getItemBySlot(EquipmentSlot.CHEST);Profile profile=chest.is(Items.ELYTRA)?Profile.ELYTRA:chest.getItem() instanceof RealmArmor.Item armor?(armor.advanced()?Profile.MAGEN:Profile.REALM):chest.getItem() instanceof ArmorItem?Profile.ARMOR:Profile.BARE;
        var prepared=PREPARED.computeIfAbsent(tier,k->new EnumMap<>(Profile.class)).computeIfAbsent(profile,p->prepare(pack,p));
        poses.pushPose();model.body.translateAndRotate(poses);poses.scale(1F/16,1F/16,1F/16);var pose=poses.last();
        var position=new org.joml.Vector3f();var normal=new org.joml.Vector3f();var matrix=pose.pose();
        for(var batch:prepared){
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(batch.texture));
            for(var f:batch.faces){
                int color=f.color,lit=f.glowing?LightTexture.FULL_BRIGHT:light;
                pose.transformNormal(f.normal[0],f.normal[1],f.normal[2],normal);
                for(int i=0;i<4;i++){
                    var p=f.points[i];matrix.transformPosition(p[0],p[1],p[2],position);
                    out.addVertex(position.x,position.y,position.z).setColor((color>>16)&255,(color>>8)&255,color&255,255).setUv(f.uv[i][0],f.uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lit).setNormal(normal.x,normal.y,normal.z);
                }renderedQuads++;
            }
        }poses.popPose();
    }
    private static List<DrawBatch> prepare(Pack pack,Profile profile){
        var result=new ArrayList<DrawBatch>();
        for(var entry:pack.batches.entrySet()){
            var faces=new ArrayList<DrawFace>();
            for(var f:entry.getValue()){
                float[][] points=new float[4][3];
                for(int i=0;i<4;i++){
                    var p=f.points[i];float x=p[0],y=p[1],z=p[2];
                    if(f.anchor.equals("front")){z+=profile.front+2.56F;if(profile.magen&&f.sternum)y+=3.6F;}
                    else if(f.anchor.equals("bridge"))z=profile.front+(z+2.56F)/4.78F*(profile.back-profile.front);
                    else z+=profile.back-2.22F;
                    points[i]=new float[]{x,y,z};
                }
                faces.add(new DrawFace(points,f.uv,f.normal,pack.palette[f.role],f.role==6));
            }
            result.add(new DrawBatch(TEXTURES.get(entry.getKey()),List.copyOf(faces)));
        }
        return List.copyOf(result);
    }
}
