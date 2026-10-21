package pro.erez.interstice.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.client.V6ShaderBridge;

@Pseudo
@Mixin(targets="net.irisshaders.iris.shaderpack.materialmap.BlockMaterialMapping", remap=false)
public abstract class IrisV6BlockMaterialsMixin {
    @Inject(method="createBlockStateIdMap", at=@At("RETURN"), remap=false)
    private static void interstice$v6Fluids(@Coerce Object blocks, @Coerce Object tags,
            CallbackInfoReturnable<Object2IntMap<BlockState>> callback) {
        if (!V6ShaderBridge.materialsActive()) return;
        var map = callback.getReturnValue();
        put(map, Interstice.LIGHT_BLOCK.get(), V6ShaderBridge.LIGHT_SEA_MATERIAL);
        put(map, Interstice.LIGHT_SEA.get(), V6ShaderBridge.LIGHT_SEA_MATERIAL);
        put(map, Interstice.HEAVY_BLOCK.get(), V6ShaderBridge.HEAVY_SEA_MATERIAL);
    }
    private static void put(Object2IntMap<BlockState> map, Block block, int material) {
        for (var state : block.getStateDefinition().getPossibleStates()) map.put(state, material);
    }
}
