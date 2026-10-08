package pro.erez.interstice.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;
import pro.erez.interstice.Interstice;

/** Opt luminous flora into vertex lighting. The renderer still respects the owner's AO option. */
@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class FloraLighting {
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        for (Block block : new Block[]{Interstice.ABYSSAL_TURF.get(), Interstice.TIDE_SPROUT.get(), Interstice.GLOOMCROWN_LEAVES.get()}) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state), (key, model) -> new SmoothLuminousModel(model));
            }
        }
    }
    private static final class SmoothLuminousModel extends BakedModelWrapper<BakedModel> {
        SmoothLuminousModel(BakedModel original) { super(original); }
        @Override public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType type) {
            TriState original = originalModel.useAmbientOcclusion(state, data, type);
            return original == TriState.FALSE ? TriState.FALSE : TriState.TRUE;
        }
    }
}
