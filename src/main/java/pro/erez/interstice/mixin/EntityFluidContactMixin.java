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
        double depth = FluidContact.lightOverlap(entity.level(), entity.getBoundingBox().deflate(0.001));
        if (depth > 0) forgeFluidTypeHeight.put(light, depth);
    }
    @Inject(method = "updateFluidOnEyes", at = @At("TAIL"))
    private void interstice$correctEyeContact(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (FluidContact.pointInLight(entity.level(), entity.getX(), entity.getEyeY(), entity.getZ())) {
            forgeFluidTypeOnEyes = Interstice.LIGHT_TYPE.get();
        } else if (forgeFluidTypeOnEyes == Interstice.LIGHT_TYPE.get()) {
            forgeFluidTypeOnEyes = NeoForgeMod.EMPTY_TYPE.value();
        }
    }
}
