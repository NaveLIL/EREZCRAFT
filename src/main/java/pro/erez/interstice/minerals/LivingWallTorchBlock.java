package pro.erez.interstice.minerals;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class LivingWallTorchBlock extends WallTorchBlock implements EntityBlock {
    public LivingWallTorchBlock(){this(BlockBehaviour.Properties.ofFullCopy(Blocks.WALL_TORCH).lightLevel(s->13));}
    private LivingWallTorchBlock(BlockBehaviour.Properties properties){super(ParticleTypes.GLOW,properties);}
    @Override public MapCodec<WallTorchBlock> codec(){return simpleCodec(properties->new LivingWallTorchBlock(properties));}
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return switch(state.getValue(FACING)){case EAST->box(0,2,4,8,16,12);case WEST->box(8,2,4,16,16,12);case NORTH->box(4,2,8,12,16,16);default->box(4,2,0,12,16,8);};}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new LivingTorchBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return LivingTorchBlockEntity.ticker(level,type);}
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random){if(random.nextInt(3)==0){var side=state.getValue(FACING).getOpposite();level.addParticle(ParticleTypes.GLOW,pos.getX()+.5+side.getStepX()*.27,pos.getY()+.7,pos.getZ()+.5+side.getStepZ()*.27,0,.01,0);}}
}
