package pro.erez.interstice.vehicle;
import java.util.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.ship.*;
/** Administrative operations are explicit, bounded to loaded plots, and never carry a PIN. */
@EventBusSubscriber(modid=Interstice.ID)
public final class VehicleCommands {
    public static final class Owners extends SavedData {
        UUID legacyOwner;
        static Owners load(CompoundTag t,HolderLookup.Provider p){var o=new Owners();if(t.hasUUID("LegacyOwner"))o.legacyOwner=t.getUUID("LegacyOwner");return o;}
        @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider p){if(legacyOwner!=null)t.putUUID("LegacyOwner",legacyOwner);return t;}
    }
    private static Owners data(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Owners::new,Owners::load),"interstice_vehicle_ownership");}
    public static void adoptLegacy(BlockEntity e){var s=VehicleAccess.security(e);if(s!=null&&s.owner()==null&&e.getLevel() instanceof ServerLevel w){var id=data(w.getServer()).legacyOwner;if(id!=null){s.claim(id);VehicleAccess.changed(e);}}}
    public static List<BlockEntity> loaded(MinecraftServer server){var out=new ArrayList<BlockEntity>();for(var world:server.getAllLevels()){var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(world);if(container==null)continue;for(var body:container.getAllSubLevels()){var b=body.getPlot().getBoundingBox();long volume=(long)(b.maxX()-b.minX()+1)*(b.maxY()-b.minY()+1)*(b.maxZ()-b.minZ()+1);if(volume>100000)continue;for(int x=b.minX();x<=b.maxX();x++)for(int y=b.minY();y<=b.maxY();y++)for(int z=b.minZ();z<=b.maxZ();z++){var e=world.getBlockEntity(new BlockPos(x,y,z));if(VehicleAccess.security(e)!=null)out.add(e);}}}return out;}
    public static String snapshot(MinecraftServer server){var list=new com.google.gson.JsonArray();for(var e:loaded(server)){var o=new com.google.gson.JsonObject();var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(e);var at=body.logicalPose().transformPosition(e.getBlockPos().getCenter());var s=VehicleAccess.security(e);o.addProperty("body",body.getUniqueId().toString());o.addProperty("type",e instanceof EitanController?"eitan":"skat");o.addProperty("dimension",e.getLevel().dimension().location().toString());o.addProperty("anchor",e.getBlockPos().toShortString());o.addProperty("x",at.x);o.addProperty("y",at.y);o.addProperty("z",at.z);o.addProperty("owner",s.owner()==null?"":s.owner().toString());o.addProperty("pin_configured",s.configured());if(e instanceof EitanController c){o.addProperty("fuel",c.fuelTicks);o.addProperty("occupants",Integer.bitCount(c.occupiedMask));o.addProperty("speed",c.speed);}list.add(o);}return list.toString();}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){if(!net.neoforged.fml.ModList.get().isLoaded("sable"))return;event.getDispatcher().register(Commands.literal("erezvehicle")
        .then(Commands.literal("access").executes(c->{var p=c.getSource().getPlayerOrException();BlockEntity e=null;if(p.getVehicle() instanceof EitanSeatEntity s)e=s.controller();else if(p.getVehicle() instanceof SkatSeatEntity s){var hit=p.pick(12,1,false);if(hit instanceof net.minecraft.world.phys.BlockHitResult b)e=VehicleAccess.anchor(p.level(),b.getBlockPos());}else {var hit=p.pick(8,1,false);if(hit instanceof net.minecraft.world.phys.BlockHitResult b)e=VehicleAccess.anchor(p.level(),b.getBlockPos());}if(e==null)return 0;VehicleNetwork.open(p,e);return 1;}))
        .then(Commands.literal("admin").requires(s->s.hasPermission(4))
            .then(Commands.literal("list").executes(c->{c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal(snapshot(c.getSource().getServer())),false);return 1;}))
            .then(Commands.literal("adopt").then(Commands.argument("owner",UuidArgument.uuid()).executes(c->{var d=data(c.getSource().getServer());d.legacyOwner=UuidArgument.getUuid(c,"owner");d.setDirty();for(var e:loaded(c.getSource().getServer()))VehicleAccess.register(e);c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("Legacy vehicle ownership assigned; existing owners preserved"),false);return 1;})))
            .then(Commands.literal("profile").then(Commands.argument("seconds",IntegerArgumentType.integer(5,120)).executes(c->{VehicleMetrics.start(IntegerArgumentType.getInteger(c,"seconds"));return 1;})))
            .then(Commands.literal("stats").executes(c->{c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal(VehicleMetrics.report()),false);return 1;}))
            .then(Commands.literal("spawn").then(Commands.literal("eitan").then(Commands.argument("at",BlockPosArgument.blockPos()).executes(c->{EitanAssembly.create(c.getSource().getLevel(),BlockPosArgument.getBlockPos(c,"at"),data(c.getSource().getServer()).legacyOwner);return 1;}))).then(Commands.literal("skat").then(Commands.argument("at",BlockPosArgument.blockPos()).executes(c->{SkatAssembly.create(c.getSource().getLevel(),BlockPosArgument.getBlockPos(c,"at"),data(c.getSource().getServer()).legacyOwner);return 1;}))))));}
    private VehicleCommands(){}
}
