package pro.erez.interstice.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.tether.*;

/** World-space slim rope geometry references the installed vanilla lead tile, never distributes a copied PNG. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class TetherRenderer {
    private static final RenderType TYPE=RenderType.entityCutoutNoCull(ResourceLocation.withDefaultNamespace("textures/entity/lead_knot.png"));
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){ClientTetherState.clear();}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;var mc=Minecraft.getInstance();if(mc.level==null)return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);var buffer=mc.renderBuffers().bufferSource();var vertex=buffer.getBuffer(TYPE);
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);boolean drew=false;
        for(var link:ClientTetherState.links()){
            if(!link.dimension().equals(mc.level.dimension().location())||!mc.level.hasChunkAt(link.anchor()))continue;
            var target=mc.level.getEntity(link.entityId());if(target==null||!target.getUUID().equals(link.player())||!mc.level.getBlockState(link.anchor()).is(RiftTethers.WINCH.get()))continue;
            var start=WinchLinks.source(link.anchor(),Direction.from3DDataValue(link.facing()));var end=target.getPosition(partial).add(0,target.getBbHeight()*.55,0);var difference=end.subtract(start);double distance=difference.length();if(distance<.01||distance>48)continue;
            var side=difference.normalize().cross(new Vec3(0,1,0));if(side.lengthSqr()<.01)side=new Vec3(1,0,0);else side=side.normalize();var other=difference.normalize().cross(side).normalize();
            double sag=Math.min(.7,Math.max(0,link.length()-distance)*.08);int light=LightTexture.pack(mc.level.getBrightness(LightLayer.BLOCK,link.anchor()),mc.level.getBrightness(LightLayer.SKY,link.anchor()));
            for(int i=0;i<24;i++){double a=i/24.0,b=(i+1)/24.0;var from=start.lerp(end,a).add(0,-4*sag*a*(1-a),0);var to=start.lerp(end,b).add(0,-4*sag*b*(1-b),0);
                quad(vertex,pose.last(),from,to,side.scale(.025),light);quad(vertex,pose.last(),from,to,other.scale(.025),light);
            }drew=true;
        }pose.popPose();if(drew)buffer.endBatch(TYPE);
    }
    private static void quad(VertexConsumer v,PoseStack.Pose pose,Vec3 from,Vec3 to,Vec3 side,int light){
        point(v,pose,from.subtract(side),0,0,light);point(v,pose,from.add(side),1,0,light);point(v,pose,to.add(side),1,1,light);point(v,pose,to.subtract(side),0,1,light);
    }
    private static void point(VertexConsumer v,PoseStack.Pose pose,Vec3 p,float u,float w,int light){v.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor(175,162,134,255).setUv(u,w).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,1,0);}
    private TetherRenderer(){}
}
