package pro.erez.interstice.equipment;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** A shaped upgrade consumes the base normally while carrying its immutable storage to the result. */
public final class BackpackUpgradeRecipe extends ShapedRecipe {
    public BackpackUpgradeRecipe(ShapedRecipe base){super(base.getGroup(),base.category(),base.pattern,new ItemStack(ExpeditionEquipment.EXPEDITION_BACKPACK.get()),base.showNotification());}
    private static ItemStack source(CraftingInput input){ItemStack found=ItemStack.EMPTY;for(int i=0;i<input.size();i++){var at=input.getItem(i);if(at.is(ExpeditionEquipment.FIELD_BACKPACK.get())){if(!found.isEmpty())return ItemStack.EMPTY;found=at;}}return found;}
    @Override public boolean matches(CraftingInput input,Level level){return super.matches(input,level)&&BackpackStorage.valid(source(input));}
    @Override public ItemStack assemble(CraftingInput input,HolderLookup.Provider lookup){var source=source(input);if(!BackpackStorage.valid(source))return ItemStack.EMPTY;var result=new ItemStack(ExpeditionEquipment.EXPEDITION_BACKPACK.get());result.applyComponents(source.getComponentsPatch());BackpackStorage.renewId(result);return result;}
    @Override public RecipeSerializer<?> getSerializer(){return ExpeditionEquipment.UPGRADE_SERIALIZER.get();}
    public static final class Serializer implements RecipeSerializer<BackpackUpgradeRecipe>{
        private static final MapCodec<BackpackUpgradeRecipe> CODEC=ShapedRecipe.Serializer.CODEC.xmap(BackpackUpgradeRecipe::new,r->r);
        private static final StreamCodec<RegistryFriendlyByteBuf,BackpackUpgradeRecipe> STREAM=ShapedRecipe.Serializer.STREAM_CODEC.map(BackpackUpgradeRecipe::new,r->r);
        public MapCodec<BackpackUpgradeRecipe> codec(){return CODEC;}
        public StreamCodec<RegistryFriendlyByteBuf,BackpackUpgradeRecipe> streamCodec(){return STREAM;}
    }
}
