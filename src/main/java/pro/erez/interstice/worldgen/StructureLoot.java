package pro.erez.interstice.worldgen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Optional upstream Lootr containers; no copied inventory, networking or persistence implementation. */
public final class StructureLoot {
    private static final ResourceLocation LOOTR_BARREL = ResourceLocation.fromNamespaceAndPath("lootr", "lootr_barrel");
    private StructureLoot() {}
    public static BlockState personal(BlockState state) {
        if (!state.is(Blocks.BARREL)) return state;
        return BuiltInRegistries.BLOCK.getOptional(LOOTR_BARREL).map(block -> block.withPropertiesOf(state)).orElse(state);
    }
    public static CompoundTag personalNbt(BlockState state, CompoundTag data) {
        if (data == null) return null;
        var result = data.copy();
        if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(LOOTR_BARREL)) result.putString("id", LOOTR_BARREL.toString());
        return result;
    }
    public static boolean matches(BlockState expected, BlockState actual) {
        return actual.is(expected.getBlock()) || expected.is(Blocks.BARREL)
                && BuiltInRegistries.BLOCK.getKey(actual.getBlock()).equals(LOOTR_BARREL);
    }
}
