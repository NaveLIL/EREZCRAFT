package pro.erez.interstice.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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
import pro.erez.interstice.FluidLab;
import pro.erez.interstice.Interstice;

/** Development-only integrated client check; excluded from the published mod jar. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class VisualSmoke {
    private static boolean started;
    private static int stage,ticks;
    private static final String WORLD="fluid-chaotic-check";
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("interstice.visualSmoke")) return;
        Minecraft mc=Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started=true;
            mc.options.pauseOnLostFocus=false;
            mc.options.hideGui=true;
            mc.options.renderDistance().set(5);
            mc.options.fov().set(110);
            if(new java.io.File(mc.gameDirectory,"saves/"+WORLD+"/level.dat").exists()) {
                mc.createWorldOpenFlows().openWorld(WORLD,()->{});
            } else {
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                        new LevelSettings("Fluid visual check",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20261006,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
            }
            return;
        }
        if(mc.player==null || mc.level==null || mc.getConnection()==null) return;
        if(stage==0) {
            mc.getConnection().sendCommand("interstice lab");
            stage=1;ticks=0;return;
        }
        if(!mc.level.dimension().equals(FluidLab.WORLD)) return;
        ticks++;
        if(stage==3 && ticks>=72 && ticks<=184 && (ticks-72)%16==0) {
            capture(mc,String.format("lower-animation-frame-%02d.png",(ticks-72)/16));
        }
        if(stage==1 && ticks==80) {
            mc.getSingleplayerServer().execute(()->{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                player.getAbilities().flying=true;player.onUpdateAbilities();
            });
            mc.getConnection().sendCommand("tp @s 28 66 28 135 0");
        }
        if(stage==1 && ticks>=200) {capture(mc,"01-between-seas.png");stage=2;ticks=0;mc.options.fov().set(85);mc.getConnection().sendCommand("tp @s 12 78 -12 45 -25");}
        else if(stage==2 && ticks==100) {capture(mc,"02-upper-sea-underside.png");}
        else if(stage==2 && ticks>=140) {capture(mc,"02b-upper-sea-animation.png");stage=3;ticks=0;mc.options.fov().set(70);mc.getConnection().sendCommand("tp @s 0 47 0 0 60");}
        else if(stage==3 && ticks==100) {capture(mc,"03-lower-sea.png");}
        else if(stage==3 && ticks==140) {capture(mc,"03b-lower-sea-animation.png");}
        else if(stage==3 && ticks>=200) {stage=4;ticks=0;if(!mc.level.getFluidState(new net.minecraft.core.BlockPos(6,68,-2)).isSource()) mc.getConnection().sendCommand("setblock 6 68 -2 interstice:light_toxin");mc.getConnection().sendCommand("tp @s 8 68 -7 22 -20");}
        else if(stage==4 && ticks>=140) {capture(mc,"04-rising-fluid.png");stage=5;ticks=0;mc.getConnection().sendCommand("tp @s 0 95 0 0 0");}
        else if(stage==5 && ticks>=80) {capture(mc,"05-inside-light.png");stage=6;ticks=0;}
        else if(stage==6 && ticks>=40) mc.stop();
    }
    private static void capture(Minecraft mc,String filename) {
        Screenshot.grab(mc.gameDirectory,filename,mc.getMainRenderTarget(),message->System.out.println("VISUAL_SMOKE "+filename+" "+message.getString()));
    }
}
