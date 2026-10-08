package pro.erez.interstice.agriculture;

import net.minecraft.world.entity.LivingEntity;

/** Single future race integration point; no transformation, hidden NBT switch or ambient immunity. */
public final class NativeFoodTolerance {
    public enum FoodKind { ROOT }
    private NativeFoodTolerance(){}
    public static boolean canDigest(LivingEntity consumer,FoodKind food){return false;}
}
