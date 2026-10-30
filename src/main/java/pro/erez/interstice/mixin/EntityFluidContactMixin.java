package pro.erez.interstice.mixin;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.erez.interstice.FluidContact;
import pro.erez.interstice.Interstice;

@Mixin(Entity.class)
public abstract class EntityFluidContactMixin {
    @Shadow protected Object2DoubleMap<FluidType> forgeFluidTypeHeight;
    @Shadow private FluidType forgeFluidTypeOnEyes;

    @Inject(method = "updateFluidHeightAndDoFluidPushing()V", at = @At("TAIL"))
    private void interstice$correctCeilingContact(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        FluidType light = Interstice.LIGHT_TYPE.get();
        forgeFluidTypeHeight.removeDouble(light);
        if(pro.erez.interstice.ocean.LowerToxinSurface.active(entity.level())&&Math.abs(entity.getY()-pro.erez.interstice.ocean.LowerToxinSurface.base(entity.level()))<1.5){double lower=pro.erez.interstice.ocean.LowerToxinSurface.overlap(entity.level(),entity.getBoundingBox().deflate(.001));forgeFluidTypeHeight.removeDouble(Interstice.HEAVY_TYPE.get());if(lower>0)forgeFluidTypeHeight.put(Interstice.HEAVY_TYPE.get(),lower);}
        if(pro.erez.interstice.ocean.ToxinHullOcclusion.dry(entity.level(),entity.getX(),entity.getY()+.01,entity.getZ()))forgeFluidTypeHeight.removeDouble(Interstice.HEAVY_TYPE.get());
        double depth = FluidContact.lightOverlap(entity.level(), entity.getBoundingBox().deflate(0.001));
        if (depth > 0) forgeFluidTypeHeight.put(light, depth);
    }
    @Inject(method = "updateFluidOnEyes", at = @At("TAIL"))
    private void interstice$correctEyeContact(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if(pro.erez.interstice.ocean.LowerToxinSurface.active(entity.level())&&Math.abs(entity.getEyeY()-pro.erez.interstice.ocean.LowerToxinSurface.base(entity.level()))<1&&pro.erez.interstice.ocean.LowerToxinSurface.cell(entity.level(),entity.blockPosition().getX(),entity.blockPosition().getZ())){if(!pro.erez.interstice.ocean.ToxinHullOcclusion.dry(entity.level(),entity.getX(),entity.getEyeY(),entity.getZ())&&entity.getEyeY()<pro.erez.interstice.ocean.LowerToxinSurface.sample(entity.level(),entity.getX(),entity.getZ(),0).height())forgeFluidTypeOnEyes=Interstice.HEAVY_TYPE.get();else if(forgeFluidTypeOnEyes==Interstice.HEAVY_TYPE.get())forgeFluidTypeOnEyes=NeoForgeMod.EMPTY_TYPE.value();}
        if(forgeFluidTypeOnEyes==Interstice.HEAVY_TYPE.get()&&pro.erez.interstice.ocean.ToxinHullOcclusion.dry(entity.level(),entity.getX(),entity.getEyeY(),entity.getZ()))forgeFluidTypeOnEyes=NeoForgeMod.EMPTY_TYPE.value();
        if (FluidContact.pointInLight(entity.level(), entity.getX(), entity.getEyeY(), entity.getZ())) {
            forgeFluidTypeOnEyes = Interstice.LIGHT_TYPE.get();
        } else if (forgeFluidTypeOnEyes == Interstice.LIGHT_TYPE.get()) {
            forgeFluidTypeOnEyes = NeoForgeMod.EMPTY_TYPE.value();
        }
    }
}
