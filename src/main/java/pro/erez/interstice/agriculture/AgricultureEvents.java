package pro.erez.interstice.agriculture;

import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.level.BlockEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.minerals.MineralEcology;

@EventBusSubscriber(modid=Interstice.ID)
public final class AgricultureEvents {
    @SubscribeEvent public static void till(BlockEvent.BlockToolModificationEvent event){
        var state=event.getState();var context=event.getContext();
        if(event.getItemAbility()==ItemAbilities.HOE_TILL&&(state.is(MineralEcology.ROOT_LOAM.get())||state.is(Interstice.ABYSSAL_TURF.get()))
                &&context.getClickedFace()!=Direction.DOWN&&context.getLevel().getBlockState(context.getClickedPos().above()).isAir())
            event.setFinalState(RealmAgriculture.FARMLAND.get().defaultBlockState());
    }
}
