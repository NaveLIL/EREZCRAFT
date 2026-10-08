package pro.erez.interstice.agriculture;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import pro.erez.interstice.minerals.MineralEcology;

public final class RetortMenu extends AbstractContainerMenu {
    public static final int BUTTON_AUTO=0,BUTTON_FILL=1,FILL_BASE=0x10000;
    public static final int FEEDBACK_NONE=0,FEEDBACK_BUSY=1,FEEDBACK_MISSING=2,FEEDBACK_INPUT_SPACE=3,
            FEEDBACK_OUTPUT_SPACE=4,FEEDBACK_FILLED=5,FEEDBACK_INVALID=6,FEEDBACK_ALREADY_READY=7;
    public final ContainerData data;
    private final Container inventory;
    private final ContainerLevelAccess access;
    private final Inventory playerInventory;
    private final SimpleContainer preview=new SimpleContainer(3);
    private int feedback;
    private long feedbackUntil;

    public RetortMenu(int id,Inventory player,RegistryFriendlyByteBuf buffer){
        this(id,player,new SimpleContainer(10),new SimpleContainerData(6),ContainerLevelAccess.NULL);buffer.readBlockPos();
    }
    public RetortMenu(int id,Inventory player,Container inventory,ContainerData originalData,ContainerLevelAccess access){
        super(RealmAgriculture.RETORT_MENU.get(),id);this.inventory=inventory;this.access=access;this.playerInventory=player;
        checkContainerSize(inventory,10);checkContainerDataCount(originalData,4);
        this.data=inventory instanceof RetortBlockEntity entity?new ContainerData(){
            @Override public int get(int i){return i<4?originalData.get(i):i==4?recipeToken(entity.selectedRecipe()):i==5&&player.player.level().getGameTime()<feedbackUntil?feedback:0;}
            @Override public void set(int i,int value){if(i<4)originalData.set(i,value);}
            @Override public int getCount(){return 6;}
        }:originalData;
        for(int i=0;i<6;i++)addSlot(new Slot(inventory,i,32+(i%3)*18,38+(i/3)*18));
        addSlot(new Slot(inventory,6,50,82){@Override public boolean mayPlace(ItemStack stack){return stack.is(MineralEcology.UMBRAL_COAL.get());}});
        for(int i=0;i<3;i++)addSlot(new Slot(inventory,7+i,133+i*18,74){@Override public boolean mayPlace(ItemStack stack){return false;}});
        for(int i=0;i<3;i++)addSlot(new Slot(preview,i,133+i*18,38){@Override public boolean mayPlace(ItemStack stack){return false;}@Override public boolean mayPickup(Player p){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,col+row*9+9,28+col*18,132+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,28+col*18,190));
        addDataSlots(this.data);broadcastChanges();
    }
    /** Fits vanilla signed-short menu data too; collisions are rejected rather than guessed. */
    public static int recipeToken(ResourceLocation id){return id==null?0:256+(id.toString().hashCode()&0x3fff);}
    public static int fillButton(ResourceLocation id){return FILL_BASE+recipeToken(id);}
    public static List<RecipeHolder<RetortRecipe>> recipes(Level level){
        return level.getRecipeManager().getAllRecipesFor(RealmAgriculture.RETORT_RECIPE_TYPE.get()).stream().sorted(Comparator.comparing(r->r.id().toString())).toList();
    }
    public static RecipeHolder<RetortRecipe> recipeForToken(Level level,int token){
        RecipeHolder<RetortRecipe> found=null;
        for(var recipe:recipes(level))if(recipeToken(recipe.id())==token){if(found!=null)return null;found=recipe;}
        return found;
    }
    public boolean batchActive(){return data.get(1)>0;}
    public RetortInput currentInputs(){return new RetortInput(java.util.stream.IntStream.range(0,6).mapToObj(i->inventory.getItem(i).copy()).toList());}
    public boolean outputsFit(RetortRecipe recipe){return RetortBlockEntity.mergeOutputs(recipe.outputs(),java.util.stream.IntStream.range(7,10).mapToObj(inventory::getItem).toList())!=null;}
    @Override public void broadcastChanges(){
        if(inventory instanceof RetortBlockEntity entity){var outputs=entity.preview();for(int i=0;i<3;i++)preview.setItem(i,i<outputs.size()?outputs.get(i).copy():ItemStack.EMPTY);}
        super.broadcastChanges();
    }
    @Override public boolean stillValid(Player player){return stillValid(access,player,RealmAgriculture.RETORT.get());}
    private void feedback(Player player,int code){feedback=code;feedbackUntil=player.level().getGameTime()+80;broadcastChanges();}
    @Override public boolean clickMenuButton(Player player,int button){
        if(player.level().isClientSide||player.isSpectator()||playerInventory.player!=player||player.containerMenu!=this||!stillValid(player)
                ||!(inventory instanceof RetortBlockEntity entity)||entity.getLevel()!=player.level())return false;
        if(entity.hasBatch()){feedback(player,FEEDBACK_BUSY);return false;}
        if(button==BUTTON_AUTO){boolean selected=entity.selectRecipe(null);feedback(player,FEEDBACK_NONE);return selected;}
        boolean fill=button==BUTTON_FILL||button>=FILL_BASE;
        int token=button==BUTTON_FILL?recipeToken(entity.selectedRecipe()):fill?button-FILL_BASE:button;
        var recipe=recipeForToken(player.level(),token);
        if(recipe==null){feedback(player,FEEDBACK_INVALID);return false;}
        if(!fill){boolean selected=entity.selectRecipe(recipe.id());feedback(player,FEEDBACK_NONE);return selected;}
        var plan=fillPlan(recipe.value(),currentInputs().items(),playerInventory.items);
        if(plan.reason()!=FEEDBACK_FILLED){feedback(player,plan.reason());return false;}
        if(!entity.canFitOutputs(recipe.value().outputs())){feedback(player,FEEDBACK_OUTPUT_SPACE);return false;}
        // One server task owns the complete commit. No cursor, armor, offhand or ghost slot participates.
        for(int i=0;i<6;i++)entity.setItem(i,plan.inputs().get(i));
        for(int i=0;i<36;i++)playerInventory.items.set(i,plan.playerItems().get(i));
        playerInventory.setChanged();entity.selectRecipe(recipe.id());feedback(player,FEEDBACK_FILLED);return true;
    }
    public record FillPlan(List<ItemStack> inputs,List<ItemStack> playerItems,int reason){}
    /** Pure dry-run shared by UI and authoritative server; every failure leaves both inventories intact. */
    public static FillPlan fillPlan(RetortRecipe recipe,List<ItemStack> input,List<ItemStack> player){
        if(input.size()!=6||player.size()!=36)return new FillPlan(List.of(),List.of(),FEEDBACK_INVALID);
        if(recipe.allocation(new RetortInput(input))!=null)return new FillPlan(List.of(),List.of(),FEEDBACK_ALREADY_READY);
        var existing=input.stream().map(ItemStack::copy).toList();var bags=player.stream().map(ItemStack::copy).toList();
        var supply=new ArrayList<ItemStack>(existing);var playerOrder=new ArrayList<Integer>();
        for(int i=0;i<36;i++)playerOrder.add(i);
        playerOrder.sort(Comparator.comparingInt(i->existing.stream().anyMatch(s->!s.isEmpty()&&ItemStack.isSameItemSameComponents(s,bags.get(i)))?0:1));
        playerOrder.forEach(i->supply.add(bags.get(i)));
        int[] take=allocation(recipe,supply);
        if(take==null)return new FillPlan(List.of(),List.of(),FEEDBACK_MISSING);
        var packed=new ArrayList<ItemStack>();existing.forEach(s->packed.add(s.copy()));
        var remaining=new ArrayList<ItemStack>();bags.forEach(s->remaining.add(s.copy()));
        for(int i=0;i<36;i++){
            int source=playerOrder.get(i);var stack=remaining.get(source).split(take[i+6]);
            for(int slot=0;slot<6&&!stack.isEmpty();slot++){
                var at=packed.get(slot);if(at.isEmpty()||!ItemStack.isSameItemSameComponents(at,stack))continue;
                int amount=Math.min(stack.getCount(),Math.max(0,at.getMaxStackSize()-at.getCount()));at.grow(amount);stack.shrink(amount);
            }
            for(int slot=0;slot<6&&!stack.isEmpty();slot++)if(packed.get(slot).isEmpty()){
                int amount=Math.min(stack.getCount(),stack.getMaxStackSize());packed.set(slot,stack.copyWithCount(amount));stack.shrink(amount);
            }
            if(!stack.isEmpty())return new FillPlan(List.of(),List.of(),FEEDBACK_INPUT_SPACE);
        }
        if(recipe.allocation(new RetortInput(packed))==null)return new FillPlan(List.of(),List.of(),FEEDBACK_INVALID);
        return new FillPlan(packed,remaining,FEEDBACK_FILLED);
    }
    private static int[] allocation(RetortRecipe recipe,List<ItemStack> supply){
        int n=recipe.inputs().size(),sink=n+supply.size()+1;int[][] capacity=new int[sink+1][sink+1];int total=0;
        for(int i=0;i<n;i++){
            var input=recipe.inputs().get(i);capacity[0][i+1]=input.count();total+=input.count();
            for(int slot=0;slot<supply.size();slot++)if(input.ingredient().test(supply.get(slot)))capacity[i+1][n+1+slot]=input.count();
        }
        for(int slot=0;slot<supply.size();slot++)capacity[n+1+slot][sink]=supply.get(slot).getCount();
        int flowed=0;
        while(flowed<total){
            int[] parent=new int[sink+1];Arrays.fill(parent,-1);parent[0]=0;var queue=new ArrayDeque<Integer>();queue.add(0);
            while(!queue.isEmpty()&&parent[sink]<0){int at=queue.remove();for(int next=1;next<=sink;next++)if(parent[next]<0&&capacity[at][next]>0){parent[next]=at;queue.add(next);}}
            if(parent[sink]<0)return null;
            int amount=total-flowed;for(int at=sink;at!=0;at=parent[at])amount=Math.min(amount,capacity[parent[at]][at]);
            for(int at=sink;at!=0;at=parent[at]){capacity[parent[at]][at]-=amount;capacity[at][parent[at]]+=amount;}flowed+=amount;
        }
        int[] take=new int[supply.size()];for(int slot=0;slot<take.length;slot++)take[slot]=capacity[sink][n+1+slot];return take;
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size()||index>=10&&index<13)return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var item=slot.getItem();var copy=item.copy();
        if(index<10){if(!moveItemStackTo(item,13,49,true))return ItemStack.EMPTY;}
        else if(item.is(MineralEcology.UMBRAL_COAL.get())){if(!moveItemStackTo(item,6,7,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(item,0,6,false))return ItemStack.EMPTY;
        if(item.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,item);return copy;
    }
}
