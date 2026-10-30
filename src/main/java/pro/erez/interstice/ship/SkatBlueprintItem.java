package pro.erez.interstice.ship;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;

/** Explicit creative construction tool; no automatic world placement or progression changes. */
public final class SkatBlueprintItem extends Item {
    public SkatBlueprintItem(){super(new Properties().stacksTo(1));}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();if(player==null||!player.isCreative())return InteractionResult.FAIL;
        if(context.getLevel() instanceof ServerLevel w)try{SkatAssembly.create(w,context.getClickedPos().above(),player.getUUID()).whenCompleteAsync((ship,error)->player.displayClientMessage(error==null?Component.translatable("message.interstice.skat_built"):Component.literal("Skat assembly failed: "+error.getMessage()),false),w.getServer());}
        catch(IllegalArgumentException e){player.displayClientMessage(Component.literal(e.getMessage()),false);return InteractionResult.FAIL;}
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
