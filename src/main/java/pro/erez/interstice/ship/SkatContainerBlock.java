package pro.erez.interstice.ship;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class SkatContainerBlock extends SkatBlock implements EntityBlock {
    private final boolean helm;
    public SkatContainerBlock(boolean helm){this.helm=helm;}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return helm?new SkatHelmEntity(p,s):new SkatCargoEntity(p,s);}
    @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(net.minecraft.world.level.Level w,BlockState s,net.minecraft.world.level.block.entity.BlockEntityType<T> type){
        if(!helm||type!=SkatShips.HELM_ENTITY.get())return null;
        return (world,pos,state,entity)->{if(world.isClientSide)((SkatHelmEntity)entity).animate();else ((SkatHelmEntity)entity).process();};
    }
}
