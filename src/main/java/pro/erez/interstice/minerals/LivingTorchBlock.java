package pro.erez.interstice.minerals;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class LivingTorchBlock extends TorchBlock implements EntityBlock {
    public LivingTorchBlock(){this(BlockBehaviour.Properties.ofFullCopy(Blocks.TORCH).lightLevel(s->13));}
    private LivingTorchBlock(BlockBehaviour.Properties properties){super(ParticleTypes.GLOW,properties);}
    @Override public MapCodec<LivingTorchBlock> codec(){return simpleCodec(LivingTorchBlock::new);}
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return box(4.5,0,4.5,11.5,14,11.5);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new LivingTorchBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return LivingTorchBlockEntity.ticker(level,type);}
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random){if(random.nextInt(3)==0)level.addParticle(ParticleTypes.GLOW,pos.getX()+.5,pos.getY()+.7,pos.getZ()+.5,0,.01,0);}
}
