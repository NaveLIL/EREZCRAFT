package pro.erez.interstice.equipment;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class BackpackMenu extends AbstractContainerMenu {
    public static final int MODE=0,SORT=1,STASH=2,REFILL=3;
    public final int sourceSlot,capacity,columns,width;
    public final Container container;
    public final ContainerData data;
    private final Inventory playerInventory;
    public BackpackMenu(int id,Inventory player,RegistryFriendlyByteBuf buffer){this(id,player,buffer.readVarInt(),buffer.readVarInt(),null);}
    public BackpackMenu(int id,Inventory player,int slot){this(id,player,slot,BackpackStorage.capacity(BackpackStorage.stack(player.player,slot)),new BackpackInventory(player.player,slot));}
    private BackpackMenu(int id,Inventory player,int slot,int capacity,BackpackInventory live){
        super(ExpeditionEquipment.PACK_MENU.get(),id);if(capacity!=54&&capacity!=72)throw new IllegalArgumentException("Unsupported pack size");if(slot<0||slot>=36&&slot!=40&&slot!=BackpackHarness.SLOT)throw new IllegalArgumentException("Unsupported pack source");
        sourceSlot=slot;this.capacity=capacity;columns=capacity/6;width=columns*18+16;playerInventory=player;container=live==null?new SimpleContainer(capacity):live;
        data=live==null?new SimpleContainerData(2):new ContainerData(){public int get(int i){if(!live.bound()||!BackpackStorage.valid(live.source()))return 0;return i==0?BackpackStorage.mode(live.source()):(int)BackpackStorage.read(live.source()).stream().filter(s->!s.isEmpty()).count();}public void set(int i,int value){}public int getCount(){return 2;}};
        for(int i=0;i<capacity;i++)addSlot(new Slot(container,i,8+(i%columns)*18,16+(i/columns)*18){@Override public boolean mayPlace(ItemStack stack){return BackpackStorage.allowed(stack);}});
        int left=(width-162)/2;
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addPlayerSlot(player,col+row*9+9,left+col*18,136+row*18);
        for(int col=0;col<9;col++)addPlayerSlot(player,col,left+col*18,194);addDataSlots(data);
    }
    private void addPlayerSlot(Inventory inventory,int i,int x,int y){addSlot(new Slot(inventory,i,x,y){@Override public boolean mayPickup(Player p){return i!=sourceSlot;}@Override public boolean mayPlace(ItemStack stack){return i!=sourceSlot;}});}
    @Override public boolean stillValid(Player player){return container instanceof BackpackInventory live?live.bound():true;}
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(!stillValid(player))return;if(type==ClickType.SWAP&&(button==sourceSlot||sourceSlot==40&&button==40))return;
        super.clicked(slot,button,type,player);if(container instanceof BackpackInventory live&&live.bound())live.setChanged();
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(!stillValid(player)||index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.mayPickup(player)||!slot.hasItem())return ItemStack.EMPTY;
        var stack=slot.getItem();var copy=stack.copy();if(index<capacity){if(!moveItemStackTo(stack,capacity,capacity+36,true))return ItemStack.EMPTY;}
        else{if(!BackpackStorage.allowed(stack)||!moveItemStackTo(stack,0,capacity,false))return ItemStack.EMPTY;}
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);container.setChanged();return copy;
    }
    @Override public boolean clickMenuButton(Player player,int button){
        if(!stillValid(player)||!(container instanceof BackpackInventory live)||!getCarried().isEmpty())return false;
        if(button==MODE){BackpackStorage.mode(live.source(),(BackpackStorage.mode(live.source())+1)%(capacity==72?3:2));player.getInventory().setChanged();if(sourceSlot==BackpackHarness.SLOT)BackpackHarness.changed(player);return true;}
        if(button==SORT){sort(live);return true;}if(button==STASH){stash(live,player);return true;}if(button==REFILL){refill(live,player);return true;}return false;
    }
    public static void sort(BackpackInventory live){
        if(!live.bound())return;var nonempty=BackpackStorage.read(live.source()).stream().filter(s->!s.isEmpty()).map(ItemStack::copy).sorted(Comparator.comparing((ItemStack s)->BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).thenComparing(s->s.getHoverName().getString())).toList();
        var result=new ArrayList<ItemStack>(Collections.nCopies(live.getContainerSize(),ItemStack.EMPTY));for(var stack:nonempty)if(BackpackStorage.insert(result,stack)!=stack.getCount())throw new IllegalStateException("Sorting must preserve capacity");live.replace(result);
    }
    public static void stash(BackpackInventory live,Player player){
        if(!live.bound())return;for(int i=9;i<36;i++){var stack=player.getInventory().getItem(i);if(i==live.sourceSlot||stack.isEmpty())continue;int accepted=live.insert(stack);if(accepted>0)stack.shrink(accepted);}player.getInventory().setChanged();
    }
    public static void refill(BackpackInventory live,Player player){
        if(!live.bound())return;var items=BackpackStorage.read(live.source());for(int i=0;i<9;i++){var at=player.getInventory().getItem(i);if(at.isEmpty()||i==live.sourceSlot||at.getCount()>=at.getMaxStackSize())continue;
            for(var reserve:items)if(ItemStack.isSameItemSameComponents(at,reserve)){int take=Math.min(reserve.getCount(),at.getMaxStackSize()-at.getCount());at.grow(take);reserve.shrink(take);if(at.getCount()==at.getMaxStackSize())break;}}
        live.replace(items);player.getInventory().setChanged();
    }
}
