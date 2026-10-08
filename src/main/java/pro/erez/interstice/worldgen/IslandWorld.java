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
    public static final ResourceKey<Level> DRAFT_WORLD=ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath(Interstice.ID,"islands_v2"));
    public static final ResourceKey<Level> PREVIOUS_WORLD=ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath(Interstice.ID,"islands_v3"));
    public static final ResourceKey<Level> LIVING_WORLD=ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath(Interstice.ID,"islands_v4"));
    private IslandWorld() {}
    public static boolean isIsland(ResourceKey<Level> dimension) {
        return dimension.equals(WORLD) || dimension.equals(TALL_WORLD)||dimension.equals(DRAFT_WORLD)||dimension.equals(PREVIOUS_WORLD)||dimension.equals(LIVING_WORLD);
    }
    public static BlockPos findLanding(ServerLevel world) {
        if(!(world.getChunkSource().getGenerator() instanceof IslandChunkGenerator generator)) throw new IllegalStateException("Island generator unavailable");
        var random=world.getChunkSource().randomState();
        if(generator.isLivingRealm())return findLivingLanding(world,generator,random);
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
                if(isSafeLandingPlatform(world,floor)) return floor.above();
                break;
            }
        }
        throw new IllegalStateException("No safe island landing within 96 blocks");
    }
    private static BlockPos findLivingLanding(ServerLevel world,IslandChunkGenerator generator,net.minecraft.world.level.levelgen.RandomState random){
        // Large sparse islands need a wider search; prefer dry low terraces over exposed peaks.
        int generated=0;
        for(int pass=0;pass<2;pass++)for(int radius=0;radius<=48;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            int x=dx*16+7,z=dz*16+7;
            var terrain=generator.terrainColumn(random,x,z);
            int minimum=featureMinimumFor(world,generator);
            int top=-1;for(int y=generator.geometry().maxLand();y>=minimum;y--)if(terrain.density(y)>0){top=y;break;}
            if(top<0||pass==0&&top>130)continue;
            var column=generator.getBaseColumn(x,z,world,random);
            for(int y=top;y>=minimum;y--){
                if(column.getBlock(y).isAir()||!column.getBlock(y).getFluidState().isEmpty())continue;
                if(!column.getBlock(y+1).isAir()||!column.getBlock(y+2).isAir())break;
                if(++generated>64)throw new IllegalStateException("No safe clearing among 64 terrain candidates");
                world.getChunk(x>>4,z>>4);var floor=new BlockPos(x,y,z);
                if(isSafeLandingPlatform(world,floor)&&hasDryExit(world,floor))return floor.above();
                break;
            }
        }
        throw new IllegalStateException("No safe realm landing within 768 blocks");
    }
    private static int featureMinimumFor(ServerLevel world,IslandChunkGenerator generator){return IslandChunkGenerator.featureMinimum(world,generator.geometry());}
    private static boolean hasDryExit(ServerLevel world,BlockPos floor){
        for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL){
            var p=floor.relative(direction,3);
            for(int dy=-2;dy<=2;dy++)if(pro.erez.interstice.rift.RiftSafety.standing(world,p.offset(0,dy+1,0)))return true;
        }
        return false;
    }
    public static boolean isSafeLandingPlatform(ServerLevel world,BlockPos floor) {
        var baseState=world.getBlockState(floor);
        if(baseState.getCollisionShape(world,floor).isEmpty() || !baseState.getFluidState().isEmpty()
                || baseState.is(net.minecraft.world.level.block.Blocks.BEDROCK)) return false;
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
            BlockPos p=floor.offset(dx,0,dz);
            var state=world.getBlockState(p);
            var below=world.getBlockState(p.below());
            boolean solid=!state.getCollisionShape(world,p).isEmpty() && state.getFluidState().isEmpty()
                    && !state.is(net.minecraft.world.level.block.Blocks.BEDROCK);
            boolean stepDown=!below.getCollisionShape(world,p.below()).isEmpty() && below.getFluidState().isEmpty()
                    && !below.is(net.minecraft.world.level.block.Blocks.BEDROCK) && state.isAir();
            if(!solid && !stepDown) return false;
            int groundY=solid ? p.getY() : p.getY()-1;
            for(int hy=1;hy<=3;hy++) {
                BlockPos head=new BlockPos(p.getX(),groundY+hy,p.getZ());
                if(!world.getBlockState(head).isAir()) return false;
            }
        }
        return true;
    }
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("interstice").requires(source->source.hasPermission(2))
                .then(Commands.literal("explore")
                        .executes(context->enter(context.getSource(),LIVING_WORLD))
                        .then(Commands.literal("living").executes(context->enter(context.getSource(),LIVING_WORLD)))
                        .then(Commands.literal("draft").executes(context->enter(context.getSource(),DRAFT_WORLD)))
                        .then(Commands.literal("previous").executes(context->enter(context.getSource(),PREVIOUS_WORLD)))
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
        source.sendSuccess(()->Component.literal("Междуморье. /interstice leave возвращает в прежнюю точку."),false);
        return 1;
    }
}
