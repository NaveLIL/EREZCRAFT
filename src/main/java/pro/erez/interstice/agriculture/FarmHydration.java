package pro.erez.interstice.agriculture;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import pro.erez.interstice.Interstice;

public final class FarmHydration {
    public enum State { WET,DRY,UNKNOWN }
    private FarmHydration(){}
    public static State scan(LevelReader level,BlockPos soil){
        boolean unknown=false;
        for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)for(int dy=0;dy<=1;dy++){
            var at=soil.offset(dx,dy,dz);if(!level.hasChunkAt(at)){unknown=true;continue;}
            var fluid=level.getFluidState(at);
            if(fluid.is(Interstice.HEAVY.get())||fluid.is(Interstice.HEAVY_FLOW.get()))return State.WET;
            if(level.getBlockEntity(at) instanceof NutrientReservoirEntity reservoir&&reservoir.amount()==1000)return State.WET;
        }
        return unknown?State.UNKNOWN:State.DRY;
    }
}
