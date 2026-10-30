package pro.erez.interstice.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.erez.interstice.ocean.*;
@Mixin(BoatRenderer.class)
public abstract class ToxinBoatRendererMixin {
    @Inject(method="render(Lnet/minecraft/world/entity/vehicle/Boat;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("HEAD"))
    private void interstice$tilt(Boat boat,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci){pose.pushPose();if(LowerToxinSurface.active(boat.level())&&Math.abs(boat.getY()-LowerToxinSurface.base(boat.level()))<1.5&&LowerToxinSurface.cell(boat.level(),boat.blockPosition().getX(),boat.blockPosition().getZ())){var s=LowerToxinSurface.sample(boat.level(),boat.getX(),boat.getZ(),partial);pose.mulPose(new org.joml.Quaternionf().rotationXYZ((float)Math.atan(s.dz()),0,(float)-Math.atan(s.dx())));}if(boat instanceof OceanBoatState state&&state.interstice$exposure()>160){float remaining=Math.max(.08f,(200-state.interstice$exposure())/40f);pose.translate(0,-.25*(1-remaining),0);pose.scale(1,remaining,1);}}
    @Inject(method="render(Lnet/minecraft/world/entity/vehicle/Boat;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("TAIL"))
    private void interstice$end(Boat boat,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci){pose.popPose();}
}
