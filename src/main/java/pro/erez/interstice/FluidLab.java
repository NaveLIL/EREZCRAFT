package pro.erez.interstice;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** A finite, isolated fluid lab. Never edits the player's normal world. */
public final class FluidLab {
    public static final ResourceKey<Level> WORLD = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Interstice.ID, "chaotic_lab"));
    private static final ResourceKey<Level> RELIEF_WORLD = ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath(Interstice.ID,"relief_lab"));
    private static final ResourceKey<Level> LEGACY_WORLD = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Interstice.ID, "fluid_lab"));
    private FluidLab() {}
    public static boolean isIntersticeWorld(ResourceKey<Level> dimension) {
        return dimension.equals(WORLD) || dimension.equals(LEGACY_WORLD) || dimension.equals(RELIEF_WORLD)
                || pro.erez.interstice.worldgen.IslandWorld.isIsland(dimension);
    }
    /** Internal transfers preserve the location where the expedition originally began. */
    public static void rememberReturn(ServerPlayer player) {
        if (isIntersticeWorld(player.level().dimension())) return;
        CompoundTag point = new CompoundTag();
        point.putString("dimension", player.level().dimension().location().toString());
        point.putDouble("x", player.getX()); point.putDouble("y", player.getY()); point.putDouble("z", player.getZ());
        point.putFloat("yaw", player.getYRot()); point.putFloat("pitch", player.getXRot());
        player.getPersistentData().put("interstice:return", point);
    }
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("interstice").requires(source -> source.hasPermission(2))
                .then(Commands.literal("lab").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ServerLevel lab = player.server.getLevel(WORLD);
                    if (lab == null) { context.getSource().sendFailure(Component.literal("Fluid lab dimension unavailable. Create/reopen the world with the mod installed.")); return 0; }
                    prepare(lab);
                    rememberReturn(player);
                    player.teleportTo(lab, 0.5, 65, 0.5, java.util.Set.of(), 0, 0);
                    context.getSource().sendSuccess(() -> Component.literal("Fluid lab: light sea above, heavy sea below. /interstice leave to return. Buckets are in the Interstice creative tab."), false);
                    return 1;
                }))
                .then(Commands.literal("leave").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!isIntersticeWorld(player.level().dimension())) return 0;
                    CompoundTag point = player.getPersistentData().getCompound("interstice:return");
                    ResourceLocation location = ResourceLocation.tryParse(point.getString("dimension"));
                    ServerLevel destination = location == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
                    if (destination == null) {
                        destination = player.server.overworld();
                        BlockPos spawn = destination.getSharedSpawnPos();
                        player.teleportTo(destination, spawn.getX()+0.5, spawn.getY()+1, spawn.getZ()+0.5, java.util.Set.of(), 0, 0);
                    } else {
                        player.teleportTo(destination, point.getDouble("x"), point.getDouble("y"), point.getDouble("z"), java.util.Set.of(), point.getFloat("yaw"), point.getFloat("pitch"));
                    }
                    player.getPersistentData().remove("interstice:return");
                    return 1;
                })));
    }
    private static void prepare(ServerLevel world) {
        LabState saved = world.getDataStorage().computeIfAbsent(new SavedData.Factory<>(LabState::new, LabState::load, DataFixTypes.LEVEL), "interstice_fluid_lab");
        if (saved.built) return;
        for (int x=-17; x<=17; x++) for (int z=-17; z<=17; z++) {
            world.setBlock(new BlockPos(x,31,z), Blocks.BARRIER.defaultBlockState(), 2);
            world.setBlock(new BlockPos(x,97,z), Blocks.BARRIER.defaultBlockState(), 2);
            if (Math.abs(x)==17 || Math.abs(z)==17) {
                for (int y=32;y<97;y++) world.setBlock(new BlockPos(x,y,z), Blocks.BARRIER.defaultBlockState(), 2);
            } else {
                for (int y=32;y<=34;y++) world.setBlock(new BlockPos(x,y,z), Interstice.HEAVY_BLOCK.get().defaultBlockState(), 2);
                SeaSurface.fillColumn(world, x, z);
            }
        }
        island(world,0,64,0,7);
        island(world,11,73,7,4);
        island(world,-10,56,-9,4);
        // Shelter for the future tide experiment.
        for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) world.setBlock(new BlockPos(x,69,z), Blocks.DEEPSLATE_BRICKS.defaultBlockState(),2);
        // Small suspended container for bucket experiments: solid ceiling, open below.
        for (int x=5;x<=8;x++) for (int z=-3;z<=0;z++) world.setBlock(new BlockPos(x,72,z),Blocks.GLASS.defaultBlockState(),2);
        saved.built = true; saved.setDirty();
    }
    private static void island(ServerLevel world,int cx,int top,int cz,int radius) {
        for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) {
            double distance=Math.sqrt(x*x+z*z);
            if(distance>radius) continue;
            int depth=Math.max(1,(int)(radius-distance)+1);
            for(int y=top-depth;y<top;y++) world.setBlock(new BlockPos(cx+x,y,cz+z),Blocks.STONE.defaultBlockState(),2);
            world.setBlock(new BlockPos(cx+x,top,cz+z),Blocks.MOSS_BLOCK.defaultBlockState(),2);
        }
    }
    private static final class LabState extends SavedData {
        boolean built;
        static LabState load(CompoundTag tag, HolderLookup.Provider registries) { LabState data=new LabState();data.built=tag.getBoolean("built");return data; }
        @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) { tag.putBoolean("built",built);return tag; }
    }
}
