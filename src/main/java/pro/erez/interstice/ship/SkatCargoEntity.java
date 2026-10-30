package pro.erez.interstice.ship;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 24 independent, server-authoritative 54-slot containers; no per-tick inventory scan. */
public final class SkatCargoEntity extends BlockEntity implements Container,MenuProvider {
    private NonNullList<ItemStack> items=NonNullList.withSize(54,ItemStack.EMPTY);
    private UUID id=UUID.randomUUID();private int fuelSlot=-2;
    @Override public void setChanged(){super.setChanged();fuelSlot=-2;}
    public boolean fuelBunker(){return SkatData.cell(getBlockState().getValue(SkatData.CELL)).cargo()==0;}
    public boolean feed(SkatEngineEntity engine){if(fuelSlot==-2){fuelSlot=-1;for(int i=0;i<items.size();i++){var stack=items.get(i);int burn=stack.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING);if(!stack.isEmpty()&&!stack.hasCraftingRemainingItem()&&burn>0&&burn<=64000){fuelSlot=i;break;}}}if(fuelSlot<0)return false;var stack=items.get(fuelSlot);if(!engine.addFuel(stack,false,1))return false;super.setChanged();if(stack.isEmpty())fuelSlot=-2;return true;}
    public SkatCargoEntity(BlockPos p,BlockState s){super(SkatShips.CARGO_ENTITY.get(),p,s);}
    public UUID inventoryId(){return id;}
    @Override public int getContainerSize(){return 54;}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return items.get(slot);}
    @Override public ItemStack removeItem(int slot,int count){var s=ContainerHelper.removeItem(items,slot,count);if(!s.isEmpty())setChanged();return s;}
    @Override public ItemStack removeItemNoUpdate(int slot){var s=ContainerHelper.takeItem(items,slot);setChanged();return s;}
    @Override public void setItem(int slot,ItemStack s){items.set(slot,s.copyWithCount(Math.min(getMaxStackSize(s),s.getCount())));setChanged();}
    @Override public void clearContent(){items.clear();setChanged();}
    @Override public boolean stillValid(Player p){
        if(level==null||p.level()!=level||!pro.erez.interstice.vehicle.VehicleAccess.allowed(p,pro.erez.interstice.vehicle.VehicleAccess.anchor(level,worldPosition))||level.getBlockEntity(worldPosition)!=this)return false;
        var center=new org.joml.Vector3d(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5);
        var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if(container.inBounds(worldPosition))container.getPlot(new net.minecraft.world.level.ChunkPos(worldPosition)).getSubLevel().logicalPose().transformPosition(center);
        return p.distanceToSqr(center.x,center.y,center.z)<=64;
    }
    @Override public Component getDisplayName(){int index=SkatData.cell(getBlockState().getValue(SkatData.CELL)).cargo();return index>=24?Component.translatable("container.interstice.skat_deck_cargo",index-23):index==0?Component.translatable("container.interstice.skat_fuel_bunker"):Component.translatable("container.interstice.skat_cargo",SkatData.cell(getBlockState().getValue(SkatData.CELL)).cargo()+1);}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player p){return new SkatCargoMenu(id,inventory,this);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putUUID("InventoryId",id);ContainerHelper.saveAllItems(tag,items,lookup);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);items=NonNullList.withSize(54,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items,lookup);if(tag.hasUUID("InventoryId"))id=tag.getUUID("InventoryId");}
}
