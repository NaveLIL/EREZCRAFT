package pro.erez.interstice.worldgen;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import pro.erez.interstice.Interstice;

public final class IslandWorld {
    public static final ResourceKey<Level> WORLD=ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath(Interstice.ID,"islands"));
    private IslandWorld() {}
    public static BlockPos findLanding(ServerLevel world) {
        if(!(world.getChunkSource().getGenerator() instanceof IslandChunkGenerator generator)) throw new IllegalStateException("Island generator unavailable");
        var random=world.getChunkSource().randomState();
        // Inspect density columns first; generate only the selected landing chunk.
        for(int radius=0;radius<=24;radius++) for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
            int x=dx*4,z=dz*4;
            NoiseColumn column=generator.getBaseColumn(x,z,world,random);
            for(int y=IslandChunkGenerator.MAX_LAND;y>=IslandChunkGenerator.MIN_LAND;y--) {
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
                .then(Commands.literal("explore").executes(context->{
                    var player=context.getSource().getPlayerOrException();
                    ServerLevel world=player.server.getLevel(WORLD);
                    if(world==null) {context.getSource().sendFailure(Component.literal("Island dimension unavailable. Reopen the world with this mod version installed."));return 0;}
                    BlockPos landing;
                    try {landing=findLanding(world);} catch(IllegalStateException exception) {context.getSource().sendFailure(Component.literal(exception.getMessage()));return 0;}
                    if(!player.level().dimension().equals(WORLD)) {
                        CompoundTag point=new CompoundTag();point.putString("dimension",player.level().dimension().location().toString());
                        point.putDouble("x",player.getX());point.putDouble("y",player.getY());point.putDouble("z",player.getZ());
                        point.putFloat("yaw",player.getYRot());point.putFloat("pitch",player.getXRot());
                        player.getPersistentData().put("interstice:return",point);
                    }
                    player.teleportTo(world,landing.getX()+0.5,landing.getY()+0.05,landing.getZ()+0.5,java.util.Set.of(),0,0);
                    context.getSource().sendSuccess(()->Component.literal("Seeded island world. /interstice leave returns to your previous location."),false);
                    return 1;
                })));
    }
}
