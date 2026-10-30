package pro.erez.interstice.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pro.erez.interstice.vehicle.*;
import java.util.*;

/** Only animated bones use a BER; fixed plating stays in the chunk mesh. */
public final class EitanRenderer implements BlockEntityRenderer<EitanController> {
    private static final Map<String,JsonObject> GROUPS=new HashMap<>(), ACTUATORS=new HashMap<>();
    private static final List<String> BONES=new ArrayList<>();
    private static final Map<String,EitanData.Mount> WHEELS=new HashMap<>();
    static{for(var b:EitanData.metadata("bones"))BONES.add(b.getAsJsonObject().get("name").getAsString());for(var a:EitanData.metadata("actuators"))ACTUATORS.put(a.getAsJsonObject().get("id").getAsString(),a.getAsJsonObject());for(var m:EitanData.MOUNTS)WHEELS.put(m.bone(),m);for(var v:EitanData.metadata("groups")){var g=v.getAsJsonObject();GROUPS.put(g.get("id").getAsString(),g);}}
    public EitanRenderer(BlockEntityRendererProvider.Context context){}
    private static float fraction(EitanController c,int channel,float partial){return c.previous[channel]+(c.progress[channel]-c.previous[channel])*partial;}
    private static void hinge(PoseStack stack,String group,float amount){var g=GROUPS.get(group);var p=EitanData.vector(g.getAsJsonArray("pivot"));stack.translate(p[0],p[2],p[1]);float angle=(float)-Math.toRadians(g.get("open_deg").getAsDouble())*amount;stack.mulPose(switch(g.get("axis").getAsString()){case "X"->Axis.XP.rotation(angle);case "Y"->Axis.ZP.rotation(angle);default->Axis.YP.rotation(angle);});stack.translate(-p[0],-p[2],-p[1]);}
    @Override public void render(EitanController c,float partial,PoseStack stack,MultiBufferSource buffers,int light,int overlay){if(EitanCameras.sensorBody(c))return;stack.pushPose();stack.translate(-EitanData.CONTROL.getX(),-EitanData.CONTROL.getY(),-EitanData.CONTROL.getZ());
        for(String name:BONES){stack.pushPose();
            if(name.startsWith("RAMP_ACTUATOR")){String id=name.substring(0,name.lastIndexOf(' '));JsonObject actuator=ACTUATORS.get(id);if(actuator!=null){var f=EitanData.vector(actuator.getAsJsonArray("fixed_m"));var m=EitanData.vector(actuator.getAsJsonArray("moving_closed_m"));var p=EitanData.vector(GROUPS.get("RAMP").getAsJsonArray("pivot"));var moving=new Vector3f((float)(m[0]-p[0]),(float)(m[2]-p[2]),(float)(m[1]-p[1])).rotateX((float)-Math.toRadians(GROUPS.get("RAMP").get("open_deg").getAsDouble())*fraction(c,0,partial)).add((float)p[0],(float)p[2],(float)p[1]);var direction=moving.sub((float)f[0],(float)f[2],(float)f[1]);float distance=direction.length();direction.normalize();stack.translate(f[0],f[2],f[1]);stack.mulPose(new Quaternionf().rotationTo(new Vector3f(0,1,0),direction));if(name.endsWith("tube"))stack.translate(0,.275,0);else{stack.translate(0,.4,0);stack.scale(1,Math.max(.01f,distance-.4f),1);}}}
            else if(name.equals("STEERING_WHEEL")){var p=EitanData.STEERING_PIVOT;var w=c.getLevel().getBlockEntity(c.origin().offset(EitanData.MOUNTS.getFirst().pos()));double angle=w instanceof EitanWheelEntity e?e.yaw(partial)*4:0;stack.translate(p[0],p[1],p[2]);stack.mulPose(Axis.ZP.rotation((float)angle));stack.translate(-p[0],-p[1],-p[2]);}
            else if(name.startsWith("WHEEL_")){String wheel=name.endsWith("_spring")?name.substring(0,name.length()-7):name;var mount=WHEELS.get(wheel);var w=c.wheel(mount.slot());if(w!=null){var e=w;double dy=c.suspensionAnchor-e.getLerpedExtension(partial)-EitanDynamics.RADIUS;if(name.endsWith("_spring")){stack.translate(0,1.18,0);stack.scale(1,(float)Math.max(.35,Math.min(2,1-dy/.7)),1);stack.translate(0,-1.18,0);}else{var hub=mount.hub();stack.translate(hub[0],hub[1]+dy,hub[2]);stack.mulPose(Axis.YP.rotation((float)e.yaw(partial)));stack.mulPose(Axis.XP.rotation(e.getLerpedAngle(partial)));stack.translate(-hub[0],-hub[1],-hub[2]);}}}
            else{int channel=EitanData.CHANNELS.indexOf(name);if(name.equals("RAMP_DOOR"))hinge(stack,"RAMP",fraction(c,0,partial));if(channel>=0)hinge(stack,name,fraction(c,channel,partial));}
            var baked=EitanVehicleModels.bone(name);Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(stack.last(),buffers.getBuffer(RenderType.solid()),null,baked,1,1,1,light,overlay);stack.popPose();
        }
        EitanCameras.displays(c,stack,buffers);stack.popPose();
    }
    @Override public boolean shouldRenderOffScreen(EitanController c){return true;}
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(EitanController c){return new net.minecraft.world.phys.AABB(c.origin()).inflate(7);}
    @Override public int getViewDistance(){return 96;}
}
