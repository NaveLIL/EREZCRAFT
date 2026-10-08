package pro.erez.interstice.agriculture;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public record RetortInput(List<ItemStack> items) implements RecipeInput {
    public RetortInput { if(items.size()!=6)throw new IllegalArgumentException("Retort needs six input slots"); }
    @Override public ItemStack getItem(int index){return items.get(index);}
    @Override public int size(){return 6;}
}
