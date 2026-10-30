package pro.erez.interstice.vehicle;

import java.util.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.ship.*;

/** Common, authoritative protection; constant-time cell/plot resolution. */
@EventBusSubscriber(modid=Interstice.ID)
public final class VehicleAccess {
    private static boolean available(){return net.neoforged.fml.ModList.get().isLoaded("sable")&&net.neoforged.fml.ModList.get().isLoaded("offroad");}
    private static final Map<Level,Map<UUID,BlockPos>> ANCHORS=new WeakHashMap<>();
    private static final Map<UUID,Long> PROMPTS=new HashMap<>();
    public static boolean part(BlockState s){if(!available())return false;var b=s.getBlock();return b instanceof SkatBlock||b instanceof SkatSeatBlock||b instanceof SkatEngineBlock||b instanceof SkatPropellerBlock||b instanceof EitanBlock||b instanceof EitanBlock.Control||b instanceof EitanWheelBlock;}
    public static VehicleSecurity security(BlockEntity e){return e instanceof EitanController c?c.security:e instanceof SkatHelmEntity h?h.security:null;}
    public static BlockEntity anchor(Level w,BlockPos p){
        if(!available()||!w.hasChunkAt(p))return null;var s=w.getBlockState(p);
        if(s.hasProperty(SkatData.CELL)&&part(s)){var o=p.subtract(SkatData.cell(s.getValue(SkatData.CELL)).pos());return w.getBlockEntity(o.offset(0,4,-6));}
        if(s.getBlock() instanceof EitanWheelBlock)return w.getBlockEntity(p.subtract(EitanData.MOUNTS.get(s.getValue(EitanWheelBlock.SLOT)).pos()).offset(EitanData.CONTROL));
        if(s.getBlock() instanceof EitanBlock.Control)return w.getBlockEntity(p);
        if(s.hasProperty(EitanBlock.CELL)&&part(s))return w.getBlockEntity(p.subtract(EitanData.CELLS.get(s.getValue(EitanBlock.CELL)).pos()).offset(EitanData.CONTROL));
        var container=SubLevelContainer.getContainer(w);if(container==null||!container.inBounds(p))return null;var plot=container.getPlot(new net.minecraft.world.level.ChunkPos(p));if(plot==null)return null;var key=ANCHORS.getOrDefault(w,Map.of()).get(plot.getSubLevel().getUniqueId());return key==null?null:w.getBlockEntity(key);
    }
    public static void register(BlockEntity e){if(e.getLevel()==null||e.getLevel().isClientSide)return;var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(e);if(body!=null)ANCHORS.computeIfAbsent(e.getLevel(),k->new HashMap<>()).put(body.getUniqueId(),e.getBlockPos());VehicleCommands.adoptLegacy(e);}
    public static boolean reach(Player p,BlockEntity e){if(e==null||e.isRemoved()||p.level()!=e.getLevel())return false;var v=e.getBlockPos().getCenter();var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(e);if(body!=null)v=body.logicalPose().transformPosition(v);return p.distanceToSqr(v)<32*32;}
    public static void changed(BlockEntity e){e.setChanged();if(e.getLevel()!=null&&!e.getLevel().isClientSide)e.getLevel().sendBlockUpdated(e.getBlockPos(),e.getBlockState(),e.getBlockState(),3);}
    public static boolean allowed(Player p,BlockEntity e){var s=security(e);return s!=null&&s.allowed(p);}
    public static boolean require(Player p,BlockEntity e){if(e==null||!reach(p,e))return false;var s=security(e);if(s==null)return false;if(s.owner()==null&&p.hasPermissions(4)){s.claim(p.getUUID());changed(e);}if(s.allowed(p))return true;if(p instanceof ServerPlayer server){long tick=p.level().getGameTime();if(PROMPTS.size()>256)PROMPTS.clear();if(tick>=PROMPTS.getOrDefault(p.getUUID(),-100L)+20){PROMPTS.put(p.getUUID(),tick);VehicleNetwork.open(server,e);}}return false;}
    public static boolean maintenance(Player p,BlockEntity e){var s=security(e);return s!=null&&s.maintenance(p,p.level().getGameTime());}
    public static float destroyProgress(BlockState state,Player p,net.minecraft.world.level.BlockGetter w,BlockPos pos){if(!(w instanceof Level level))return 0;var e=anchor(level,pos);return e==null?(p.hasPermissions(4)&&p.isShiftKeyDown()?1:0):maintenance(p,e)?1F/30:0;}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void breakBlock(BlockEvent.BreakEvent event){if(!(event.getLevel() instanceof Level w))return;var e=anchor(w,event.getPos());if((part(event.getState())||e!=null)&&!(e==null?event.getPlayer().hasPermissions(4)&&event.getPlayer().isShiftKeyDown():maintenance(event.getPlayer(),e))){event.setCanceled(true);event.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable("interstice.vehicle.protected"),true);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void use(PlayerInteractEvent.RightClickBlock event){var w=event.getLevel();var e=anchor(w,event.getPos());if(e==null)return;var p=event.getEntity();if(w.isClientSide)return;
        if(p.isShiftKeyDown()&&event.getItemStack().isEmpty()&&event.getPos().equals(e.getBlockPos())&&p instanceof ServerPlayer sp){VehicleNetwork.open(sp,e);event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);return;}
        if(!require(p,e)){event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);return;}
        if(!maintenance(p,e)&&!event.getItemStack().isEmpty()&&event.getItemStack().getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING)<=0)event.setUseItem(TriState.FALSE);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void place(BlockEvent.EntityPlaceEvent event){if(!(event.getLevel() instanceof Level w))return;var e=anchor(w,event.getPos());if(e!=null&&event.getEntity() instanceof Player p&&!maintenance(p,e))event.setCanceled(true);}
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event){event.getAffectedBlocks().removeIf(p->part(event.getLevel().getBlockState(p))||anchor(event.getLevel(),p)!=null);}
    private VehicleAccess(){}
}
