package pro.erez.interstice.client;

import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.EventPriority;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.IslandWorld;

/** Real entry, survival safety, streamed terrain and return-trip check. Excluded from the release jar. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class IslandVisualSmoke {
    private static boolean started;
    private static int stage,ticks,totalTicks;
    private static String origin;
    private static double ox,oy,oz;
    private static boolean safeArrival;
    private static int blackFogFrames;
    private static boolean fogStayedBlack=true,nightStayedFixed=true,glowLitNeighbors;
    private static float initialSunTime;
    private static boolean ordinarySkyRestored;
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void checkFog(ViewportEvent.ComputeFogColor event) {
        if(!Boolean.getBoolean("interstice.islandSmoke") || stage<1 || stage>5) return;
        var level=event.getCamera().getEntity().level();
        if(!level.dimension().equals(IslandWorld.WORLD)) return;
        var point=event.getCamera().getPosition();
        if(!level.getFluidState(net.minecraft.core.BlockPos.containing(point)).isEmpty()) return;
        blackFogFrames++;
        fogStayedBlack &= event.getRed()==0 && event.getGreen()==0 && event.getBlue()==0;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("interstice.islandSmoke")) return;
        Minecraft mc=Minecraft.getInstance();
        if(!started && mc.screen instanceof TitleScreen) {
            started=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(6);mc.options.hideGui=false;
            mc.options.cloudStatus().set(CloudStatus.FANCY);
            String name="seeded-island-check";
            if(new java.io.File(mc.gameDirectory,"saves/"+name+"/level.dat").exists()) mc.createWorldOpenFlows().openWorld(name,()->{});
            else mc.createWorldOpenFlows().createFreshLevel(name,new LevelSettings("Seeded island check",GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261006,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
            return;
        }
        if(mc.player==null || mc.level==null || mc.getConnection()==null) return;
        if(++totalTicks>2400) {report(mc,false,"client-timeout");mc.stop();return;}
        if(stage==-1) {
            if(!mc.level.dimension().equals(IslandWorld.WORLD)) { stage=0; ticks=0; }
            return;
        }
        if(stage==0) {
            if(mc.level.dimension().equals(IslandWorld.WORLD)) {
                mc.getConnection().sendCommand("interstice leave"); stage=-1; return;
            }
            origin=mc.level.dimension().location().toString();ox=mc.player.getX();oy=mc.player.getY();oz=mc.player.getZ();
            mc.getConnection().sendCommand("gamemode survival");
            mc.getConnection().sendCommand("interstice explore");stage=1;ticks=0;return;
        }
        if(stage<6 && !mc.level.dimension().equals(IslandWorld.WORLD)) return;
        ticks++;
        if(stage==1 && ticks>=60) {
            initialSunTime=mc.level.getTimeOfDay(0);
            nightStayedFixed=mc.level.dimensionType().fixedTime().orElse(-1)==18000
                    && !mc.level.dimensionType().hasSkyLight()
                    && mc.level.effects() instanceof IslandAtmosphere.BlackVoidEffects
                    && mc.level.effects().skyType()==DimensionSpecialEffects.SkyType.NONE
                    && Float.isNaN(mc.level.effects().getCloudHeight());
            safeArrival=mc.player.getHealth()>=19.99F && mc.player.onGround() && !mc.player.isInFluidType();
            if(!safeArrival) {report(mc,false,"unsafe-arrival");mc.stop();return;}
            capture(mc,"00-survival-arrival.png");
            mc.getConnection().sendCommand("gamemode creative");mc.options.hideGui=true;mc.options.fov().set(85);stage=2;ticks=0;
        } else if(stage==2 && ticks==20) {
            mc.getConnection().sendCommand("time set day");
            var uuid=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{var player=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);player.getAbilities().flying=true;player.onUpdateAbilities();});
            mc.getConnection().sendCommand("tp @s 28 70 -34 40 10");
        } else if(stage==2 && ticks>=220) {
            nightStayedFixed &= mc.level.getTimeOfDay(0)==initialSunTime;
            mc.getConnection().sendCommand("time set midnight");
            capture(mc,"01-islands-overview.png");mc.getConnection().sendCommand("tp @s 64 68 40 130 10");stage=3;ticks=0;
        } else if(stage==3 && ticks>=180) {
            capture(mc,"02-islands-and-seas.png");mc.getConnection().sendCommand("tp @s 384 68 -272 45 12");stage=4;ticks=0;
        } else if(stage==4 && ticks>=240) {
            capture(mc,"03-distant-generated-region.png");mc.getConnection().sendCommand("tp @s 24 76 -24 45 -25");stage=5;ticks=0;
        } else if(stage==5 && ticks>=160) {
            nightStayedFixed &= mc.level.getTimeOfDay(0)==initialSunTime;
            int seaY=(int)Math.floor(pro.erez.interstice.SeaSurface.cellMinimum(24,-24));
            var sourcePos=new net.minecraft.core.BlockPos(24,seaY,-24);
            int belowLight=mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,sourcePos.below());
            glowLitNeighbors=mc.level.getBlockState(sourcePos).getLightEmission(mc.level,sourcePos)==15 && belowLight>=12;
            System.out.println("ISLAND_ATMOSPHERE neighbor_block_light="+belowLight+" fixed_night="+nightStayedFixed+" black_fog_frames="+blackFogFrames);
            capture(mc,"04-upper-sea-over-islands.png");mc.getConnection().sendCommand("interstice leave");stage=6;ticks=0;
        } else if(stage==6 && ticks>=40) {
            boolean returned=mc.level.dimension().location().toString().equals(origin)
                    && Math.abs(mc.player.getX()-ox)<1 && Math.abs(mc.player.getY()-oy)<1 && Math.abs(mc.player.getZ()-oz)<1;
            ordinarySkyRestored=mc.level.effects().skyType()==DimensionSpecialEffects.SkyType.NORMAL;
            capture(mc,"05-return-to-overworld.png");
            boolean atmosphere=nightStayedFixed && fogStayedBlack && blackFogFrames>10 && glowLitNeighbors && ordinarySkyRestored;
            report(mc,returned && atmosphere,!returned ? "return-mismatch" : atmosphere ? "completed" : "atmosphere-mismatch");mc.stop();
        }
    }
    private static void capture(Minecraft mc,String name) {
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println("ISLAND_VISUAL "+name+" "+message.getString()));
    }
    private static void report(Minecraft mc,boolean passed,String reason) {
        String json="{\"passed\":"+passed+",\"reason\":\""+reason+"\",\"survival_arrival_safe\":"+safeArrival+",\"fixed_midnight\":"+nightStayedFixed+",\"black_air_fog\":"+fogStayedBlack+",\"black_fog_frames\":"+blackFogFrames+",\"emission_lights_neighbors\":"+glowLitNeighbors+",\"overworld_sky_restored\":"+ordinarySkyRestored+",\"origin_dimension\":\""+origin+"\",\"total_client_ticks\":"+totalTicks+"}";
        try {Files.writeString(mc.gameDirectory.toPath().resolve("island-validation.json"),json);} catch(Exception e) {throw new RuntimeException(e);}
        System.out.println("ISLAND_VALIDATION "+json);
    }
}
