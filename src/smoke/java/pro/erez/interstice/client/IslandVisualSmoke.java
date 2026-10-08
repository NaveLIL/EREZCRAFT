package pro.erez.interstice.client;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.EventPriority;
import pro.erez.interstice.FluidContact;
import pro.erez.interstice.FluidLab;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.worldgen.IslandWorld;

/** Fresh, bounded real entry/atmosphere/return check, with an optional tall-world route. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class IslandVisualSmoke {
    private static final boolean TALL=Boolean.getBoolean("interstice.tallSmoke");
    private static boolean started,finished;
    private static long began;
    private static int stage,ticks,totalTicks;
    private static String origin;
    private static double ox,oy,oz;
    private static boolean safeArrival,glowLitNeighbors,ordinarySkyRestored,internalTransfers;
    private static int blackFogFrames,immersedFogFrames;
    private static boolean fogStayedBlack=true,nightStayedFixed=true,immersionFogCorrect=true;
    private static float initialSunTime;
    private static final LinkedHashMap<String,Object> arrival=new LinkedHashMap<>();

    private static ResourceKey<Level> target() {return TALL ? IslandWorld.TALL_WORLD : IslandWorld.WORLD;}
    private static GeometryProfile profile() {return TALL ? GeometryProfile.TALL : GeometryProfile.LEGACY;}
    private static String enterCommand() {return TALL ? "interstice explore tall" : "interstice explore legacy";}
    private static void require(boolean condition,String message) {if(!condition) throw new IllegalStateException(message);}
    private static void advance(int next) {stage=next;ticks=0;}

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void checkFog(ViewportEvent.ComputeFogColor event) {
        if(!Boolean.getBoolean("interstice.islandSmoke") || finished || stage<1) return;
        var level=event.getCamera().getEntity().level();
        if(!level.dimension().equals(target())) return;
        var point=event.getCamera().getPosition();
        if(FluidContact.pointInLight(level,point.x,point.y,point.z)) {
            immersedFogFrames++;
            immersionFogCorrect &= event.getRed()==.70F && event.getGreen()==.69F && event.getBlue()==.47F;
        } else if(level.getFluidState(BlockPos.containing(point)).isEmpty()) {
            blackFogFrames++;
            fogStayedBlack &= event.getRed()==0 && event.getGreen()==0 && event.getBlue()==0;
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("interstice.islandSmoke") || finished) return;
        Minecraft mc=Minecraft.getInstance();
        if(began==0) began=System.nanoTime();
        try {
            require(System.nanoTime()-began<900_000_000_000L,"Initialization or client operation timed out");
            if(!started && mc.screen instanceof TitleScreen) {
                started=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(6);
                mc.options.gamma().set(1.0);
                mc.options.framerateLimit().set(60);mc.options.hideGui=false;
                mc.options.cloudStatus().set(CloudStatus.FANCY);
                String name=(TALL ? "tall-island-check-" : "island-check-")+System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(name,new LevelSettings("Island expedition check",GameType.SURVIVAL,
                        false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20261006,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
                return;
            }
            if(mc.player==null || mc.level==null || mc.getConnection()==null || mc.screen!=null) return;
            totalTicks++;
            if(stage==0) {
                if(!mc.player.onGround()) return;
                origin=mc.level.dimension().location().toString();ox=mc.player.getX();oy=mc.player.getY();oz=mc.player.getZ();
                mc.getConnection().sendCommand(enterCommand());advance(1);return;
            }
            ResourceKey<Level> expected=stage==7 ? IslandWorld.WORLD : stage==8 ? FluidLab.WORLD : target();
            if(stage<10 && !mc.level.dimension().equals(expected)) return;
            ticks++;
            if(stage==1 && ticks>=60) {
                require(mc.level.getHeight()==profile().height() && GeometryProfiles.get(mc.level).equals(profile()),"Arrival geometry mismatch");
                initialSunTime=mc.level.getTimeOfDay(0);
                nightStayedFixed=mc.level.dimensionType().fixedTime().orElse(-1)==18000
                        && !mc.level.dimensionType().hasSkyLight()
                        && mc.level.effects() instanceof IslandAtmosphere.BlackVoidEffects
                        && mc.level.effects().skyType()==DimensionSpecialEffects.SkyType.NONE
                        && Float.isNaN(mc.level.effects().getCloudHeight());
                safeArrival=mc.player.getHealth()>=19.99F && mc.player.onGround() && !mc.player.isInFluidType()
                        && mc.gameMode.getPlayerMode()==GameType.SURVIVAL;
                arrival.put("health",mc.player.getHealth());arrival.put("on_ground",mc.player.onGround());
                arrival.put("in_fluid",mc.player.isInFluidType());arrival.put("game_mode",mc.gameMode.getPlayerMode().getName());
                arrival.put("position",java.util.List.of(mc.player.getX(),mc.player.getY(),mc.player.getZ()));
                arrival.put("floor",mc.level.getBlockState(mc.player.blockPosition().below()).toString());
                capture(mc,"00-survival-arrival.png");
                require(safeArrival,"Unsafe survival arrival: "+arrival);
                mc.getConnection().sendCommand("gamemode creative");
                mc.getConnection().sendCommand("effect give @s minecraft:night_vision infinite 1 true");
                mc.options.hideGui=true;mc.options.fov().set(85);advance(2);
            } else if(stage==2 && ticks==20) {
                mc.getConnection().sendCommand("time set day");
                var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
                server.execute(()->{var player=server.getPlayerList().getPlayer(uuid);player.getAbilities().flying=true;player.onUpdateAbilities();});
                var landingPos=mc.player.blockPosition();
                mc.getConnection().sendCommand("tp @s "+landingPos.getX()+" "+(landingPos.getY()+0.1)+" "+landingPos.getZ()+" 45 10");
            } else if(stage==2 && ticks>=220) {
                nightStayedFixed &= mc.level.getTimeOfDay(0)==initialSunTime;
                mc.getConnection().sendCommand("time set midnight");
                capture(mc,"01-islands-overview.png");
                var cave=findCave(mc.level,mc.player.blockPosition());
                mc.getConnection().sendCommand("tp @s "+(cave.getX()+0.5)+" "+(cave.getY()+0.1)+" "+(cave.getZ()+0.5)+" 90 0");advance(3);
            } else if(stage==3 && ticks>=180) {
                capture(mc,"02-islands-and-seas.png");
                var lowPos=findTierGround(mc.level,mc.player.blockPosition(),45,75);
                mc.getConnection().sendCommand("tp @s "+(lowPos.getX()+0.5)+" "+(lowPos.getY()+0.1)+" "+(lowPos.getZ()+0.5)+" 45 -40");advance(4);
            } else if(stage==4 && ticks>=240) {
                capture(mc,"03-distant-generated-region.png");
                mc.getConnection().sendCommand("tp @s 24 "+(profile().upperReference()-18)+" -24 45 -25");advance(5);
            } else if(stage==5 && ticks>=160) {
                nightStayedFixed &= mc.level.getTimeOfDay(0)==initialSunTime;
                int seaY=(int)Math.floor(SeaSurface.cellMinimum(GeometryProfiles.get(mc.level),24,-24,true));
                var sourcePos=new BlockPos(24,seaY,-24);
                int belowLight=mc.level.getBrightness(LightLayer.BLOCK,sourcePos.below());
                glowLitNeighbors=mc.level.getBlockState(sourcePos).getLightEmission(mc.level,sourcePos)==15 && belowLight>=12;
                System.out.println("ISLAND_ATMOSPHERE height="+mc.level.getHeight()+" neighbor_block_light="+belowLight
                        +" fixed_night="+nightStayedFixed+" black_fog_frames="+blackFogFrames);
                capture(mc,"04-upper-sea-over-islands.png");
                if(TALL) {
                    // Independent translated legacy observation; the actual camera/fog event must see immersion.
                    double y=0x1.6a424fd30a82fp6+128-mc.player.getEyeHeight()+.5;
                    mc.getConnection().sendCommand("tp @s 7.375 "+y+" 9.625 180 0");advance(6);
                } else {mc.getConnection().sendCommand("interstice leave");advance(10);}
            } else if(stage==6 && ticks>=60) {
                require(immersedFogFrames>10 && immersionFogCorrect,"Tall ocean camera immersion was not observed correctly");
                capture(mc,"05-upper-sea-immersion.png");
                mc.getConnection().sendCommand("interstice explore legacy");advance(7);
            } else if(stage==7 && ticks>=60) {
                require(mc.level.getHeight()==128 && GeometryProfiles.get(mc.level).equals(GeometryProfile.LEGACY),"Legacy geometry changed on return");
                capture(mc,"06-legacy-islands.png");mc.getConnection().sendCommand("interstice lab");advance(8);
            } else if(stage==8 && ticks>=60) {
                require(GeometryProfiles.get(mc.level).equals(GeometryProfile.LEGACY),"Laboratory geometry changed");
                capture(mc,"07-legacy-laboratory.png");mc.getConnection().sendCommand(enterCommand());advance(9);
            } else if(stage==9 && ticks>=60) {
                require(mc.level.getHeight()==256 && GeometryProfiles.get(mc.level).equals(GeometryProfile.TALL),"Tall profile lost after internal transfers");
                internalTransfers=true;mc.getConnection().sendCommand("interstice leave");advance(10);
            } else if(stage==10 && ticks>=40) {
                boolean returned=mc.level.dimension().location().toString().equals(origin)
                        && Math.abs(mc.player.getX()-ox)<1 && Math.abs(mc.player.getY()-oy)<1 && Math.abs(mc.player.getZ()-oz)<1;
                ordinarySkyRestored=mc.level.effects().skyType()==DimensionSpecialEffects.SkyType.NORMAL;
                capture(mc,"08-return-to-overworld.png");
                mc.getConnection().sendCommand("effect clear @s");
                require(returned,"Original return location was lost");
                require(nightStayedFixed && fogStayedBlack && blackFogFrames>10 && glowLitNeighbors && ordinarySkyRestored,"Atmosphere mismatch");
                report(mc,true,"completed");
            }
        } catch(Throwable error) {error.printStackTrace();report(mc,false,error.toString());}
    }
    private static void capture(Minecraft mc,String name) {
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println("ISLAND_VISUAL "+name));
    }
    private static BlockPos findCave(Level level, BlockPos near) {
        for(int r=0;r<=48;r++) for(int dx=-r;dx<=r;dx++) for(int dz=-r;dz<=r;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=r) continue;
            int x=near.getX()+dx, z=near.getZ()+dz;
            for(int y=45;y<=near.getY()-3;y++) {
                BlockPos p=new BlockPos(x,y,z);
                if(level.getBlockState(p).isAir() && !level.getBlockState(p.below()).isAir()
                        && !level.getBlockState(p.above(3)).isAir()) return p;
            }
        }
        return near;
    }
    private static BlockPos findTierGround(Level level, BlockPos near, int minY, int maxY) {
        for(int r=0;r<=48;r++) for(int dx=-r;dx<=r;dx++) for(int dz=-r;dz<=r;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=r) continue;
            int x=near.getX()+dx, z=near.getZ()+dz;
            for(int y=maxY;y>=minY;y--) {
                BlockPos p=new BlockPos(x,y,z);
                if(!level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                        && level.getBlockState(p.above(2)).isAir()) return p.above();
            }
        }
        return near;
    }
    private static void report(Minecraft mc,boolean passed,String reason) {
        finished=true;
        var result=new LinkedHashMap<String,Object>();
        result.put("passed",passed);result.put("reason",reason);result.put("dimension",target().location().toString());
        result.put("entry_command",enterCommand());
        result.put("height",profile().height());result.put("survival_arrival_safe",safeArrival);
        result.put("arrival",arrival);
        result.put("fixed_midnight",nightStayedFixed);result.put("black_air_fog",fogStayedBlack);
        result.put("black_fog_frames",blackFogFrames);result.put("emission_lights_neighbors",glowLitNeighbors);
        result.put("immersed_fog_frames",immersedFogFrames);result.put("immersion_fog_correct",immersionFogCorrect);
        result.put("internal_transfers",internalTransfers);result.put("overworld_sky_restored",ordinarySkyRestored);
        result.put("origin_dimension",origin);result.put("total_client_ticks",totalTicks);
        result.put("duration_seconds",(System.nanoTime()-began)/1_000_000_000L);
        String json=new GsonBuilder().setPrettyPrinting().create().toJson(result);
        try {Files.writeString(mc.gameDirectory.toPath().resolve("island-validation.json"),json);}
        catch(Exception e) {throw new RuntimeException(e);}
        System.out.println("ISLAND_VALIDATION "+json);mc.stop();
    }
}
