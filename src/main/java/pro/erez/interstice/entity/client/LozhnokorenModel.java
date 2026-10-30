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
import pro.erez.interstice.fauna.Lozhnokoren;

/** Authored cuboids and face UVs remain rigid; only the existing native joints move. */
public final class LozhnokorenModel extends HierarchicalModel<Lozhnokoren> {
    public static final ModelLayerLocation LAYER_LOCATION=new ModelLayerLocation(id("lozhnokoren"),"main");
    private record Bone(String name,String parent,float[] offset,float[] rotation){}
    private record Face(float[] xyz,float[] uv,float nx,float ny,float nz){}
    private record Mesh(List<Bone> bones,Map<String,List<Face>> faces,int cubes,int quads,String hash,String source,JsonArray animations){}
    private final ModelPart root;

    private final Map<String,ModelPart> joints=new HashMap<>();
    private final Map<String,List<String>> children=new HashMap<>();
    private Mesh current;
    private final Map<Lozhnokoren, Transition> transitions=new WeakHashMap<>();
    private static final class Transition {
        Lozhnokoren.Phase phase;
        float since;
        Map<String,PartPose> from=new HashMap<>(),shown=new HashMap<>();
    }
    public int renderedFaces,renderedVertices;
    public long renderedFrames;
    public LozhnokorenModel(ModelPart baked){
        root=baked.getChild("root");
        current=load();
        for(var bone:current.bones){
            var part=bone.parent==null?root:joints.get(bone.parent).getChild(bone.name);
            joints.put(bone.name,part);children.put(bone.name,new ArrayList<>());
            if(bone.parent!=null)children.get(bone.parent).add(bone.name);
        }
    }
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(Interstice.ID,path);}
    private static float[] vector(JsonArray a){float[] out=new float[a.size()];for(int i=0;i<a.size();i++)out[i]=a.get(i).getAsFloat();return out;}
    private static Mesh load(){
        try(var stream=Minecraft.getInstance().getResourceManager().open(id("models/entity/lozhnokoren.json"))){
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
            if(cubes!=357||quads!=2142||bones.size()!=88)throw new IllegalStateException("Incomplete LK01 mesh");
            return new Mesh(List.copyOf(bones),faces,cubes,quads,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),json.get("source_sha256").getAsString(),json.getAsJsonArray("animations"));
        }catch(Exception error){throw new IllegalStateException("Cannot load LK01",error);}
    }
    public static LayerDefinition createBodyLayer(){
        var mesh=new MeshDefinition();var parts=new HashMap<String,PartDefinition>();
        for(var b:load().bones){var p=b.parent==null?mesh.getRoot():parts.get(b.parent);parts.put(b.name,p.addOrReplaceChild(b.name,CubeListBuilder.create(),PartPose.offsetAndRotation(b.offset[0],b.offset[1],b.offset[2],b.rotation[0],b.rotation[1],b.rotation[2])));}
        return LayerDefinition.create(mesh,256,128);
    }
    @Override public ModelPart root(){return root;}
    public ModelPart joint(String name){return joints.get(name);}
    public String sourceHash(){return current.source;}
    public String meshHash(){return current.hash;}
    public int sourceCubes(){return current.cubes;}
    public int sourceQuads(){return current.quads;}
    @Override public void setupAnim(Lozhnokoren entity,float limbSwing,float limbSwingAmount,float age,float yaw,float pitch){
        root.getAllParts().forEach(ModelPart::resetPose);if(entity.isDeadOrDying())return;
        float elapsed=entity.phaseAge(age-entity.tickCount)/20;
        switch(entity.phase()){
            case CAMOUFLAGED -> {} // A resting root is motionless. All original clips remain available.
            case WARNING -> clip("Threat",Math.min(elapsed,.7f),1);
            case STRIKE -> {} // Blend the actually displayed raised pose down to impact, below.
            case RECOVER -> clip("Idle",age/20,.35f);
            default -> {if(limbSwingAmount>.01)clip("Crawl",limbSwing*.2f,Math.min(1,limbSwingAmount*5));else clip("Idle",age/20,1);
                if(entity.phase()==Lozhnokoren.Phase.PURSUIT){
                    var walking=new HashMap<String,PartPose>();joints.forEach((n,p)->walking.put(n,p.storePose()));
                    root.getAllParts().forEach(ModelPart::resetPose);clip("Threat",.7f,1);
                    joints.forEach((n,p)->{if(!n.contains("grasp")&&!n.contains("finger"))p.loadPose(walking.get(n));});
                }
            }
        }
        var state=transitions.computeIfAbsent(entity,key->new Transition());
        if(state.phase!=entity.phase()){
            state.phase=entity.phase();state.since=age;state.from=new HashMap<>(state.shown);
            // A newly tracked attack still starts at the authored raised pose.
            if(state.from.isEmpty()&&state.phase==Lozhnokoren.Phase.STRIKE){
                clip("Threat",.7f,1);joints.forEach((n,p)->state.from.put(n,p.storePose()));
                root.getAllParts().forEach(ModelPart::resetPose);
            }
        }
        float t=net.minecraft.util.Mth.clamp((age-state.since)/8,0,1);t=t*t*(3-2*t);
        for(var entry:joints.entrySet()){
            var p=entry.getValue();var before=state.from.get(entry.getKey());
            if(before!=null){p.x=lerp(before.x,p.x,t);p.y=lerp(before.y,p.y,t);p.z=lerp(before.z,p.z,t);p.xRot=lerp(before.xRot,p.xRot,t);p.yRot=lerp(before.yRot,p.yRot,t);p.zRot=lerp(before.zRot,p.zRot,t);}
            state.shown.put(entry.getKey(),p.storePose());
        }
        float partial=net.minecraft.util.Mth.clamp(age-entity.tickCount,0,1);
        for(int i=1;i<11;i++)joint(String.format(java.util.Locale.ROOT,"segment_%02d",i)).yRot-=Math.toRadians(entity.bodyJointAngle(i,partial));
    }
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    public void clip(String name,float time,float weight){
        for(var item:current.animations){var animation=item.getAsJsonObject();if(!animation.get("name").getAsString().equals(name))continue;
            float length=animation.get("length").getAsFloat();time=animation.get("loop").getAsString().equals("loop")?((time%length)+length)%length:Math.min(length,Math.max(0,time));
            for(var entry:animation.getAsJsonObject("animators").entrySet()){
                var track=entry.getValue().getAsJsonObject();var part=joints.get(track.get("name").getAsString());if(part==null)throw new IllegalStateException("Missing source joint");
                for(String channel:List.of("rotation","position")){
                    JsonObject before=null,after=null;for(var f:track.getAsJsonArray("keyframes")){var key=f.getAsJsonObject();if(!key.get("channel").getAsString().equals(channel))continue;float at=key.get("time").getAsFloat();if(at<=time&&(before==null||at>before.get("time").getAsFloat()))before=key;if(at>=time&&(after==null||at<after.get("time").getAsFloat()))after=key;}
                    if(before==null)before=after;if(after==null)after=before;if(before==null)continue;float a=before.get("time").getAsFloat(),b=after.get("time").getAsFloat(),f=a==b?0:(time-a)/(b-a);float[] out=new float[3];
                    for(int i=0;i<3;i++){String axis="xyz".substring(i,i+1);float v=before.getAsJsonArray("data_points").get(0).getAsJsonObject().get(axis).getAsFloat(),v2=after.getAsJsonArray("data_points").get(0).getAsJsonObject().get(axis).getAsFloat();out[i]=(v+(v2-v)*f)*weight;}
                    if(channel.equals("rotation")){part.xRot-=Math.toRadians(out[0]);part.yRot+=Math.toRadians(out[1]);part.zRot-=Math.toRadians(out[2]);}else{part.x+=out[0];part.y-=out[1];part.z+=out[2];}
                }
            }
        }
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
