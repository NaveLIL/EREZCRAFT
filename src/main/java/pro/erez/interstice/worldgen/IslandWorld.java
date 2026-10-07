package pro.erez.interstice.worldgen;

import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.FluidLab;

public final class IslandWorld {
    public static final ResourceKey<Level> WORLD=ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath(Interstice.ID,"islands"));
    public static final ResourceKey<Level> TALL_WORLD=ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath(Interstice.ID,"islands_tall"));
    private IslandWorld() {}
    public static boolean isIsland(ResourceKey<Level> dimension) {
        return dimension.equals(WORLD) || dimension.equals(TALL_WORLD);
    }
    public static BlockPos findLanding(ServerLevel world) {
        if(!(world.getChunkSource().getGenerator() instanceof IslandChunkGenerator generator)) throw new IllegalStateException("Island generator unavailable");
        var random=world.getChunkSource().randomState();
        // Inspect density columns first; generate only the selected landing chunk.
        for(int radius=0;radius<=24;radius++) for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
            int x=dx*4,z=dz*4;
            NoiseColumn column=generator.getBaseColumn(x,z,world,random);
            for(int y=generator.geometry().maxLand();y>=generator.geometry().minLand();y--) {
                if(column.getBlock(y).isAir() || !column.getBlock(y).getFluidState().isEmpty()) continue;
                if(!column.getBlock(y+1).isAir() || !column.getBlock(y+2).isAir()) break;
                world.getChunk(x>>4,z>>4);
                BlockPos floor=new BlockPos(x,y,z);
                if(!world.getBlockState(floor).getCollisionShape(world,floor).isEmpty()
                        && world.getBlockState(floor.above()).isAir() && world.getBlockState(floor.above(2)).isAir()) return floor.above();
                break;
            }
        }
        throw new IllegalStateException("No safe island landing within 96 blocks");
    }
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("interstice").requires(source->source.hasPermission(2))
                .then(Commands.literal("explore")
                        .executes(context->enter(context.getSource(),TALL_WORLD))
                        .then(Commands.literal("tall").executes(context->enter(context.getSource(),TALL_WORLD)))
                        .then(Commands.literal("legacy").executes(context->enter(context.getSource(),WORLD)))));
    }
    private static int enter(CommandSourceStack source,ResourceKey<Level> dimension)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=source.getPlayerOrException();
        ServerLevel world=player.server.getLevel(dimension);
        if(world==null) {source.sendFailure(Component.literal("Island dimension unavailable. Create a new world with this mod version installed."));return 0;}
        BlockPos landing;
        try {landing=findLanding(world);} catch(IllegalStateException exception) {source.sendFailure(Component.literal(exception.getMessage()));return 0;}
        FluidLab.rememberReturn(player);
        player.teleportTo(world,landing.getX()+0.5,landing.getY()+0.05,landing.getZ()+0.5,java.util.Set.of(),0,0);
        source.sendSuccess(()->Component.literal("Seeded island world ("+world.getHeight()+" blocks high). /interstice leave returns to your previous location."),false);
        return 1;
    }
}
