package pro.erez.interstice.ship;

import appeng.api.config.*;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;

/** Finite fuel/energy source. Boat controls can draw from it even if ME channels fail. */
public final class MarineGeneratorEntity extends AENetworkedPoweredBlockEntity {
    private final AppEngInternalInventory fuel=new AppEngInternalInventory(this,1);
    private int burn;private long generatedTicks;private boolean enabled=true;
    public MarineGeneratorEntity(BlockPos p,BlockState s){super(MarineShips.GENERATOR_ENTITY.get(),p,s);setInternalMaxPower(100000);setInternalPublicPowerStorage(true);setInternalPowerFlow(AccessRestriction.READ_WRITE);getMainNode().setFlags(GridFlags.DENSE_CAPACITY).setIdlePowerUsage(2);}
    @Override protected Item getItemFromBlockEntity(){return MarineMe.item("energy_acceptor");}
    @Override protected void emitPowerStateEvent(appeng.api.networking.events.GridPowerStorageStateChanged.PowerEventType type){getMainNode().ifPresent(grid->grid.postEvent(new appeng.api.networking.events.GridPowerStorageStateChanged(this,type)));}
    @Override public InternalInventory getInternalInventory(){return fuel;}
    @Override public void onChangeInventory(AppEngInternalInventory inventory,int slot){setChanged();}
    public boolean addFuel(ItemStack stack,boolean creative){
        if(stack.isEmpty()||stack.getBurnTime(RecipeType.SMELTING)<=0||!stack.getCraftingRemainingItem().isEmpty())return false;
        var old=fuel.getStackInSlot(0);if(!old.isEmpty()&&!ItemStack.isSameItemSameComponents(old,stack))return false;
        int count=Math.min(stack.getCount(),64-old.getCount());if(count<=0)return false;var next=stack.copyWithCount(count+old.getCount());fuel.setItemDirect(0,next);if(!creative)stack.shrink(count);setChanged();return true;
    }
    public void process(){
        if(level==null||level.isClientSide)return;
        if(!enabled){if(level.getGameTime()%20==0){var c=MarineData.cell(getBlockState().getValue(MarineData.CELL));MarineMe.connect(level,worldPosition.subtract(c.pos()),getMainNode().getNode());}return;}
        if(burn==0&&getAECurrentPower()<getAEMaxPower()-128){var stack=fuel.getStackInSlot(0);int duration=stack.getBurnTime(RecipeType.SMELTING);if(duration>0&&stack.getCraftingRemainingItem().isEmpty()){burn=duration;stack.shrink(1);fuel.setItemDirect(0,stack);setChanged();}}
        if(burn>0){burn--;generatedTicks++;injectAEPower(128,Actionable.MODULATE);if(level.getGameTime()%20==0)setChanged();}
        if(level.getGameTime()%20==0){var c=MarineData.cell(getBlockState().getValue(MarineData.CELL));MarineMe.connect(level,worldPosition.subtract(c.pos()),getMainNode().getNode());}
    }
    public int burnTicks(){return burn;}
    public long generatedTicks(){return generatedTicks;}
    public boolean enabled(){return enabled;}
    public void enabled(boolean value){enabled=value;setChanged();}
    @Override public void loadTag(CompoundTag t,HolderLookup.Provider p){super.loadTag(t,p);burn=Math.max(0,t.getInt("MarineBurn"));generatedTicks=Math.max(0,t.getLong("MarineGeneratedTicks"));enabled=!t.contains("MarineEnabled")||t.getBoolean("MarineEnabled");}
    @Override public void saveAdditional(CompoundTag t,HolderLookup.Provider p){super.saveAdditional(t,p);t.putInt("MarineBurn",burn);t.putLong("MarineGeneratedTicks",generatedTicks);t.putBoolean("MarineEnabled",enabled);}
}
