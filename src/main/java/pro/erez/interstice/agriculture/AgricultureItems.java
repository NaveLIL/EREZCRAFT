package pro.erez.interstice.agriculture;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.CommonHooks;

public final class AgricultureItems {
    private AgricultureItems(){}
    public static final class ReservoirItem extends BlockItem {
        public ReservoirItem(){super(RealmAgriculture.RESERVOIR.get(),new Properties());}
        public static boolean full(ItemStack stack){return stack.getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getInt("Amount")==1000;}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> text,TooltipFlag flags){text.add(Component.translatable(full(stack)?"tooltip.interstice.reservoir.full":"tooltip.interstice.reservoir.empty"));}
    }
    public static final class ToxicRoot extends ItemNameBlockItem {
        public ToxicRoot(){super(RealmAgriculture.ROOT_CROP.get(),new Properties().food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(3).saturationModifier(.2F).build()));}
        @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity consumer){var result=super.finishUsingItem(stack,level,consumer);
            if(!level.isClientSide&&!NativeFoodTolerance.canDigest(consumer,NativeFoodTolerance.FoodKind.ROOT)){
                consumer.addEffect(new MobEffectInstance(MobEffects.POISON,160,0));consumer.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,240,0));
            }return result;
        }
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> text,TooltipFlag flags){text.add(Component.translatable("tooltip.interstice.native_root.toxic"));text.add(Component.translatable("tooltip.interstice.native_crop"));}
    }
    public static final class Fertilizer extends Item {
        public Fertilizer(){super(new Properties());}
        @Override public InteractionResult useOn(UseOnContext context){var level=context.getLevel();var pos=context.getClickedPos();var state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof NativeCropBlock crop)||!crop.canGrow(level,pos,state))return InteractionResult.PASS;
            if(level instanceof ServerLevel server&&CommonHooks.canCropGrow(server,pos,state,true)){crop.advance(server,pos,state);if(context.getPlayer()==null||!context.getPlayer().isCreative())context.getItemInHand().shrink(1);}
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> text,TooltipFlag flags){text.add(Component.translatable("tooltip.interstice.mineral_fertilizer"));}
    }
    public static final class Cultivator extends Item {
        public Cultivator(){super(new Properties().durability(256));}
        @Override public InteractionResult useOn(UseOnContext context){var level=context.getLevel();var pos=context.getClickedPos();var state=level.getBlockState(pos);var player=context.getPlayer();
            if(!(state.getBlock() instanceof NativeCropBlock crop)||!crop.isMaxAge(state)||player==null||!player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand()))return InteractionResult.PASS;
            if(level instanceof ServerLevel server){
                var drops=Block.getDrops(state,server,pos,server.getBlockEntity(pos),player,ItemStack.EMPTY);var seed=state.is(RealmAgriculture.GRAIN_CROP.get())?RealmAgriculture.GRAIN.get():RealmAgriculture.ROOT.get();boolean replant=false;
                for(var stack:drops)if(stack.is(seed)&&!stack.isEmpty()){stack.shrink(1);replant=true;break;}
                if(!replant)for(int i=0;i<player.getInventory().items.size();i++)if(player.getInventory().items.get(i).is(seed)){player.getInventory().removeItem(i,1);replant=true;break;}
                level.setBlock(pos,replant?crop.getStateForAge(0):net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                for(var stack:drops)if(!stack.isEmpty())Block.popResource(server,pos,stack);
                if(!player.isCreative())context.getItemInHand().hurtAndBreak(1,player,LivingEntity.getSlotForHand(context.getHand()));
            }return InteractionResult.sidedSuccess(level.isClientSide);
        }
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> text,TooltipFlag flags){text.add(Component.translatable("tooltip.interstice.cultivator"));}
    }
}
