package pro.erez.interstice.ship;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
public final class SkatLighting {
    public static final int HEADLIGHTS=1,BOARD=2,INTERIOR=4,ALL=7;
    public static int emission(BlockState state){
        var cell=SkatData.cell(state.getValue(SkatData.CELL));
        if(!state.hasProperty(SkatLampBlock.LIT)||!state.getValue(SkatLampBlock.LIT))return 0;
        return switch(cell.lightMask()){case HEADLIGHTS->15;case BOARD->10;case INTERIOR->14;default->0;};
    }
    public static void apply(Level world,BlockPos origin,int mask){
        for(var cell:SkatData.CELLS)if(cell.lightMask()!=0){var p=origin.offset(cell.pos());var s=world.getBlockState(p);
            if(s.hasProperty(SkatLampBlock.LIT)){boolean lit=(mask&cell.lightMask())!=0;if(s.getValue(SkatLampBlock.LIT)!=lit)world.setBlock(p,s.setValue(SkatLampBlock.LIT,lit),Block.UPDATE_ALL);}
        }
    }
    public static boolean panelHit(double x,double y,double z){return x>=-2.2&&x<=3.2&&y>=4.65&&y<=5.65&&z>=-6.25&&z<=-5.10;}
    private SkatLighting(){}
}
