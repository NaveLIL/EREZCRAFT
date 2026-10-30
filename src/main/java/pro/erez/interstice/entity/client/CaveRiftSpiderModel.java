package pro.erez.interstice.entity.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.CaveRiftSpiderEntity;

/** Authored cuboids and face UVs remain rigid; only the existing native joints move. */
public final class CaveRiftSpiderModel extends HierarchicalModel<CaveRiftSpiderEntity> {
    public static final ModelLayerLocation LAYER_LOCATION=new ModelLayerLocation(id("cave_rift_spider"),"main");
    private record Bone(String name,String parent,float[] offset,float[] rotation){}
    private record Face(float[] xyz,float[] uv,float nx,float ny,float nz){}
    private record Mesh(List<Bone> bones,Map<String,List<Face>> faces,int cubes,int quads,String hash,String source){}
    private final ModelPart root;
    private final Map<CaveRiftSpiderEntity.Variant,Mesh> meshes=new EnumMap<>(CaveRiftSpiderEntity.Variant.class);
    private final Map<String,ModelPart> joints=new HashMap<>();
    private final Map<String,List<String>> children=new HashMap<>();
    private Mesh current;
    public int renderedFaces,renderedVertices;
    public long renderedFrames;
    public CaveRiftSpiderModel(ModelPart baked){
        root=baked.getChild("root");
        for(var variant:CaveRiftSpiderEntity.Variant.values())meshes.put(variant,load(variant));
        current=meshes.get(CaveRiftSpiderEntity.Variant.LURKER);
        for(var bone:current.bones){
            var part=bone.parent==null?root:joints.get(bone.parent).getChild(bone.name);
            joints.put(bone.name,part);children.put(bone.name,new ArrayList<>());
            if(bone.parent!=null)children.get(bone.parent).add(bone.name);
        }
    }
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(Interstice.ID,path);}
    private static float[] vector(JsonArray a){float[] out=new float[a.size()];for(int i=0;i<a.size();i++)out[i]=a.get(i).getAsFloat();return out;}
    private static Mesh load(CaveRiftSpiderEntity.Variant variant){
        try(var stream=Minecraft.getInstance().getResourceManager().open(id("models/entity/spider/"+variant.name().toLowerCase(Locale.ROOT)+".json"))){
            var bytes=stream.readAllBytes();var json=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            var bones=new ArrayList<Bone>();for(var value:json.getAsJsonArray("bones")){var b=value.getAsJsonObject();bones.add(new Bone(b.get("name").getAsString(),b.get("parent").isJsonNull()?null:b.get("parent").getAsString(),vector(b.getAsJsonArray("offset")),vector(b.getAsJsonArray("rotation"))));}
            var atlas=json.getAsJsonObject("atlas");float width=atlas.get("width").getAsFloat(),height=atlas.get("height").getAsFloat();
            var faces=new HashMap<String,List<Face>>();int cubes=0,quads=0;
            for(var value:json.getAsJsonArray("parts")){
                var part=value.getAsJsonObject();var positions=part.getAsJsonArray("vertices");var list=faces.computeIfAbsent(part.get("bone").getAsString(),key->new ArrayList<>());cubes++;
                for(var faceValue:part.getAsJsonArray("faces")){
                    var face=faceValue.getAsJsonObject();var ids=face.getAsJsonArray("indices");var uvs=face.getAsJsonArray("uv");float[] xyz=new float[12],uv=new float[8];
                    for(int i=0;i<4;i++){var point=positions.get(ids.get(i).getAsInt()).getAsJsonArray();for(int k=0;k<3;k++)xyz[i*3+k]=point.get(k).getAsFloat()/16;uv[i*2]=uvs.get(i).getAsJsonArray().get(0).getAsFloat()/width;uv[i*2+1]=uvs.get(i).getAsJsonArray().get(1).getAsFloat()/height;}
                    var normal=new Vector3f(xyz[3]-xyz[0],xyz[4]-xyz[1],xyz[5]-xyz[2]).cross(xyz[6]-xyz[0],xyz[7]-xyz[1],xyz[8]-xyz[2]).normalize();
                    list.add(new Face(xyz,uv,normal.x,normal.y,normal.z));quads++;
                }
            }
            if(cubes!=309||quads!=1854||bones.size()!=53)throw new IllegalStateException("Incomplete selected spider mesh: "+variant);
            return new Mesh(List.copyOf(bones),faces,cubes,quads,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),json.get("source_sha256").getAsString());
        }catch(Exception error){throw new IllegalStateException("Cannot load authored spider "+variant,error);}
    }
    public static LayerDefinition createBodyLayer(){
        var mesh=new MeshDefinition();var parts=new HashMap<String,PartDefinition>();
        for(var b:load(CaveRiftSpiderEntity.Variant.LURKER).bones){var p=b.parent==null?mesh.getRoot():parts.get(b.parent);parts.put(b.name,p.addOrReplaceChild(b.name,CubeListBuilder.create(),PartPose.offsetAndRotation(b.offset[0],b.offset[1],b.offset[2],b.rotation[0],b.rotation[1],b.rotation[2])));}
        return LayerDefinition.create(mesh,512,768);
    }
    @Override public ModelPart root(){return root;}
    public ModelPart joint(String name){return joints.get(name);}
    public String sourceHash(CaveRiftSpiderEntity.Variant variant){return meshes.get(variant).source;}
    public String meshHash(CaveRiftSpiderEntity.Variant variant){return meshes.get(variant).hash;}
    public int sourceCubes(){return current.cubes;}
    public int sourceQuads(){return current.quads;}
    @Override public void setupAnim(CaveRiftSpiderEntity entity,float limbSwing,float limbSwingAmount,float age,float yaw,float pitch){
        current=meshes.get(entity.getVariant());root.getAllParts().forEach(ModelPart::resetPose);
        // Rest is the authored still pose. A tarantula does not pump its abdomen or fidget continuously.
        // Native limbSwing includes vertical travel on walls/ceilings (entity calculateEntityAnimation).
        if(!entity.isDeadOrDying()&&limbSwingAmount>.015F)animateWalk(CaveRiftSpiderAnimation.MOVE,limbSwing,limbSwingAmount,2.5F,2.5F);
    }
    private void render(String name,PoseStack pose,VertexConsumer out,int light,int overlay,int color){
        var part=joints.get(name);if(!part.visible)return;pose.pushPose();part.translateAndRotate(pose);
        if(!part.skipDraw)for(var face:current.faces.getOrDefault(name,List.of())){
            for(int i=0;i<4;i++)out.addVertex(pose.last(),face.xyz[i*3],face.xyz[i*3+1],face.xyz[i*3+2]).setColor(color).setUv(face.uv[i*2],face.uv[i*2+1]).setOverlay(overlay).setLight(light).setNormal(pose.last(),face.nx,face.ny,face.nz);
            renderedFaces++;renderedVertices+=4;
        }
        for(var child:children.get(name))render(child,pose,out,light,overlay,color);pose.popPose();
    }
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer out,int light,int overlay,int color){renderedFaces=renderedVertices=0;render("root",pose,out,light,overlay,color);renderedFrames++;}
}
