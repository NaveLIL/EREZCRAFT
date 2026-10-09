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
    public static final ResourceKey<Level> VANILLA_WORLD=ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath(Interstice.ID,"islands_v5"));
    public static final ResourceKey<Level> CURRENT_WORLD=VANILLA_WORLD;
    private IslandWorld() {}
    public static boolean isIsland(ResourceKey<Level> dimension) {
        return dimension.equals(WORLD) || dimension.equals(TALL_WORLD)||dimension.equals(DRAFT_WORLD)||dimension.equals(PREVIOUS_WORLD)||dimension.equals(LIVING_WORLD)||dimension.equals(VANILLA_WORLD);
    }
    public static BlockPos findLanding(ServerLevel world) {
        if(!(world.getChunkSource().getGenerator() instanceof IslandChunkGenerator generator)) throw new IllegalStateException("Island generator unavailable");
        var random=world.getChunkSource().randomState();
        if(generator.terrainRevision()==5)return findVanillaLanding(world,generator,random);
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
    private static BlockPos findVanillaLanding(ServerLevel world,IslandChunkGenerator generator,net.minecraft.world.level.levelgen.RandomState random){
        // A forest or an existing echo may occupy a chunk's centre. Inspect its actual interior,
        // including coastal veneers, before spending another FULL-chunk generation allowance.
        var inspected=new java.util.HashSet<Long>();
        int generated=0,minimum=generator.geometry().lowerSeaTop()+1;
        for(int pass=0;pass<2;pass++)for(int radius=0;radius<=48;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            long key=net.minecraft.world.level.ChunkPos.asLong(dx,dz);
            int ceiling=pass==0?Math.min(130,generator.geometry().maxLand()):generator.geometry().maxLand();
            if(!inspected.contains(key)){
                if(!rawVanillaClearing(world,generator,random,dx,dz,minimum,ceiling))continue;
                if(!world.hasChunk(dx,dz)&&++generated>64)throw new IllegalStateException("No safe V5 clearing among 64 generated terrain chunks");
                world.getChunk(dx,dz);
                inspected.add(key);
            }
            // The 3x3 echo stays inside this loaded chunk; no neighbour load or terrain removal.
            for(int localX=3;localX<=12;localX++)for(int localZ=3;localZ<=12;localZ++){
                int x=dx*16+localX,z=dz*16+localZ;
                var column=generator.getBaseColumn(x,z,world,random);
                int expected=rawDryTop(generator,column,minimum);
                if(expected<minimum||expected>ceiling)continue;
                // Veneers and settled coast sand may change the surface by a block. An old echo's
                // roof or a player's tower is not a new natural landing terrace.
                for(int y=Math.min(ceiling,expected+1);y>=Math.max(minimum,expected-2);y--){
                    var floor=new BlockPos(x,y,z);var state=world.getBlockState(floor);
                    if(!StoneVaults.isGround(state)&&!state.is(pro.erez.interstice.minerals.MineralEcology.TOXIC_SAND.get()))continue;
                    if(!world.getBlockState(floor.above()).isAir())continue;
                    if(isSafeLandingPlatform(world,floor)&&hasDryExit(world,floor)
                            &&pro.erez.interstice.rift.RiftSafety.canPrepareEcho(world,floor.above()))return floor.above();
                    // A roof or a dangerous surface cannot be bypassed by selecting the same column underground.
                    break;
                }
            }
        }
        throw new IllegalStateException("No safe V5 realm landing within 768 blocks");
    }
    private static boolean rawVanillaClearing(ServerLevel world,IslandChunkGenerator generator,net.minecraft.world.level.levelgen.RandomState random,int chunkX,int chunkZ,int minimum,int ceiling){
        // Numeric 3x3 slope/headroom checks reject steep ridges and sea before requesting FULL terrain.
        for(int localX:new int[]{4,11})for(int localZ:new int[]{4,11}){
            int x=chunkX*16+localX,z=chunkZ*16+localZ;
            var centre=generator.getBaseColumn(x,z,world,random);int top=rawDryTop(generator,centre,minimum);
            if(top<minimum||top>ceiling)continue;
            boolean safe=true;
            for(int ox=-1;ox<=1&&safe;ox++)for(int oz=-1;oz<=1&&safe;oz++){
                var column=ox==0&&oz==0?centre:generator.getBaseColumn(x+ox,z+oz,world,random);
                var floor=column.getBlock(top);
                boolean supported=!floor.isAir()&&floor.getFluidState().isEmpty()
                        ||floor.isAir()&&!column.getBlock(top-1).isAir()&&column.getBlock(top-1).getFluidState().isEmpty();
                if(!supported){safe=false;break;}
                for(int head=1;head<=3;head++)if(!column.getBlock(top+head).isAir()){safe=false;break;}
            }
            if(safe)return true;
        }
        return false;
    }
    private static int rawDryTop(IslandChunkGenerator generator,NoiseColumn column,int minimum){
        for(int y=generator.geometry().maxLand();y>=minimum;y--)if(!column.getBlock(y).isAir()&&column.getBlock(y).getFluidState().isEmpty())return y;
        return -1;
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
                        .executes(context->enter(context.getSource(),CURRENT_WORLD))
                        .then(Commands.literal("living").executes(context->enter(context.getSource(),CURRENT_WORLD)))
                        .then(Commands.literal("vanilla").executes(context->enter(context.getSource(),CURRENT_WORLD)))
                        .then(Commands.literal("v4").executes(context->enter(context.getSource(),LIVING_WORLD)))
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
