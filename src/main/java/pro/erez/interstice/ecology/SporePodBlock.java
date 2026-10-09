package pro.erez.interstice.ecology;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.common.ItemAbilities;
import pro.erez.interstice.agriculture.RealmAgriculture;

/** A visible native pressure seed. It changes only itself; it never explodes or primes neighbors. */
public final class SporePodBlock extends Block {
    public enum Phase implements StringRepresentable { ARMED("armed"),PRIMED("primed"),SPENT("spent");private final String id;Phase(String id){this.id=id;}public String getSerializedName(){return id;} }
    public static final EnumProperty<Phase> PHASE=EnumProperty.create("phase",Phase.class);
    public static final IntegerProperty FUSE=IntegerProperty.create("fuse",0,12);
    public static final int WARNING_TICKS=12;
    public SporePodBlock(){this(BlockBehaviour.Properties.of().noOcclusion().instabreak().sound(SoundType.FUNGUS).lightLevel(s->s.getValue(PHASE)==Phase.PRIMED?6:s.getValue(PHASE)==Phase.ARMED?2:0));}
    private SporePodBlock(BlockBehaviour.Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(PHASE,Phase.ARMED).setValue(FUSE,0));}
    @Override protected MapCodec<SporePodBlock> codec(){return simpleCodec(SporePodBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(PHASE,FUSE);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return box(2,0,2,14,state.getValue(PHASE)==Phase.SPENT?2:5,14);}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return box(3,0,3,13,2,13);}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return VenomReedBlock.supported(level,pos);}
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){return direction==Direction.DOWN&&!canSurvive(state,level,pos)?Blocks.AIR.defaultBlockState():state;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(PHASE,Phase.SPENT);}
    private static boolean visitor(Entity entity){return entity instanceof LivingEntity alive&&alive.isAlive()&&!(alive instanceof Player p&&(p.isCreative()||p.isSpectator()));}
    private void prime(BlockState state,Level level,BlockPos pos,Entity entity){
        if(!(level instanceof ServerLevel server)||!visitor(entity)||state.getValue(PHASE)!=Phase.ARMED||!server.getBlockState(pos).is(this))return;
        var feet=entity.getBoundingBox();if(feet.minY<pos.getY()-.05||feet.minY>pos.getY()+.4||feet.maxX<=pos.getX()+.2||feet.minX>=pos.getX()+.8||feet.maxZ<=pos.getZ()+.2||feet.minZ>=pos.getZ()+.8)return;
        var current=server.getBlockState(pos);if(current.getValue(PHASE)!=Phase.ARMED)return;
        server.setBlock(pos,current.setValue(PHASE,Phase.PRIMED).setValue(FUSE,WARNING_TICKS),3);warn(server,pos);server.scheduleTick(pos,this,1);
    }
    static void warn(ServerLevel level,BlockPos pos){level.playSound(null,pos,RealmEcology.POD_WARNING.get(),SoundSource.BLOCKS,.7F,.8F);level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,pos.getX()+.5,pos.getY()+.35,pos.getZ()+.5,8,.2,.1,.2,.01);}
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity){prime(state,level,pos,entity);}
    @Override public void stepOn(Level level,BlockPos pos,BlockState state,Entity entity){prime(state,level,pos,entity);super.stepOn(level,pos,state,entity);}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        var current=level.getBlockState(pos);if(!current.is(this)||current.getValue(PHASE)!=Phase.PRIMED)return;
        int fuse=current.getValue(FUSE);if(fuse>1){level.setBlock(pos,current.setValue(FUSE,fuse-1),3);level.scheduleTick(pos,this,1);}
        else{level.setBlock(pos,current.setValue(PHASE,Phase.SPENT).setValue(FUSE,0),3);SporePodGas.emit(level,pos,0);}
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!player.mayUseItemAt(pos,hit.getDirection(),stack)||!level.mayInteract(player,pos))return ItemInteractionResult.FAIL;
        boolean shear=stack.canPerformAction(ItemAbilities.SHEARS_DISARM),arm=stack.is(RealmAgriculture.PASTE.get());
        if(!shear&&!arm)return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(level instanceof ServerLevel server){var current=server.getBlockState(pos);if(!current.is(this))return ItemInteractionResult.FAIL;
            if(shear&&current.getValue(PHASE)!=Phase.SPENT){
                server.setBlock(pos,current.setValue(PHASE,Phase.SPENT).setValue(FUSE,0),3);server.destroyBlock(pos,false);
                Block.popResource(server,pos,new ItemStack(RealmEcology.POD_SHELL.get()));if(!player.isCreative())stack.hurtAndBreak(1,player,LivingEntity.getSlotForHand(hand));
            }else if(arm&&current.getValue(PHASE)==Phase.SPENT){server.setBlock(pos,current.setValue(PHASE,Phase.ARMED).setValue(FUSE,0),3);if(!player.isCreative())stack.shrink(1);}
            else return ItemInteractionResult.FAIL;
        }return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved){
        if(!state.is(next.getBlock())&&state.getValue(PHASE)!=Phase.SPENT&&level instanceof ServerLevel server){warn(server,pos);SporePodGas.emit(server,pos,WARNING_TICKS);}super.onRemove(state,level,pos,next,moved);
    }
}
