package pro.erez.interstice.equipment;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** An equipment socket, not another storage layer. The native menu owns every item transfer. */
public final class HarnessMenu extends AbstractContainerMenu {
    private final Player owner;
    private final Container back;
    public HarnessMenu(int id,Inventory inventory){
        super(ExpeditionEquipment.HARNESS_MENU.get(),id);owner=inventory.player;
        back=owner.level().isClientSide?new SimpleContainer(1):new Container(){
            public int getContainerSize(){return 1;}
            public boolean isEmpty(){return BackpackHarness.get(owner).isEmpty();}
            public ItemStack getItem(int slot){return slot==0?BackpackHarness.get(owner):ItemStack.EMPTY;}
            public ItemStack removeItem(int slot,int count){if(slot!=0||count<=0)return ItemStack.EMPTY;var stack=BackpackHarness.get(owner);if(stack.isEmpty())return ItemStack.EMPTY;BackpackHarness.set(owner,ItemStack.EMPTY);return stack;}
            public ItemStack removeItemNoUpdate(int slot){return removeItem(slot,1);}
            public void setItem(int slot,ItemStack stack){if(slot!=0||!BackpackHarness.valid(stack))throw new IllegalArgumentException("Invalid back socket item");BackpackHarness.set(owner,stack);}
            public void setChanged(){BackpackHarness.changed(owner);}
            public boolean stillValid(Player player){return player==owner&&owner.isAlive()&&!owner.isSpectator();}
            public void clearContent(){BackpackHarness.set(owner,ItemStack.EMPTY);}
            public int getMaxStackSize(){return 1;}
            public boolean canPlaceItem(int slot,ItemStack stack){return slot==0&&BackpackHarness.valid(stack)&&!stack.isEmpty();}
        };
        addSlot(new Slot(back,0,80,34){@Override public boolean mayPlace(ItemStack stack){return !stack.isEmpty()&&BackpackHarness.valid(stack);}@Override public int getMaxStackSize(){return 1;}@Override public int getMaxStackSize(ItemStack stack){return 1;}});
        for(int row=0;row<3;row++)for(int column=0;column<9;column++)addSlot(new Slot(inventory,column+row*9+9,8+column*18,82+row*18));
        for(int column=0;column<9;column++)addSlot(new Slot(inventory,column,8+column*18,140));
    }
    @Override public boolean stillValid(Player player){return player==owner&&owner.isAlive()&&!owner.isSpectator();}
    @Override public void clicked(int slot,int button,ClickType type,Player player){if(stillValid(player))super.clicked(slot,button,type,player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(!stillValid(player)||index<0||index>=slots.size())return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var copy=stack.copy();
        if(index==0){if(!moveItemStackTo(stack,1,37,true))return ItemStack.EMPTY;}
        else{if(!BackpackHarness.valid(stack)||!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;}
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return copy;
    }
}
