package pro.erez.interstice.client;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import pro.erez.interstice.FluidContact;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.OceanLiquidBlock;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.geometry.GeometryProfiles;

/** Bounded, fresh client world: real configuration, render-region lifetime and scheduled ocean joining. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class GeometryVisualSmoke {
    private static final String[] ROUTE={"islands","geometry_probe","islands_tall","islands"};
    private static final int[] OFFSETS={0,8,128,0};
    private static final int[] HEIGHTS={128,128,256,128};
    private static final List<Map<String,Object>> observations=new ArrayList<>();
    private static long began;
    private static boolean started,finished;
    private static int stage,route,ticks;
    private static RenderChunkRegion retained;
    private static GeometryProfile retainedProfile;
    private static int retainedOffset;
    private static CompletableFuture<Void> serverWork;
    private static long serverStartTick;
    private static Map<BlockPos,BlockState> held;
    private static BlockPos source;

    private static void require(boolean condition,String message) {
        if(!condition) throw new IllegalStateException(message);
    }
    private static ResourceKey<Level> key(String name) {
        return ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath(Interstice.ID,name));
    }
    private static GeometryProfile expected() {
        return new GeometryProfile(1,0,HEIGHTS[route],34,94+OFFSETS[route],6);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("interstice.geometrySmoke") || finished) return;
        Minecraft mc=Minecraft.getInstance();
        if(began==0) began=System.nanoTime();
        try {
            require(System.nanoTime()-began<900_000_000_000L,"Initialization or client operation timed out");
            if(!started && mc.screen instanceof TitleScreen) {
                started=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);
                mc.options.framerateLimit().set(60);mc.options.hideGui=true;
                String name="geometry-check-"+System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(name,new LevelSettings("Geometry check",GameType.CREATIVE,
                        false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20261006,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
                return;
            }
            if(mc.player==null || mc.level==null || mc.getConnection()==null || mc.screen!=null) return;
            if(stage==0) { travel(mc);stage=1;ticks=0;return; }
            if(!mc.level.dimension().equals(key(ROUTE[route]))) return;
            ticks++;
            if(stage==1 && ticks>=100) {
                require(GeometryProfiles.get(mc.level).equals(expected()),"ClientLevel did not receive its profile");
                require(mc.level.getHeight()==expected().height(),"Client world bounds mismatch");
                BlockPos corner=new BlockPos(0,(int)Math.floor(0x1.5c6cbbffb01e1p6+OFFSETS[route]),0);
                if(!(mc.level.getBlockState(corner).getBlock() instanceof OceanLiquidBlock)) return;
                RenderChunkRegion region=new RenderRegionCache().createRegion(mc.level,SectionPos.of(corner),false);
                require(region!=null && GeometryProfiles.get(region).equals(expected()),"Render region profile mismatch");
                int vertices=checkRender(region,OFFSETS[route]);
                checkContact(mc,OFFSETS[route]);
                if(retained!=null) {
                    require(GeometryProfiles.get(retained).equals(retainedProfile),"Old region rebound to current world");
                    checkRender(retained,retainedOffset);
                }
                retained=region;retainedProfile=expected();retainedOffset=OFFSETS[route];
                observations.add(Map.of("dimension",ROUTE[route],"height",mc.level.getHeight(),
                        "offset",OFFSETS[route],"rendered_vertices",vertices,"contact_and_bucket",true));
                capture(mc,route+"-"+ROUTE[route]+"-surface.png");
                if(route==1 || route==2) { prepareStream(mc);stage=2;ticks=0; }
                else next(mc);
            } else if(stage==2 && serverWork.isDone()) {
                serverWork.join();
                var server=mc.getSingleplayerServer();
                if(server.getTickCount()-serverStartTick<160) return;
                serverWork=server.submit(()->{
                    var world=server.getLevel(key(ROUTE[route]));
                    held.forEach((p,s)->require(world.getBlockState(p).equals(s),"Generated sea was overwritten at "+p));
                    require(world.getBlockState(source.above(2)).is(Interstice.LIGHT_BLOCK.get()),"Stream failed to rise");
                    for(int x=3;x<=11;x++) for(int z=5;z<=13;z++) for(int y=source.getY()+2;y<=expected().upperMaximum();y++) {
                        if(x==7 && z==9) continue;
                        require(!world.getBlockState(new BlockPos(x,y,z)).is(Interstice.LIGHT_BLOCK.get()),"Secondary layer in generated ocean");
                    }
                    world.setBlock(source,Blocks.AIR.defaultBlockState(),3);
                    serverStartTick=server.getTickCount();
                });
                capture(mc,ROUTE[route]+"-stream-joined-generated-ocean.png");stage=3;
            } else if(stage==3 && serverWork.isDone()) {
                serverWork.join();
                var server=mc.getSingleplayerServer();
                if(server.getTickCount()-serverStartTick<120) return;
                serverWork=server.submit(()->{
                    var world=server.getLevel(key(ROUTE[route]));
                    for(int y=source.getY();y<source.getY()+4;y++)
                        require(world.getFluidState(new BlockPos(7,y,9)).isEmpty(),"Generated sea sustained orphaned stream");
                    held.forEach((p,s)->require(world.getBlockState(p).equals(s),"Drainage changed generated sea"));
                });stage=4;
            } else if(stage==4 && serverWork.isDone()) {
                serverWork.join();observations.add(Map.of("dimension",ROUTE[route],"generated_ocean_join_and_drain",true));next(mc);
            }
        } catch(Throwable error) { error.printStackTrace();report(mc,false,error.toString()); }
    }
    private static void travel(Minecraft mc) {
        double y=0x1.6a424fd30a82fp6+OFFSETS[route]-7;
        mc.getConnection().sendCommand("execute in interstice:"+ROUTE[route]+" run tp @s 7.375 "+y+" 9.625 180 -40");
        var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
        server.execute(()->{var p=server.getPlayerList().getPlayer(uuid);p.getAbilities().flying=true;p.onUpdateAbilities();});
    }
    private static void next(Minecraft mc) {
        if(++route==ROUTE.length) { report(mc,true,"completed");return; }
        travel(mc);stage=1;ticks=0;
    }
    private static int checkRender(RenderChunkRegion region,int offset) {
        double golden=0x1.5c6cbbffb01e1p6+offset;
        BlockPos p=new BlockPos(0,(int)Math.floor(golden),0);
        Recorder recorder=new Recorder();var state=region.getBlockState(p);
        require(state.getBlock() instanceof OceanLiquidBlock,"Missing held sea in retained region");
        require(IClientFluidTypeExtensions.of(state.getFluidState()).renderFluid(state.getFluidState(),region,p,recorder,state),"Custom renderer was not called");
        require(!recorder.vertices.isEmpty(),"Renderer emitted no vertices");
        boolean found=false;
        for(Vec3 v:recorder.vertices) if(v.x==0 && v.z==0 && Math.abs(v.y+(p.getY()&~15)-golden)<1e-5) found=true;
        require(found,"Rendered sea does not match independent legacy vertex translated by "+offset);
        return recorder.vertices.size();
    }
    private static void checkContact(Minecraft mc,int offset) {
        double x=7.375,z=9.625,y=0x1.6a424fd30a82fp6+offset;
        BlockPos p=BlockPos.containing(x,y,z);var fluid=mc.level.getFluidState(p);
        require(!FluidContact.pointInLight(mc.level,x,y-.02,z),"Dry camera classified as immersed");
        require(FluidContact.pointInLight(mc.level,x,y+.02,z),"Wet camera missed sea");
        require(FluidContact.overlap(mc.level,p,fluid,new AABB(x-.0001,y+.02,z-.0001,x+.0001,y+.03,z+.0001))>0,"Client contact missed sea");
        var hit=fluid.getShape(mc.level,p).clip(new Vec3(x,y-.5,z),new Vec3(x,y+.5,z),p);
        require(hit!=null && Math.abs(hit.getLocation().y-y)<1e-6,"Client bucket ray missed actual sea");
    }
    private static void prepareStream(Minecraft mc) {
        var server=mc.getSingleplayerServer();
        serverWork=server.submit(()->{
            var world=server.getLevel(key(ROUTE[route]));held=new HashMap<>();
            for(int x=3;x<=11;x++) for(int z=5;z<=13;z++) for(int y=expected().upperMinimum();y<expected().roof();y++) {
                var p=new BlockPos(x,y,z);var s=world.getBlockState(p);
                if(s.getBlock() instanceof OceanLiquidBlock) held.put(p,s);
            }
            // Independent old cell-minimum observation, shifted with this world's profile.
            source=new BlockPos(7,(int)Math.floor(0x1.69d75422e2fb3p6+OFFSETS[route])-4,9);
            for(int y=-1;y<=1;y++) for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++)
                if(y==-1 || dx!=0 || dz!=0) world.setBlock(source.offset(dx,y,dz),Blocks.GLASS.defaultBlockState(),3);
            require(Interstice.LIGHT_BUCKET.get().emptyContents(null,world,source,null,Interstice.LIGHT_BUCKET.get().getDefaultInstance()),"Bucket failed in generated dimension");
            serverStartTick=server.getTickCount();
        });
        // View the junction from the side, above the glass source enclosure.
        double surface=0x1.6a424fd30a82fp6+OFFSETS[route];
        mc.getConnection().sendCommand("tp @s 7.5 "+(surface-3.5)+" 17.5 180 -12");
    }
    private static void capture(Minecraft mc,String name) {
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println("GEOMETRY_IMAGE "+name));
    }
    private static void report(Minecraft mc,boolean passed,String reason) {
        finished=true;
        var result=Map.of("passed",passed,"reason",reason,"observations",observations,
                "duration_seconds",(System.nanoTime()-began)/1_000_000_000L);
        String json=new GsonBuilder().setPrettyPrinting().create().toJson(result);
        try { Files.writeString(mc.gameDirectory.toPath().resolve("geometry-validation.json"),json); }
        catch(Exception e) {throw new RuntimeException(e);}
        System.out.println("GEOMETRY_VALIDATION "+json);mc.stop();
    }
    private static final class Recorder implements VertexConsumer {
        final List<Vec3> vertices=new ArrayList<>();
        public VertexConsumer addVertex(float x,float y,float z) {vertices.add(new Vec3(x,y,z));return this;}
        public VertexConsumer setColor(int r,int g,int b,int a) {return this;}
        public VertexConsumer setUv(float u,float v) {return this;}
        public VertexConsumer setUv1(int u,int v) {return this;}
        public VertexConsumer setUv2(int u,int v) {return this;}
        public VertexConsumer setNormal(float x,float y,float z) {return this;}
    }
}
