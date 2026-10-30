package pro.erez.interstice.ship;
import java.util.List;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Saved finite fuel; neutral/disconnected pilots cannot leave a powered runaway ship. */
public final class SkatEngineEntity extends GeneratingKineticBlockEntity {
    private int fuelTicks,remaining;private float command,loadedKineticSpeed;private boolean initialized;
    public float loadedKineticSpeed(){return loadedKineticSpeed;}
    public SkatEngineEntity(BlockPos p,BlockState s){super(SkatShips.ENGINE_ENTITY.get(),p,s);}
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours){}
    @Override public float getGeneratedSpeed(){return fuelTicks>0?command:0;}
    @Override public float calculateAddedStressCapacity(){return lastCapacityProvided=256;}
    public int fuelTicks(){return fuelTicks;}
    public boolean addFuel(ItemStack stack,boolean creative){
        return addFuel(stack,creative,1);
    }
    public boolean addFuel(ItemStack stack,boolean creative,int requested){
        int burn=stack.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING);if(burn<=0||stack.hasCraftingRemainingItem())return false;
        int count=Math.min(Math.min(stack.getCount(),Math.clamp(requested,1,64)),(64000-fuelTicks)/burn);if(count<=0)return false;
        if(level!=null&&!level.isClientSide){boolean empty=fuelTicks==0;fuelTicks+=burn*count;if(!creative)stack.shrink(count);if(empty&&command!=0)updateGeneratedRotation();setChanged();sendData();}return true;
    }
    public void throttle(float rpm){rpm=Math.clamp(rpm,-128,128);if(command!=rpm){command=rpm;updateGeneratedRotation();setChanged();sendData();}}
    @Override public void tick(){super.tick();if(level==null||level.isClientSide)return;
        if(!initialized){initialized=true;updateGeneratedRotation();}
        if(command!=0&&fuelTicks>0){fuelTicks--;remaining++;if(fuelTicks==0)updateGeneratedRotation();if(remaining%20==0)setChanged();}
    }
    @Override protected void write(CompoundTag tag,HolderLookup.Provider lookup,boolean packet){super.write(tag,lookup,packet);tag.putInt("FuelTicks",fuelTicks);if(packet)tag.putFloat("Command",command);}
    @Override protected void read(CompoundTag tag,HolderLookup.Provider lookup,boolean packet){super.read(tag,lookup,packet);fuelTicks=Math.clamp(tag.getInt("FuelTicks"),0,64000);command=packet?Math.clamp(tag.getFloat("Command"),-128,128):0;
        if(!packet){if(Math.abs(tag.getFloat("Speed"))>Math.abs(loadedKineticSpeed))loadedKineticSpeed=tag.getFloat("Speed");clearKineticInformation();updateSpeed=true;preventSpeedUpdate=0;reActivateSource=false;initialized=false;}
    }
}
