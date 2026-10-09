package pro.erez.interstice.equipment;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** A shaped upgrade consumes the base normally while carrying its storage and modules to the result. */
public final class BackpackUpgradeRecipe extends ShapedRecipe {
    public BackpackUpgradeRecipe(ShapedRecipe base) {
        super(base.getGroup(), base.category(), base.pattern, base.getResultItem(null), base.showNotification());
    }

    private static ItemStack source(CraftingInput input) {
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            var at = input.getItem(i);
            if (BackpackStorage.isPack(at)) {
                if (!found.isEmpty()) return ItemStack.EMPTY;
                found = at;
            }
        }
        return found;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return super.matches(input, level) && BackpackStorage.valid(source(input));
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
        var source = source(input);
        if (!BackpackStorage.valid(source)) return ItemStack.EMPTY;
        var result = getResultItem(lookup).copy();
        result.applyComponents(source.getComponentsPatch());
        BackpackStorage.renewId(result);
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ExpeditionEquipment.UPGRADE_SERIALIZER.get();
    }

    public static final class Serializer implements RecipeSerializer<BackpackUpgradeRecipe> {
        private static final MapCodec<BackpackUpgradeRecipe> CODEC = ShapedRecipe.Serializer.CODEC.xmap(BackpackUpgradeRecipe::new, r -> r);
        private static final StreamCodec<RegistryFriendlyByteBuf, BackpackUpgradeRecipe> STREAM = ShapedRecipe.Serializer.STREAM_CODEC.map(BackpackUpgradeRecipe::new, r -> r);

        @Override public MapCodec<BackpackUpgradeRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, BackpackUpgradeRecipe> streamCodec() { return STREAM; }
    }
}
