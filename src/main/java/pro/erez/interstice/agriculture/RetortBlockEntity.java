package pro.erez.interstice.agriculture;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import pro.erez.interstice.minerals.MineralEcology;

/** Six inputs, one fuel, three outputs. A saved batch owns its ingredients and output snapshot. */
public final class RetortBlockEntity extends BlockEntity implements WorldlyContainer,MenuProvider {
    private NonNullList<ItemStack> items=NonNullList.withSize(10,ItemStack.EMPTY);
    private NonNullList<ItemStack> reserved=NonNullList.withSize(6,ItemStack.EMPTY),pending=NonNullList.withSize(3,ItemStack.EMPTY);
    private int progress,total,heat,status;
    private boolean batch;
    private ResourceLocation selectedRecipe;
    public final ContainerData data=new ContainerData(){
        @Override public int get(int i){return switch(i){case 0->progress;case 1->total;case 2->heat;case 3->status;default->0;};}
        @Override public void set(int i,int value){switch(i){case 0->progress=value;case 1->total=value;case 2->heat=value;case 3->status=value;}}
        @Override public int getCount(){return 4;}
    };
    public RetortBlockEntity(BlockPos pos,BlockState state){super(RealmAgriculture.RETORT_ENTITY.get(),pos,state);}
    public List<ItemStack> preview(){
        if(batch)return pending.stream().map(ItemStack::copy).toList();
        if(selectedRecipe!=null){var selected=selected();return selected==null?List.of():selected.outputs().stream().map(ItemStack::copy).toList();}
        var candidate=candidate();return candidate==null?List.of():candidate.outputs().stream().map(ItemStack::copy).toList();
    }
    public boolean hasBatch(){return batch;}
    public ResourceLocation selectedRecipe(){return selectedRecipe;}
    public boolean selectRecipe(ResourceLocation id){
        if(level==null||level.isClientSide||batch)return false;
        if(id!=null&&recipe(id)==null)return false;
        selectedRecipe=id;setChanged();return true;
    }
    public boolean canFitOutputs(List<ItemStack> output){return merged(output)!=null;}
    private RetortRecipe recipe(ResourceLocation id){
        if(level==null)return null;
        return level.getRecipeManager().byKey(id).filter(r->r.value().getType()==RealmAgriculture.RETORT_RECIPE_TYPE.get())
                .map(r->(RetortRecipe)r.value()).orElse(null);
    }
    private RetortRecipe selected(){return selectedRecipe==null?null:recipe(selectedRecipe);}
    private RetortRecipe candidate(){
        if(level==null||level.isClientSide)return null;
        var input=input();
        if(selectedRecipe!=null){var selected=selected();return selected!=null&&selected.matches(input,level)?selected:null;}
        return level.getRecipeManager().getAllRecipesFor(RealmAgriculture.RETORT_RECIPE_TYPE.get()).stream()
                .sorted(Comparator.comparing(r->r.id().toString())).map(r->r.value()).filter(r->r.matches(input,level)).findFirst().orElse(null);
    }
    private RetortInput input(){return new RetortInput(java.util.stream.IntStream.range(0,6).mapToObj(i->items.get(i).copy()).toList());}
    private NonNullList<ItemStack> merged(List<ItemStack> outputs){
        return mergeOutputs(outputs,java.util.stream.IntStream.range(7,10).mapToObj(items::get).toList());
    }
    public static NonNullList<ItemStack> mergeOutputs(List<ItemStack> outputs,List<ItemStack> current){
        if(current.size()!=3)return null;
        var slots=NonNullList.withSize(3,ItemStack.EMPTY);for(int i=0;i<3;i++)slots.set(i,current.get(i).copy());
        for(var original:outputs){var stack=original.copy();
            for(int i=0;i<3&&!stack.isEmpty();i++){var at=slots.get(i);if(at.isEmpty()||!ItemStack.isSameItemSameComponents(at,stack))continue;
                int count=Math.min(stack.getCount(),at.getMaxStackSize()-at.getCount());at.grow(count);stack.shrink(count);}
            for(int i=0;i<3&&!stack.isEmpty();i++)if(slots.get(i).isEmpty()){int count=Math.min(stack.getCount(),stack.getMaxStackSize());slots.set(i,stack.copyWithCount(count));stack.shrink(count);}
            if(!stack.isEmpty())return null;
        }return slots;
    }
    public void process(){
        if(level==null||level.isClientSide)return;
        status=0;
        if(!batch){var recipe=candidate();if(recipe==null&&selectedRecipe!=null)status=selected()==null?5:4;if(recipe!=null){
            if(merged(recipe.outputs())==null){status=3;return;}
            if(heat==0&&!items.get(6).is(MineralEcology.UMBRAL_COAL.get())){status=2;return;}
            int[] take=recipe.allocation(input());for(int i=0;i<6;i++)reserved.set(i,items.get(i).split(take[i]));
            for(int i=0;i<3;i++)pending.set(i,i<recipe.outputs().size()?recipe.outputs().get(i).copy():ItemStack.EMPTY);
            total=recipe.ticks();progress=0;batch=true;setChanged();
        }}
        if(batch){
            if(progress<total){
                if(heat==0){if(!items.get(6).is(MineralEcology.UMBRAL_COAL.get())){status=2;setLit(false);return;}items.get(6).shrink(1);heat=3200;setChanged();}
                heat--;progress++;status=1;setLit(true);
            }
            if(progress>=total){var output=merged(pending);if(output==null){status=3;setLit(false);return;}
                for(int i=0;i<3;i++)items.set(i+7,output.get(i));clearBatch();setChanged();status=0;setLit(false);
            }
        }else setLit(false);
        if(level.getGameTime()%20==0)setChanged();
    }
    private void setLit(boolean lit){var state=getBlockState();if(state.getValue(RetortBlock.LIT)!=lit)level.setBlock(worldPosition,state.setValue(RetortBlock.LIT,lit),3);}
    private void clearBatch(){batch=false;progress=total=0;reserved=NonNullList.withSize(6,ItemStack.EMPTY);pending=NonNullList.withSize(3,ItemStack.EMPTY);}
    public void release(){
        if(level==null||level.isClientSide)return;
        var drops=new ArrayList<ItemStack>();for(int i=0;i<10;i++){var stack=items.set(i,ItemStack.EMPTY);if(!stack.isEmpty())drops.add(stack);}
        if(batch)drops.addAll((progress>=total?pending:reserved).stream().filter(s->!s.isEmpty()).map(ItemStack::copy).toList());
        clearBatch();setChanged();for(var stack:drops)Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,stack);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);ContainerHelper.saveAllItems(tag,items,lookup);
        var r=new CompoundTag();ContainerHelper.saveAllItems(r,reserved,lookup);tag.put("Reserved",r);var p=new CompoundTag();ContainerHelper.saveAllItems(p,pending,lookup);tag.put("Pending",p);
        tag.putBoolean("Batch",batch);tag.putInt("Progress",progress);tag.putInt("Total",total);tag.putInt("Heat",heat);
        if(selectedRecipe!=null)tag.putString("SelectedRecipe",selectedRecipe.toString());
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);
        items=NonNullList.withSize(10,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items,lookup);reserved=NonNullList.withSize(6,ItemStack.EMPTY);pending=NonNullList.withSize(3,ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound("Reserved"),reserved,lookup);ContainerHelper.loadAllItems(tag.getCompound("Pending"),pending,lookup);
        batch=tag.getBoolean("Batch");total=Math.max(0,Math.min(32000,tag.getInt("Total")));progress=Math.max(0,Math.min(total,tag.getInt("Progress")));heat=Math.max(0,Math.min(3200,tag.getInt("Heat")));
        String storedSelection=tag.getString("SelectedRecipe");
        selectedRecipe=storedSelection.isBlank()?null:ResourceLocation.tryParse(storedSelection);
    }
    @Override public int getContainerSize(){return 10;}
    @Override public boolean isEmpty(){return !batch&&items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return items.get(slot);}
    @Override public ItemStack removeItem(int slot,int count){var result=ContainerHelper.removeItem(items,slot,count);if(!result.isEmpty())setChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){var result=ContainerHelper.takeItem(items,slot);setChanged();return result;}
    @Override public void setItem(int slot,ItemStack stack){items.set(slot,stack.copyWithCount(Math.min(stack.getCount(),stack.getMaxStackSize())));setChanged();}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot<6||slot==6&&stack.is(MineralEcology.UMBRAL_COAL.get());}
    @Override public boolean stillValid(Player player){return Container.stillValidBlockEntity(this,player);}
    @Override public void clearContent(){items.clear();clearBatch();setChanged();}
    @Override public int[] getSlotsForFace(Direction direction){return direction==Direction.DOWN?new int[]{7,8,9}:direction==Direction.UP?new int[]{0,1,2,3,4,5}:new int[]{6};}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction direction){return canPlaceItem(slot,stack);}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction direction){return slot>=7;}
    @Override public Component getDisplayName(){return Component.translatable("block.interstice.reaction_retort");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return new RetortMenu(id,inventory,this,data,ContainerLevelAccess.create(level,worldPosition));}
}
