package pro.erez.interstice;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

public final class ToxicFluidType extends FluidType {
    private final boolean light;
    public ToxicFluidType(boolean light) {
        super(Properties.create().density(light ? -800 : 2400).viscosity(light ? 700 : 2400)
                .lightLevel(light ? 15 : 0).canSwim(true).canDrown(false)
                .canConvertToSource(false).canPushEntity(!light).supportsBoating(false));
        this.light = light;
    }
    @Override
    public boolean move(FluidState state, LivingEntity entity, Vec3 input, double gravity) {
        if (!light) return false; // NeoForge's ordinary water movement for heavy liquid.
        entity.moveRelative(0.02F, input);
        entity.move(MoverType.SELF, entity.getDeltaMovement());
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.8, 0.8, 0.8).add(0, 0.025, 0));
        entity.fallDistance = 0;
        return true;
    }
}
