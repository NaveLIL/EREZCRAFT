package pro.erez.interstice.gear;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Refill exactly one spent physical module; native shapeless crafting consumes all reagents. */
public final class EngineerRefillRecipe extends ShapelessRecipe {
    EngineerRefillRecipe(ShapelessRecipe base){super(base.getGroup(),base.category(),base.getResultItem(null),base.getIngredients());}
    private static ItemStack source(CraftingInput input){
        ItemStack found=ItemStack.EMPTY;
        for(int i=0;i<input.size();i++){var at=input.getItem(i);if(!EngineerModules.cell(at)&&!EngineerModules.cassette(at))continue;if(!found.isEmpty())return ItemStack.EMPTY;found=at;}
        return found;
    }
    private boolean depleted(ItemStack stack){return !stack.isEmpty()&&stack.is(getResultItem(null).getItem())&&(EngineerModules.cell(stack)?EngineerModules.energy(stack)==0:EngineerModules.reserve(stack)==0);}
    @Override public boolean matches(CraftingInput input,Level level){return super.matches(input,level)&&depleted(source(input));}
    @Override public ItemStack assemble(CraftingInput input,HolderLookup.Provider lookup){
        var source=source(input);if(!depleted(source))return ItemStack.EMPTY;var result=source.copyWithCount(1);
        if(EngineerModules.cell(result))EngineerModules.energy(result,EngineerModules.CAPACITY);else EngineerModules.reserve(result,EngineerModules.AIR_TICKS);
        return result;
    }
    @Override public RecipeSerializer<?> getSerializer(){return EngineerArmor.REFILL.get();}
    public static final class Serializer implements RecipeSerializer<EngineerRefillRecipe>{
        private static final MapCodec<EngineerRefillRecipe> CODEC=new ShapelessRecipe.Serializer().codec().xmap(EngineerRefillRecipe::new,r->r);
        private static final StreamCodec<RegistryFriendlyByteBuf,EngineerRefillRecipe> STREAM=ShapelessRecipe.Serializer.STREAM_CODEC.map(EngineerRefillRecipe::new,r->r);
        public MapCodec<EngineerRefillRecipe> codec(){return CODEC;}
        public StreamCodec<RegistryFriendlyByteBuf,EngineerRefillRecipe> streamCodec(){return STREAM;}
    }
}
