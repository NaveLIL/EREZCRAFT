package pro.erez.interstice.ship;

import appeng.api.networking.*;
import appeng.me.helpers.IGridConnectedBlockEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Internal fixed wiring loom; actual AE2 nodes/channels/storage, no shadow inventory. */
public final class MarineMe {
    public static final BlockPos CONTROLLER=new BlockPos(-3,2,-1),GENERATOR=new BlockPos(-3,3,-1),SECOND_DRIVE=new BlockPos(-2,3,-1);
    public static final java.util.List<BlockPos> TERMINALS=MarineData.metadata("terminals").asList().stream().map(v->{var p=v.getAsJsonObject().getAsJsonArray("anchor");return new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());}).toList();
    public static final double[][] SCREENS=MarineData.metadata("terminals").asList().stream().map(v->{var p=v.getAsJsonObject().getAsJsonArray("screen");return new double[]{p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble()};}).toArray(double[][]::new);
    public static boolean screenHit(BlockPos cell,net.minecraft.world.phys.Vec3 p){int i=TERMINALS.indexOf(cell);double[] c=i<0?new double[]{-1.45,3.3,-.8}:SCREENS[i];return Math.abs(p.x-c[0])<.2&&Math.abs(p.y-c[1])<.36&&Math.abs(p.z-c[2])<.43;}
    public static int screenAt(net.minecraft.world.phys.Vec3 p){for(int i=0;i<SCREENS.length;i++){var c=SCREENS[i];if(Math.abs(p.x-c[0])<.2&&Math.abs(p.y-c[1])<.36&&Math.abs(p.z-c[2])<.43)return i;}return -1;}
    public static boolean openScreen(Level w,BlockPos origin,net.minecraft.world.phys.Vec3 hit,net.minecraft.world.entity.player.Player player){
        int index=screenAt(hit);if(index<0)return false;var entity=w.getBlockEntity(origin.offset(TERMINALS.get(index)));
        if(entity instanceof MarineTerminalEntity t){if(!w.isClientSide)t.open(player);return true;}
        if(entity instanceof MarineDriveEntity d){if(!w.isClientSide){if(player.isShiftKeyDown())d.openMenu(player);else d.openTerminal(player);}return true;}
        return false;
    }
    public static Item item(String name){return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2",name));}
    public static void connect(Level w,BlockPos origin,IGridNode core){
        if(core==null)return;var targets=new java.util.ArrayList<>(TERMINALS);targets.add(SECOND_DRIVE);targets.add(CONTROLLER);
        for(var offset:targets){var entity=w.getBlockEntity(origin.offset(offset));IGridNode node=null;
            if(entity instanceof IGridConnectedBlockEntity e)node=e.getMainNode().getNode();
            if(node==null||node==core)continue;
            IGridNode target=node;boolean exists=core.getConnections().stream().anyMatch(c->c.getOtherSide(core)==target);
            if(!exists)GridHelper.createConnection(core,target);
        }
    }
    private MarineMe(){}
}
