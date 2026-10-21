package pro.erez.interstice.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.erez.interstice.AbyssalTurfBlock;
import pro.erez.interstice.worldgen.IslandWorld;

/** A weak biological glow does not turn forest terrain into a shader light-source material. */
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper_neoforge", remap=false)
public abstract class DhV6MaterialMixin {
    @Shadow @Final private BlockState blockState;
    @Unique private static byte interstice$grassMaterial = -1;
    @Unique private static byte interstice$leavesMaterial = -1;

    @Inject(method="getMaterialId", at=@At("RETURN"), cancellable=true, remap=false)
    private void interstice$weakForestGlow(CallbackInfoReturnable<Byte> callback) {
        // DH's shared synthetic AIR wrapper deliberately stores a null state.
        // It already has its own material byte; preserve that original return
        // without consulting the client world or classifying any forest block.
        if (blockState == null) return;
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(IslandWorld.TENSION_WORLD)) return;
        var block = blockState.getBlock();
        if (!(block instanceof AbyssalTurfBlock) && !(block instanceof LeavesBlock
                && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("interstice"))) return;
        try {
            if (interstice$grassMaterial < 0) {
                var type = Class.forName("com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial");
                interstice$grassMaterial = type.getField("index").getByte(type.getField("GRASS").get(null));
                interstice$leavesMaterial = type.getField("index").getByte(type.getField("LEAVES").get(null));
            }
            callback.setReturnValue(block instanceof LeavesBlock ? interstice$leavesMaterial : interstice$grassMaterial);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Pinned DH forest material API changed", error);
        }
    }
}
