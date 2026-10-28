package pro.erez.interstice.gear;

import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Native slot transactions own module moves; a stale armor lease cannot write back. */
public final class EngineerMenu extends AbstractContainerMenu {
    private final Player owner;
    private final ItemStack helmet,chest;
    private final SimpleContainer sockets;
    private boolean refreshing=true;
    public EngineerMenu(int id,Inventory inventory){
        super(EngineerArmor.MENU.get(),id);owner=inventory.player;
        helmet=owner.getItemBySlot(EquipmentSlot.HEAD);chest=owner.getItemBySlot(EquipmentSlot.CHEST);
        sockets=new SimpleContainer(3){@Override public void setChanged(){super.setChanged();if(!refreshing&&!owner.level().isClientSide)flush();}};
        for(int i=0;i<3;i++){final int index=i;addSlot(new Slot(sockets,i,30+i*50,34){
            @Override public boolean mayPlace(ItemStack stack){return indexAllowed(index,stack);}
            @Override public boolean mayPickup(Player p){return stillValid(p);}
            @Override public int getMaxStackSize(){return 1;}
            @Override public int getMaxStackSize(ItemStack stack){return 1;}
        });}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,8+col*18,112+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,8+col*18,170));
        refreshing=false;refresh();
    }
    private ItemStack armor(int slot){return slot==0?helmet:chest;}
    private int socket(int slot){return slot==2?1:0;}
    private boolean indexAllowed(int slot,ItemStack stack){
        var item=armor(slot);var equipment=slot==0?EquipmentSlot.HEAD:EquipmentSlot.CHEST;
        return EngineerArmor.worn(item,equipment)&&EngineerModules.valid(item)&&EngineerModules.allowed(item,socket(slot),stack);
    }
    private void refresh(){
        if(owner.level().isClientSide)return;refreshing=true;
        try{for(int i=0;i<3;i++)sockets.setItem(i,EngineerModules.get(armor(i),socket(i)));}finally{refreshing=false;}
    }
    private void flush(){
        if(!stillValid(owner))return;
        for(int i=0;i<3;i++)if(EngineerModules.valid(armor(i))){var stack=sockets.getItem(i);if(!EngineerModules.allowed(armor(i),socket(i),stack))throw new IllegalStateException("Invalid engineer module transaction");EngineerModules.set(armor(i),socket(i),stack);}
        owner.getInventory().setChanged();
    }
    @Override public boolean stillValid(Player player){return player==owner&&owner.isAlive()&&!owner.isSpectator()&&(owner.level().isClientSide||owner.getItemBySlot(EquipmentSlot.HEAD)==helmet&&owner.getItemBySlot(EquipmentSlot.CHEST)==chest)
        &&(EngineerArmor.worn(owner,EquipmentSlot.HEAD)||EngineerArmor.worn(owner,EquipmentSlot.CHEST));}
    @Override public void clicked(int slot,int button,ClickType type,Player player){if(!stillValid(player))return;refresh();super.clicked(slot,button,type,player);if(!owner.level().isClientSide)flush();}
    @Override public void broadcastChanges(){refresh();super.broadcastChanges();}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(!stillValid(player)||index<0||index>=slots.size())return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var copy=stack.copy();
        if(index<3){if(!moveItemStackTo(stack,3,39,true))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(stack,0,3,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return copy;
    }
    @Override public boolean clickMenuButton(Player player,int button){
        if(!(player instanceof net.minecraft.server.level.ServerPlayer server)||!stillValid(player)||!EngineerArmor.worn(player,EquipmentSlot.HEAD))return false;
        if(button==0||button==1){EngineerSystems.toggle(helmet,button==1);owner.getInventory().setChanged();EngineerSystems.syncWorn(server);return true;}
        return button==2&&EngineerSystems.pulse(server);
    }
}
