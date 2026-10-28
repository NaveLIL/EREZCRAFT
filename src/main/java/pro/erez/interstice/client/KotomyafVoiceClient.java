package pro.erez.interstice.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.gear.*;

/** Finite sounds, no loop and no stop/restart on key release, removal of a piece or another jump. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class KotomyafVoiceClient {
    private static final Map<Integer,Voice> VOICES=new HashMap<>();
    private static ClientLevel level;private static long clock;
    private static final class Voice {SoundInstance sound;boolean pendingMeow,observedActive,meow;long started,purrUntil,startedNanos;}
    /** Native acceptance counters, also useful for diagnosing muted/failed audio. */
    public static long meowsStarted,purrsStarted,naturallyFinished;
    public static long purrsNaturallyFinished,shortestPurrFinishedMillis=Long.MAX_VALUE;
    private KotomyafVoiceClient(){}
    public static void request(KotomyafSounds.Voice packet){
        var mc=Minecraft.getInstance();if(mc.level==null)return;syncLevel(mc);
        if(!(mc.level.getEntity(packet.entityId()) instanceof Player p)||!KotomyafArmor.full(p))return;
        var v=VOICES.computeIfAbsent(p.getId(),k->new Voice());
        if(packet.jump())v.pendingMeow=true;else v.purrUntil=clock+15;
        advance(mc,p,v);
    }
    private static void syncLevel(Minecraft mc){if(level!=mc.level){VOICES.clear();level=mc.level;}}
    private static void advance(Minecraft mc,Player player,Voice v){
        if(v.sound!=null){
            boolean active=mc.getSoundManager().isActive(v.sound);v.observedActive|=active;
            if(active||!v.observedActive&&clock-v.started<20)return;
            if(v.observedActive){naturallyFinished++;if(!v.meow){purrsNaturallyFinished++;shortestPurrFinishedMillis=Math.min(shortestPurrFinishedMillis,(System.nanoTime()-v.startedNanos)/1_000_000);}}
            v.sound=null;
        }
        if(player==null||!KotomyafArmor.full(player)){v.pendingMeow=false;v.purrUntil=0;return;}
        boolean meow=v.pendingMeow;
        if(!meow&&!(v.purrUntil>clock&&player.isCrouching()&&player.walkAnimation.speed()>.005F))return;
        v.pendingMeow=false;v.purrUntil=0;v.observedActive=false;v.started=clock;v.startedNanos=System.nanoTime();v.meow=meow;
        v.sound=new SimpleSoundInstance(meow?SoundEvents.CAT_AMBIENT:SoundEvents.CAT_PURR,SoundSource.PLAYERS,
                meow?.85F:.65F,1F,RandomSource.create(),player.getX(),player.getY()+1,player.getZ());
        mc.getSoundManager().play(v.sound);if(meow)meowsStarted++;else purrsStarted++;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();syncLevel(mc);clock++;if(mc.level==null)return;
        VOICES.entrySet().removeIf(entry->{
            var entity=mc.level.getEntity(entry.getKey());var p=entity instanceof Player player?player:null;
            var voice=entry.getValue();advance(mc,p,voice);
            return voice.sound==null&&!voice.pendingMeow&&voice.purrUntil<=clock;
        });
    }
}
