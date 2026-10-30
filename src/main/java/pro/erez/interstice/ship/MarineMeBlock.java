package pro.erez.interstice.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Authored cell shape/appearance retained around real AE2 functional entities. */
public final class MarineMeBlock extends MarineBlock implements EntityBlock {
    public enum Kind {TERMINAL,DRIVE,GENERATOR}
    private final Kind kind;
    public MarineMeBlock(Kind kind){this.kind=kind;registerDefaultState(defaultBlockState().setValue(POSE,0));}
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(POSE);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return switch(kind){case TERMINAL->new MarineTerminalEntity(p,s);case DRIVE->new MarineDriveEntity(p,s);case GENERATOR->new MarineGeneratorEntity(p,s);};}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level w,BlockPos p,Player player,BlockHitResult hit){
        var cell=MarineData.cell(s.getValue(MarineData.CELL));var origin=p.subtract(cell.pos());var at=hit.getLocation().subtract(origin.getX(),origin.getY(),origin.getZ());
        if(MarineMe.openScreen(w,origin,at,player))return InteractionResult.sidedSuccess(w.isClientSide);
        if(!cell.mechanisms().isEmpty()&&!MarineMe.screenHit(cell.pos(),at))return super.useWithoutItem(s,w,p,player,hit);
        if(w.isClientSide)return InteractionResult.SUCCESS;
        var entity=w.getBlockEntity(p);
        if(entity instanceof MarineTerminalEntity t)t.open(player);
        else if(entity instanceof MarineDriveEntity d){if(player.isShiftKeyDown())d.openMenu(player);else d.openTerminal(player);}
        else if(entity instanceof MarineGeneratorEntity g){if(player.isShiftKeyDown())g.enabled(!g.enabled());player.displayClientMessage(net.minecraft.network.chat.Component.literal("NE01 · "+(g.enabled()?"ВКЛ":"ВЫКЛ")+" · "+(int)g.getAECurrentPower()+" AE · топливо "+g.getInternalInventory().getStackInSlot(0).getCount()),true);}
        return InteractionResult.CONSUME;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level w,BlockState s,BlockEntityType<T> type){return w.isClientSide||kind!=Kind.GENERATOR?null:(level,pos,state,entity)->{if(entity instanceof MarineGeneratorEntity generator)generator.process();};}
    @Override protected void onRemove(BlockState s,Level w,BlockPos p,BlockState next,boolean moving){
        if(!moving&&!s.is(next.getBlock())&&w.getBlockEntity(p) instanceof appeng.blockentity.AEBaseBlockEntity entity){var drops=new java.util.ArrayList<net.minecraft.world.item.ItemStack>();entity.addAdditionalDrops(w,p,drops);for(var stack:drops)Block.popResource(w,p,stack);entity.clearContent();}
        super.onRemove(s,w,p,next,moving);
    }
}
