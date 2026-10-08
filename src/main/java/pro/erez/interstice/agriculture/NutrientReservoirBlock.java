package pro.erez.interstice.agriculture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import pro.erez.interstice.Interstice;

public final class NutrientReservoirBlock extends BaseEntityBlock {
    public static final BooleanProperty FILLED=BooleanProperty.create("filled");
    public NutrientReservoirBlock(){this(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS).strength(3,8));}
    private NutrientReservoirBlock(BlockBehaviour.Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FILLED,false));}
    @Override public MapCodec<NutrientReservoirBlock> codec(){return simpleCodec(NutrientReservoirBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FILLED);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NutrientReservoirEntity(pos,state);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!(level.getBlockEntity(pos) instanceof NutrientReservoirEntity reservoir))return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        boolean fill=stack.is(Interstice.RIFTSILVER_HEAVY_BUCKET.get())&&reservoir.amount()==0;
        boolean drain=stack.is(Interstice.RIFTSILVER_BUCKET.get())&&reservoir.amount()==1000;
        if(!fill&&!drain)return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(!level.isClientSide){reservoir.setFull(fill);
            if(!player.isCreative()){
                var result=new ItemStack(fill?Interstice.RIFTSILVER_BUCKET.get():Interstice.RIFTSILVER_HEAVY_BUCKET.get());stack.shrink(1);
                if(stack.isEmpty())player.setItemInHand(hand,result);else if(!player.getInventory().add(result))player.drop(result,false);
            }
        }return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved){
        if(!state.is(next.getBlock())&&!level.isClientSide&&level.getBlockEntity(pos) instanceof NutrientReservoirEntity reservoir&&reservoir.amount()==1000){
            var saved=reservoir.saveWithFullMetadata(level.registryAccess());
            if(reservoir.emptyForRemoval()){
                var item=new ItemStack(RealmAgriculture.RESERVOIR.get());
                item.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(saved));
                item.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,1);
                Block.popResource(level,pos,item);
            }
        }super.onRemove(state,level,pos,next,moved);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity owner,ItemStack stack){
        if(!level.isClientSide&&level.getBlockEntity(pos) instanceof NutrientReservoirEntity reservoir)reservoir.setFull(reservoir.amount()==1000);
    }
}
