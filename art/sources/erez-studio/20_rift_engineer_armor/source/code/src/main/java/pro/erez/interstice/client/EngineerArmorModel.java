package pro.erez.interstice.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Exact E05 authored mesh/UVs; only declared coat panels receive continuous cloth deformation. */
public final class EngineerArmorModel extends HumanoidModel<LivingEntity>{
    private record Face(int[] indices,float[][] uv,Vector3f normal){}
    private static final class Part{
        String name,bone,slot;boolean glow,cloth;float[][] points;List<Face> faces;
        Vector3f[] deformed;
    }
    private record Data(List<Part> parts,String hash,int sourceFaces,int sourceVertices){}
    private static Data DATA;
    private static final Map<LivingEntity,EngineerCloth.State> CLOTH=new WeakHashMap<>();
    private final EquipmentSlot slot;
    private LivingEntity wearer;private float partial;
    private EngineerCloth.State cloth;
    public long renderedVertices;public int renderedParts;
    public static float lastPhysicalPenetration,lastPinnedError;
    public static boolean probeContacts;
    public static long clothFrames;
    private static final Vector3f V=new Vector3f();
    public EngineerArmorModel(EquipmentSlot slot){super(root());this.slot=slot;data();}
    private static ModelPart root(){var bones=new HashMap<String,ModelPart>();for(var n:List.of("head","hat","body","right_arm","left_arm","right_leg","left_leg"))bones.put(n,new ModelPart(List.of(),Map.of()));return new ModelPart(List.of(),bones);}
    public static void reload(){DATA=null;CLOTH.clear();}
    public static String sourceHash(){return data().hash;}
    public static int sourceFaceCount(){return data().sourceFaces;}
    public static int sourcePartCount(){return data().parts.size();}
    private static float[] point(JsonArray a){return new float[]{a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()};}
    private static Vector3f normal(float[] a,float[] b,float[] c){return new Vector3f(b[0]-a[0],b[1]-a[1],b[2]-a[2]).cross(c[0]-a[0],c[1]-a[1],c[2]-a[2]).normalize();}
    private static boolean cloth(String name){return name.contains("_front_skirt")||name.contains("_rear_skirt")||name.contains("_front_hem")||name.contains("_rear_hem")||name.contains("_inner_lining")||name.contains("_side_gusset")||name.contains("_front_fold")||name.contains("_back_fold")||name.contains("waist_bridge");}
    private static Data data(){
        if(DATA!=null)return DATA;
        try(var stream=Minecraft.getInstance().getResourceManager().open(ResourceLocation.fromNamespaceAndPath("interstice","armor/engineer.json"))){
            byte[] bytes=stream.readAllBytes();String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            var json=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();var list=new ArrayList<Part>();int sourceFaces=0,sourceVertices=0;
            float width=json.getAsJsonObject("atlas").get("width").getAsFloat(),height=json.getAsJsonObject("atlas").get("height").getAsFloat();
            for(var value:json.getAsJsonArray("parts")){
                var p=value.getAsJsonObject();var part=new Part();part.name=p.get("name").getAsString();part.bone=p.get("bone").getAsString();part.slot=p.get("slot").getAsString();part.glow=p.get("glow").getAsBoolean();part.cloth=part.bone.endsWith("_leg")&&cloth(part.name);
                var points=new ArrayList<float[]>();for(var v:p.getAsJsonArray("vertices"))points.add(point(v.getAsJsonArray()));sourceVertices+=points.size();
                var faces=new ArrayList<Face>();
                for(var fv:p.getAsJsonArray("faces")){
                    sourceFaces++;var f=fv.getAsJsonObject();var ids=f.getAsJsonArray("indices");var uvs=f.getAsJsonArray("uv");int n=ids.size();if(n!=3&&n!=4)throw new IllegalStateException("Unexpected authored face "+n);
                    int[] indices=new int[4];float[][] uv=new float[4][2];
                    for(int i=0;i<4;i++){int j=Math.min(i,n-1);indices[i]=ids.get(j).getAsInt();uv[i]=new float[]{uvs.get(j).getAsJsonArray().get(0).getAsFloat()/width,uvs.get(j).getAsJsonArray().get(1).getAsFloat()/height};}
                    if(part.cloth){
                        // Affine/bilinear subdivision preserves the exact flat neutral surface and texture mapping.
                        int U=Math.max(1,Math.min(24,(int)Math.ceil(distance(points.get(indices[0]),points.get(indices[1]))/.65))),
                            W=Math.max(1,Math.min(24,(int)Math.ceil(distance(points.get(indices[0]),points.get(indices[3]))/.65)));
                        for(int y=0;y<W;y++)for(int x=0;x<U;x++){
                            int[] sub=new int[4];float[][] suv=new float[4][2];float[][] params={{x/(float)U,y/(float)W},{(x+1F)/U,y/(float)W},{(x+1F)/U,(y+1F)/W},{x/(float)U,(y+1F)/W}};
                            for(int i=0;i<4;i++){sub[i]=points.size();points.add(bilinear(points,indices,params[i][0],params[i][1]));suv[i]=bilerpUV(uv,params[i][0],params[i][1]);}
                            var norm=normal(points.get(sub[0]),points.get(sub[1]),points.get(sub[2]));faces.add(new Face(sub,suv,norm));
                        }
                    }else faces.add(new Face(indices,uv,normal(points.get(indices[0]),points.get(indices[1]),points.get(indices[2]))));
                }
                part.points=points.toArray(float[][]::new);part.faces=List.copyOf(faces);
                if(part.cloth){part.deformed=new Vector3f[part.points.length];for(int i=0;i<part.points.length;i++)part.deformed[i]=new Vector3f();}
                list.add(part);
            }DATA=new Data(List.copyOf(list),hash,sourceFaces,sourceVertices);return DATA;
        }catch(Exception e){throw new IllegalStateException("Cannot load exact engineer armor mesh",e);}
    }
    private static float distance(float[] a,float[] b){return (float)Math.sqrt((a[0]-b[0])*(a[0]-b[0])+(a[1]-b[1])*(a[1]-b[1])+(a[2]-b[2])*(a[2]-b[2]));}
    private static float[] bilinear(List<float[]> p,int[] ids,float u,float v){float[] out=new float[3];for(int i=0;i<4;i++){float w=weight(i,u,v);for(int j=0;j<3;j++)out[j]+=p.get(ids[i])[j]*w;}return out;}
    private static float[] bilerpUV(float[][] uv,float u,float v){float[] out=new float[2];for(int i=0;i<4;i++){float w=weight(i,u,v);out[0]+=uv[i][0]*w;out[1]+=uv[i][1]*w;}return out;}
    private static float weight(int i,float u,float v){return switch(i){case 0->(1-u)*(1-v);case 1->u*(1-v);case 2->u*v;default->(1-u)*v;};}
    private static Matrix4f matrix(ModelPart p){return new Matrix4f().translate(p.x,p.y,p.z).rotateZYX(p.zRot,p.yRot,p.xRot).scale(p.xScale,p.yScale,p.zScale);}
    private ModelPart bone(String name){return switch(name){case "head"->head;case "body"->body;case "right_arm"->rightArm;case "left_arm"->leftArm;case "right_leg"->rightLeg;case "left_leg"->leftLeg;default->throw new IllegalArgumentException(name);};}
    public void prepare(LivingEntity entity,float partialTick){
        wearer=entity;partial=partialTick;
        if(slot!=EquipmentSlot.LEGS)return;
        cloth=CLOTH.computeIfAbsent(entity,key->new EngineerCloth.State());var bodyMatrix=matrix(body);cloth.pose(bodyMatrix,matrix(rightLeg),matrix(leftLeg));
        double dx=entity.getX()-entity.xo,dy=entity.getY()-entity.yo,dz=entity.getZ()-entity.zo;
        if(dx*dx+dy*dy+dz*dz>16)cloth.reset();
        float yaw=(float)Math.toRadians(entity.yBodyRot),co=(float)Math.cos(yaw),si=(float)Math.sin(yaw);
        var motion=new Vector3f((float)(dx*co+dz*si)*16,(float)-dy*16,(float)(dx*si-dz*co)*16);new Matrix4f(bodyMatrix).invert().transformDirection(motion);
        cloth.update(entity.tickCount,motion);clothFrames++;lastPinnedError=cloth.pinnedError();lastPhysicalPenetration=0;
        for(var part:data().parts)if(part.cloth){
            float px=part.bone.equals("right_leg")?-1.9F:1.9F;
            for(int i=0;i<part.points.length;i++){
                var p=part.points[i];cloth.deform(part.name,part.bone.equals("right_leg")?0:1,p[0]+px,p[1]+12,p[2],partial,part.deformed[i]);
                if(probeContacts)lastPhysicalPenetration=Math.max(lastPhysicalPenetration,cloth.penetration(part.deformed[i]));
            }
        }
    }
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer out,int light,int overlay,int color){
        renderedVertices=0;renderedParts=0;
        String kind=switch(slot){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";case FEET->"boots";default->"";};
        for(var part:data().parts){
            if(!part.slot.equals(kind)||!bone(part.bone).visible)continue;renderedParts++;
            pose.pushPose();
            if(young){if(part.bone.equals("head")){pose.scale(.75F,.75F,.75F);pose.translate(0,1,0);}else{pose.scale(.5F,.5F,.5F);pose.translate(0,1.5,0);}}
            if(part.cloth)body.translateAndRotate(pose);else bone(part.bone).translateAndRotate(pose);
            for(var f:part.faces){
                float nx=f.normal.x,ny=f.normal.y,nz=f.normal.z;
                if(part.cloth){Vector3f a=part.deformed[f.indices[0]],b=part.deformed[f.indices[1]],c=part.deformed[f.indices[2]];V.set(b).sub(a).cross(c.x-a.x,c.y-a.y,c.z-a.z);if(V.lengthSquared()>1e-10F)V.normalize();else V.set(f.normal);nx=V.x;ny=V.y;nz=V.z;}
                int lit=part.glow?LightTexture.FULL_BRIGHT:light;
                for(int i=0;i<4;i++){
                    int index=f.indices[i];float x,y,z;
                    if(part.cloth){var p=part.deformed[index];x=p.x;y=p.y;z=p.z;}else{var p=part.points[index];x=p[0];y=p[1];z=p[2];}
                    out.addVertex(pose.last(),x/16,y/16,z/16).setColor(color).setUv(f.uv[i][0],f.uv[i][1]).setOverlay(overlay).setLight(lit).setNormal(pose.last(),nx,ny,nz);renderedVertices++;
                }
            }pose.popPose();
        }
    }
}
