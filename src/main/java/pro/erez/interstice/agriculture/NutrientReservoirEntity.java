package pro.erez.interstice.agriculture;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One bucket, saved rather than inferred from decorative block state. No automatic neighbor copying. */
public final class NutrientReservoirEntity extends BlockEntity {
    private int amount;
    public NutrientReservoirEntity(BlockPos pos,BlockState state){super(RealmAgriculture.RESERVOIR_ENTITY.get(),pos,state);}
    public int amount(){return amount;}
    public boolean emptyForRemoval(){boolean full=amount==1000;amount=0;setChanged();return full;}
    public void setFull(boolean full){amount=full?1000:0;setChanged();if(level!=null){var state=getBlockState();level.setBlock(worldPosition,state.setValue(NutrientReservoirBlock.FILLED,full),3);level.sendBlockUpdated(worldPosition,state,getBlockState(),3);}}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putInt("Amount",amount);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);amount=tag.getInt("Amount")==1000?1000:0;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){return saveWithoutMetadata(lookup);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
