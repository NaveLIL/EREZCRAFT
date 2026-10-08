package pro.erez.interstice.equipment;

import java.util.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A live lease tied to both source object and UUID. Lost leases never write their stale snapshot back. */
public final class BackpackInventory extends SimpleContainer {
    private final Player player;
    private final ItemStack source;
    private final UUID id;
    public final int sourceSlot;
    private boolean loading;
    public BackpackInventory(Player player,int slot){super(BackpackStorage.capacity(BackpackStorage.stack(player,slot)));this.player=player;source=BackpackStorage.stack(player,slot);sourceSlot=slot;id=BackpackStorage.ensureId(source);
        loading=true;var items=BackpackStorage.read(source);for(int i=0;i<items.size();i++)super.setItem(i,items.get(i));loading=false;
    }
    public boolean bound(){return player.isAlive()&&BackpackStorage.stack(player,sourceSlot)==source&&BackpackStorage.isPack(source)&&id.equals(BackpackStorage.id(source));}
    public ItemStack source(){return source;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return BackpackStorage.allowed(stack);}
    @Override public void setChanged(){super.setChanged();if(!loading&&player!=null&&bound()){var items=new ArrayList<ItemStack>();for(int i=0;i<getContainerSize();i++)items.add(getItem(i).copy());BackpackStorage.write(source,items);player.getInventory().setChanged();if(sourceSlot==BackpackHarness.SLOT)BackpackHarness.changed(player);}}
    public int insert(ItemStack stack){if(!bound())return 0;var items=BackpackStorage.read(source);int accepted=BackpackStorage.insert(items,stack);if(accepted>0)replace(items);return accepted;}
    public void replace(List<ItemStack> items){if(!bound())return;if(items.size()!=getContainerSize()||items.stream().anyMatch(s->!BackpackStorage.allowed(s)))throw new IllegalArgumentException("Invalid live pack transaction");loading=true;for(int i=0;i<items.size();i++)super.setItem(i,items.get(i).copy());loading=false;setChanged();}
}
