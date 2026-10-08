package pro.erez.interstice.agriculture;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import pro.erez.interstice.minerals.MineralEcology;

public final class RetortMenu extends AbstractContainerMenu {
    public final ContainerData data;
    private final Container inventory;
    private final ContainerLevelAccess access;
    private final SimpleContainer preview=new SimpleContainer(3);
    public RetortMenu(int id,Inventory player,RegistryFriendlyByteBuf buffer){this(id,player,new SimpleContainer(10),new SimpleContainerData(4),ContainerLevelAccess.NULL);buffer.readBlockPos();}
    public RetortMenu(int id,Inventory player,Container inventory,ContainerData data,ContainerLevelAccess access){
        super(RealmAgriculture.RETORT_MENU.get(),id);this.inventory=inventory;this.data=data;this.access=access;checkContainerSize(inventory,10);checkContainerDataCount(data,4);
        for(int i=0;i<6;i++)addSlot(new Slot(inventory,i,32+(i%3)*18,38+(i/3)*18));
        addSlot(new Slot(inventory,6,50,82){@Override public boolean mayPlace(ItemStack stack){return stack.is(MineralEcology.UMBRAL_COAL.get());}});
        for(int i=0;i<3;i++)addSlot(new Slot(inventory,7+i,133+i*18,74){@Override public boolean mayPlace(ItemStack stack){return false;}});
        for(int i=0;i<3;i++)addSlot(new Slot(preview,i,133+i*18,38){@Override public boolean mayPlace(ItemStack stack){return false;}@Override public boolean mayPickup(Player p){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,col+row*9+9,28+col*18,132+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,28+col*18,190));
        addDataSlots(data);broadcastChanges();
    }
    @Override public void broadcastChanges(){if(inventory instanceof RetortBlockEntity entity){var outputs=entity.preview();for(int i=0;i<3;i++)preview.setItem(i,i<outputs.size()?outputs.get(i).copy():ItemStack.EMPTY);}super.broadcastChanges();}
    @Override public boolean stillValid(Player player){return stillValid(access,player,RealmAgriculture.RETORT.get());}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index>=10&&index<13)return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var item=slot.getItem();var copy=item.copy();
        if(index<10){if(!moveItemStackTo(item,13,49,true))return ItemStack.EMPTY;}
        else if(item.is(MineralEcology.UMBRAL_COAL.get())){if(!moveItemStackTo(item,6,7,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(item,0,6,false))return ItemStack.EMPTY;
        if(item.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,item);return copy;
    }
}
