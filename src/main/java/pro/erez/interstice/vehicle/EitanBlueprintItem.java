package pro.erez.interstice.vehicle;

import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;

/** Creative delivery until the owner's deferred progression/recipes stage. */
public final class EitanBlueprintItem extends Item {
    public EitanBlueprintItem(){super(new Properties().stacksTo(1));}
    @Override public InteractionResult useOn(UseOnContext context){var player=context.getPlayer();if(player==null)return InteractionResult.FAIL;if(!player.isCreative()){player.displayClientMessage(Component.translatable("interstice.eitan.creative_blueprint"),true);return InteractionResult.FAIL;}if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
        var level=(ServerLevel)context.getLevel();var site=context.getClickedPos().above();
        try{EitanAssembly.create(level,site,player.getUUID()).whenComplete((ship,error)->level.getServer().execute(()->player.displayClientMessage(Component.translatable(error==null?"interstice.eitan.created":"interstice.eitan.create_failed"),false)));return InteractionResult.CONSUME;}catch(IllegalArgumentException e){player.displayClientMessage(Component.translatable("interstice.eitan.clear_site"),true);return InteractionResult.FAIL;}
    }
}
