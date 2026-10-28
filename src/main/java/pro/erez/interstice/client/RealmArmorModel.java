package pro.erez.interstice.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import java.io.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import pro.erez.interstice.gear.RealmArmor;

/** Authored JSON geometry, bound to the native six-bone armor pose, including child scaling. */
public final class RealmArmorModel extends HumanoidModel<LivingEntity> {
    private record Box(String bone,String slot,String role,float[] from,float[] size,float[] rotation,float[] pivot){}
    private final List<Box> boxes=new ArrayList<>();
    private final Map<String,Integer> palette=new HashMap<>();
    private EquipmentSlot slot;
    private String pass;
    private final boolean advanced;
    public boolean powered;
    public long renderedVertices;
    public RealmArmorModel(String tier){
        super(root());
        advanced=tier.equals("aegis");
        try(var reader=Minecraft.getInstance().getResourceManager().openAsReader(ResourceLocation.fromNamespaceAndPath("interstice",tier.equals("kotomyaf")?"armor/kotomyaf.json":"armor/armor_design.json"))){
            var data=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("sets").getAsJsonObject(tier);
            data.getAsJsonObject("palette").entrySet().forEach(e->palette.put(e.getKey(),0xff000000|Integer.parseInt(e.getValue().getAsString().substring(1),16)));
            for(var entry:data.getAsJsonArray("cubes")){
                var c=entry.getAsJsonObject();boxes.add(new Box(c.get("bone").getAsString(),c.get("slot").getAsString(),c.get("role").getAsString(),array(c,"from"),array(c,"size"),array(c,"rotation"),array(c,"rotation_pivot")));
            }
        }catch(IOException e){throw new IllegalStateException("Cannot load authored armor geometry",e);}
    }
    private static float[] array(JsonObject o,String key){
        if(!o.has(key))return new float[]{0,0,0};var a=o.getAsJsonArray(key);return new float[]{a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()};
    }
    private static ModelPart root(){
        Map<String,ModelPart> bones=new HashMap<>();
        for(String name:List.of("head","hat","body","right_arm","left_arm","right_leg","left_leg"))bones.put(name,new ModelPart(List.of(),Map.of()));
        return new ModelPart(List.of(),bones);
    }
    public RealmArmorModel slot(EquipmentSlot value){slot=value;pass=null;return this;}
    public int material(int layer){pass=RealmArmor.ROLES.get(layer);return advanced&&!powered&&pass.equals("buckle")?0xff334352:palette.get(pass);}
    public int cubeCount(){return boxes.size();}
    private ModelPart bone(String name){return switch(name){case "head"->head;case "body"->body;case "right_arm"->rightArm;case "left_arm"->leftArm;case "right_leg"->rightLeg;case "left_leg"->leftLeg;default->throw new IllegalArgumentException(name);};}
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer out,int light,int overlay,int color){
        String material=pass;pass=null; // Subsequent native trim/glint passes must cover the whole piece.
        for(var b:boxes){
            if(!b.slot.equals(slot.getName())||material!=null&&!b.role.equals(material))continue;
            var parent=bone(b.bone);if(!parent.visible)continue;
            pose.pushPose();
            if(young){if(b.bone.equals("head")){pose.scale(.75F,.75F,.75F);pose.translate(0,1,0);}else{pose.scale(.5F,.5F,.5F);pose.translate(0,1.5,0);}}
            parent.translateAndRotate(pose);
            pose.translate(b.pivot[0]/16,b.pivot[1]/16,b.pivot[2]/16);
            pose.mulPose(Axis.ZP.rotationDegrees(b.rotation[2]));pose.mulPose(Axis.YP.rotationDegrees(b.rotation[1]));pose.mulPose(Axis.XP.rotationDegrees(b.rotation[0]));
            pose.translate(-b.pivot[0]/16,-b.pivot[1]/16,-b.pivot[2]/16);
            int lit=advanced&&powered&&"buckle".equals(material)?net.minecraft.client.renderer.LightTexture.FULL_BRIGHT:light;
            cube(out,pose.last(),lit,overlay,color,b,material==null);pose.popPose();
        }
    }
    private void cube(VertexConsumer out,PoseStack.Pose pose,int light,int overlay,int color,Box b,boolean auxiliary){
        float x=b.from[0]/16,y=b.from[1]/16,z=b.from[2]/16,X=x+b.size[0]/16,Y=y+b.size[1]/16,Z=z+b.size[2]/16;
        face(out,pose,light,overlay,color,b,auxiliary,new float[][]{{X,y,z},{x,y,z},{x,Y,z},{X,Y,z}},0,0,-1,0);
        face(out,pose,light,overlay,color,b,auxiliary,new float[][]{{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}},0,0,1,1);
        face(out,pose,light,overlay,color,b,auxiliary,new float[][]{{x,y,z},{x,y,Z},{x,Y,Z},{x,Y,z}},-1,0,0,2);
        face(out,pose,light,overlay,color,b,auxiliary,new float[][]{{X,y,Z},{X,y,z},{X,Y,z},{X,Y,Z}},1,0,0,3);
        face(out,pose,light,overlay,color,b,auxiliary,new float[][]{{x,y,z},{X,y,z},{X,y,Z},{x,y,Z}},0,-1,0,4);
        face(out,pose,light,overlay,color,b,auxiliary,new float[][]{{x,Y,Z},{X,Y,Z},{X,Y,z},{x,Y,z}},0,1,0,5);
    }
    private void face(VertexConsumer out,PoseStack.Pose pose,int light,int overlay,int color,Box box,boolean auxiliary,float[][] points,float nx,float ny,float nz,int side){
        for(int i=0;i<4;i++){
            float u=(i==0||i==3)?16:8,v=i<2?0:8;
            if(auxiliary){
                // Project the native trim atlas onto the original bone box; never sample one pixel patch for a trim.
                float[] p=points[i];boolean headBox=box.bone.equals("head"),bodyBox=box.bone.equals("body"),arm=box.bone.endsWith("arm");
                float dx=headBox||bodyBox?8:4,dy=headBox?8:12,dz=headBox?8:4;
                float minX=headBox||bodyBox?-4:arm?(box.bone.equals("right_arm")?-3:-1):-2,minY=headBox?-8:arm?-2:0,minZ=-dz/2;
                float fx=clamp((p[0]*16-minX)/dx),fy=clamp((p[1]*16-minY)/dy),fz=clamp((p[2]*16-minZ)/dz);
                float U=headBox?0:bodyBox?16:arm?40:0,V=headBox?0:16;
                if(side==0){u=U+dz+fx*dx;v=V+dz+fy*dy;}
                else if(side==1){u=U+2*dz+dx+(1-fx)*dx;v=V+dz+fy*dy;}
                else if(side==2){u=U+fz*dz;v=V+dz+fy*dy;}
                else if(side==3){u=U+dz+dx+(1-fz)*dz;v=V+dz+fy*dy;}
                else{u=U+dz+(side==5?dx:0)+fx*dx;v=V+fz*dz;}
            }
            float[] p=points[i];out.addVertex(pose,p[0],p[1],p[2]).setColor(color).setUv(u/64,v/32).setOverlay(overlay).setLight(light).setNormal(pose,nx,ny,nz);renderedVertices++;
        }
    }
    private static float clamp(float v){return Math.clamp(v,0,1);}
}
