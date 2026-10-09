package pro.erez.interstice.ecology;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.common.ItemAbilities;

/** Familiar sting interval is shared with existing fronds, so two species cannot double one contact. */
public final class VenomReedBlock extends Block {
    public VenomReedBlock(){this(BlockBehaviour.Properties.of().noCollission().noOcclusion().instabreak().sound(SoundType.ROOTS));}
    private VenomReedBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override protected MapCodec<VenomReedBlock> codec(){return simpleCodec(VenomReedBlock::new);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return box(2,0,2,14,14,14);}
    public static boolean supported(LevelReader level,BlockPos pos){var below=pos.below();var support=level.getBlockState(below);return !support.is(Blocks.BEDROCK)&&support.getFluidState().isEmpty()&&support.isFaceSturdy(level,below,Direction.UP);}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return supported(level,pos);}
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){return direction==Direction.DOWN&&!canSurvive(state,level,pos)?Blocks.AIR.defaultBlockState():state;}
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity){
        if(level.isClientSide||!(entity instanceof LivingEntity victim)||!victim.isAlive()||victim instanceof Player p&&(p.isCreative()||p.isSpectator()))return;
        long now=level.getServer().overworld().getGameTime();var data=victim.getPersistentData();if(data.getLong(CavePlantBlock.STING_COOLDOWN_TAG)>now)return;
        data.putLong(CavePlantBlock.STING_COOLDOWN_TAG,now+40);if(victim instanceof net.minecraft.server.level.ServerPlayer player&&pro.erez.interstice.gear.RealmGear.protect(player,pro.erez.interstice.gear.RealmGear.HazardKind.STINGING_PLANT))return;victim.hurt(level.damageSources().sweetBerryBush(),2);
        victim.addEffect(new MobEffectInstance(MobEffects.POISON,100));victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60));
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!stack.canPerformAction(ItemAbilities.SHEARS_HARVEST)||!player.mayUseItemAt(pos,hit.getDirection(),stack)||!level.mayInteract(player,pos))return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(level instanceof ServerLevel server){if(!server.getBlockState(pos).is(this))return ItemInteractionResult.FAIL;
            server.setBlock(pos,Blocks.AIR.defaultBlockState(),3);Block.popResource(server,pos,new ItemStack(RealmEcology.VENOM_FIBER.get()));if(!player.isCreative())stack.hurtAndBreak(1,player,LivingEntity.getSlotForHand(hand));
        }return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
