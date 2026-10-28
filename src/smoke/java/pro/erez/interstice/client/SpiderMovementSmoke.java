package pro.erez.interstice.client;

import com.google.gson.*;
import com.seibel.distanthorizons.api.DhApi;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.CaveRiftSpiderEntity;
import pro.erez.interstice.fauna.StrongRealmFauna;
import pro.erez.interstice.worldgen.*;

/** Fresh full-pack native scene, not a Survival acceptance test. Never touches an existing save. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class SpiderMovementSmoke {
    private static final boolean ENABLED=Boolean.getBoolean("interstice.spiderMovementSmoke");
    private static final String SAVE="spider-movement-owned";
    private static final JsonObject report=new JsonObject();
    private static final List<CaveRiftSpiderEntity> spiders=new ArrayList<>();
    private static final List<Vec3> goals=new ArrayList<>();
    private static final boolean[] walls=new boolean[3];
    private static CompletableFuture<?> preparation;
    private static ServerLevel level;
    private static pro.erez.interstice.fauna.VaultHunter hunter;
    private static net.minecraft.world.entity.decoration.ArmorStand camera;
    private static int startTick,stage,shot,viewWait, aiTick, combatTick;
    private static net.minecraft.world.entity.monster.Zombie ceilingPrey;
    private static Vec3 remembered;
    private static net.minecraft.server.level.ServerPlayer aiPlayer;
    private static boolean traversalFinished, ceilingCombatFinished, hunterCombatStarted, headingHoldFinished;
    private static boolean modelVerified, strikeScreenshot;
    private static final Set<Integer> wallHeadings=new HashSet<>();
    private static int strikeCameraTicks;
    private static final List<pro.erez.interstice.fauna.VaultHunter> stressHunters=new ArrayList<>();
    private static final List<Double> stressTickMs=new ArrayList<>();
    private static int motionFrameCount,lastMotionFrameTick=-1;
    private static final Map<String,List<Double>> serverMotionSteps=new LinkedHashMap<>();
    private static final Map<String,Vec3> serverMotionPrevious=new HashMap<>();
    private static long tickStarted;
    private static int stressStart;
    private static CaveRiftSpiderEntity motionSpider;
    private static pro.erez.interstice.fauna.VaultHunter motionHunter;
    private static net.minecraft.world.entity.monster.Zombie motionPrey;
    private static final Map<String,List<Double>> motionSteps=new LinkedHashMap<>();
    private static final Map<String,Vec3> motionPrevious=new HashMap<>();
    private static volatile int elapsed;
    private static boolean started,closing;
    private static volatile boolean done;
    private static long deadline;
    private static NativeChunkSettler.Session settler;
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException(why);}
    @SubscribeEvent public static void serverPre(ServerTickEvent.Pre event){if(ENABLED)tickStarted=System.nanoTime();}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){
        if(!ENABLED)return;
        if(settler!=null)settler.tick(event.getServer());
        if(level==null||startTick==0||done)return;
        try{
            elapsed=event.getServer().getTickCount()-startTick;
            for(int i=0;i<spiders.size();i++){
                var s=spiders.get(i);walls[i]|=s.isClimbingWall()&&s.getY()>71;
                require(level.noCollision(s,s.getBoundingBox().deflate(.01)),"Native spider intersected terrain: "+s.getVariant()+" "+s.position());
            }
            if(elapsed>=240&&!traversalFinished){
                var results=new JsonArray();
                for(int i=0;i<3;i++){
                    var s=spiders.get(i);require(walls[i]&&s.isClimbingCeiling()&&s.getBoundingBox().getCenter().distanceTo(goals.get(i))<.3,"Native traversal failed: "+s.getVariant()+" "+s.position());
                    var row=new JsonObject();row.addProperty("variant",s.getVariant().name());row.addProperty("wall_climbed",walls[i]);row.addProperty("ceiling_reached",s.isClimbingCeiling());row.addProperty("height",s.getBbHeight());row.addProperty("distance_to_goal",s.getBoundingBox().getCenter().distanceTo(goals.get(i)));results.add(row);
                }
                require(spiders.stream().allMatch(s->s.getBrain() instanceof net.tslat.smartbrainlib.api.core.SmartBrain),"A spider still uses a legacy brain");
                report.addProperty("all_spiders_use_smartbrain",true);
                report.add("actual_tick_traversal",results);
                require(hunter.isAlive(),"Prototype hunter died in the dry fixture");
                report.addProperty("hunter_server_position",hunter.position().toString());
                report.addProperty("hunter_client_id",hunter.getId());
                report.addProperty("server_traversal_passed",true);
                traversalFinished=true;
                for(int i=0;i<spiders.size();i++){
                    var s=spiders.get(i);s.setNoAi(true);
                    s.moveTo(2+s.getBbWidth()/2+.01,72,3.5+i*7);
                    s.setCrawlOrientation(new Vec3(0,1,0),new Vec3(1,0,0),0);
                }
            }
            // Hold the already-tested wall pose long enough for the observer to
            // receive it even while DH is initially building client meshes.
            if(traversalFinished&&elapsed>=300&&!headingHoldFinished){
                for(int i=0;i<spiders.size();i++){
                    var s=spiders.get(i);var goal=goals.get(i);
                    s.moveTo(goal.x,goal.y-s.getBbHeight()/2,goal.z);
                    s.setCrawlOrientation(new Vec3(1,0,0),new Vec3(0,-1,0),0);
                }
                headingHoldFinished=true;
                ceilingPrey=new net.minecraft.world.entity.monster.Zombie(net.minecraft.world.entity.EntityType.ZOMBIE,level);
                ceilingPrey.setNoAi(true);ceilingPrey.setNoGravity(true);ceilingPrey.moveTo(5.5,70,17.5);level.addFreshEntity(ceilingPrey);
                spiders.get(2).setNoAi(false);spiders.get(2).setTarget(ceilingPrey);combatTick=event.getServer().getTickCount();
            }
            if(headingHoldFinished&&!ceilingCombatFinished&&event.getServer().getTickCount()-combatTick>=90){
                require(ceilingPrey.getHealth()<20&&spiders.get(2).isClimbingCeiling(),"Full-pack ceiling spitter failed to hit actual prey");
                report.addProperty("full_pack_ceiling_spit_hit",true);ceilingPrey.discard();
                var s=spiders.get(2);s.setNoAi(true);s.setTarget(null);Vec3 goal=goals.get(2);s.moveTo(goal.x,goal.y-s.getBbHeight()/2,goal.z);s.setDeltaMovement(Vec3.ZERO);
                aiPlayer=event.getServer().getPlayerList().getPlayers().getFirst();
                aiPlayer.setGameMode(GameType.SURVIVAL);aiPlayer.setInvulnerable(false);aiPlayer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,1000,4));
                aiPlayer.teleportTo(level,13.5,70,22.5,Set.of(),0,0);aiPlayer.hasChangedDimension();hunter.setNoAi(false);
                aiTick=event.getServer().getTickCount();ceilingCombatFinished=true;
            }
            if(ceilingCombatFinished){
                int tick=event.getServer().getTickCount()-aiTick;
                if(tick==6){
                    require(hunter.getTarget()==aiPlayer,"Full-pack hunter did not acquire visible actual player");
                    remembered=hunter.lastKnownPosition();
                    for(int z=-8;z<=34;z++)for(int y=70;y<=76;y++)level.setBlock(new BlockPos(11,y,z),Blocks.STONE.defaultBlockState(),3);
                    aiPlayer.teleportTo(level,13.5,70,24.5,Set.of(),0,0);aiPlayer.hasChangedDimension();
                }
                if(tick==12){
                    require(hunter.getTarget()==null&&hunter.lastKnownPosition().equals(remembered),"Full-pack hunter tracked a hidden player's new coordinates");
                    require(hunter.phase()==pro.erez.interstice.fauna.VaultHunter.Phase.AMBUSH,"Full-pack hunter did not take reachable cover for an ambush");
                    report.addProperty("full_pack_hunter_lost_sight_snapshot",true);
                    report.addProperty("full_pack_hunter_covered_ambush",true);
                }
                if(tick==220){
                    require(hunter.lastKnownPosition()==null,"Full-pack hunter did not forget the hidden player");
                    report.addProperty("full_pack_hunter_finite_memory",true);
                    hunter.setNoAi(true);hunter.moveTo(6.5,70,22.5);
                    aiPlayer.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
                    aiPlayer.setHealth(20);aiPlayer.teleportTo(level,7.5,70,22.5,Set.of(),90,0);aiPlayer.hasChangedDimension();
                    hunter.setNoAi(false);hunterCombatStarted=true;
                }
                if(hunterCombatStarted&&stressStart==0&&tick>220&&aiPlayer.getHealth()<20){
                    report.addProperty("full_pack_wendigo_strike_damage",true);
                    hunter.setNoAi(true);hunter.moveTo(6.5,70,22.5);hunter.setYRot(0);hunter.yBodyRot=0;hunter.yHeadRot=0;
                    aiPlayer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,1000,4));
                    aiPlayer.teleportTo(level,9.5,70,12.5,Set.of(),0,0);aiPlayer.hasChangedDimension();
                    for(int i=0;i<8;i++){
                        var actor=StrongRealmFauna.VAULT_HUNTER.get().create(level);actor.setNoAi(true);actor.setPersistenceRequired();actor.moveTo(3.5+(i%4)*2,70,2.5+(i/4)*3);
                        if(i<4)actor.setHealth(8);level.addFreshEntity(actor);stressHunters.add(actor);
                    }
                    stressStart=event.getServer().getTickCount();
                }
                if(stressStart>0){
                    int age=event.getServer().getTickCount()-stressStart;
                    if(age==20)stressHunters.forEach(e->e.setNoAi(false));
                    if(age>20)stressTickMs.add((System.nanoTime()-tickStarted)/1_000_000D);
                    if(age==160){
                        require(stressHunters.size()==8&&stressHunters.stream().allMatch(e->e.isAlive()&&e.getBrain() instanceof net.tslat.smartbrainlib.api.core.SmartBrain),"Eight-hunter full-pack scenario failed");
                        require(stressHunters.stream().anyMatch(e->e.phase()==pro.erez.interstice.fauna.VaultHunter.Phase.COOLDOWN),"Wounded stress hunters never completed retreat");
                        require(stressHunters.stream().anyMatch(e->e.phase()==pro.erez.interstice.fauna.VaultHunter.Phase.CHASE),"Healthy stress hunters did not pursue player");
                        stressTickMs.sort(Double::compare);
                        var timing=new JsonObject();timing.addProperty("active_hunters",8);timing.addProperty("samples",stressTickMs.size());
                        timing.addProperty("p50_ms",stressTickMs.get(stressTickMs.size()/2));timing.addProperty("p95_ms",stressTickMs.get((int)(stressTickMs.size()*.95)));timing.addProperty("max_ms",stressTickMs.getLast());
                        timing.addProperty("scope","Prepared integrated-server scene, not production capacity or Survival acceptance");report.add("eight_hunter_timing",timing);
                        stressHunters.forEach(net.minecraft.world.entity.Entity::discard);
                        for(int x=-2;x<=50;x++)for(int z=18;z<=34;z++){
                            level.setBlock(new BlockPos(x,69,z),Blocks.STONE.defaultBlockState(),3);
                            for(int y=70;y<=77;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
                        }
                        motionHunter=StrongRealmFauna.VAULT_HUNTER.get().create(level);
                        motionHunter.moveTo(3.5,70,22.5);motionHunter.setPersistenceRequired();level.addFreshEntity(motionHunter);
                        motionSpider=Interstice.CAVE_RIFT_SPIDER.get().create(level);
                        motionSpider.setVariant(CaveRiftSpiderEntity.Variant.SPITTER);motionSpider.moveTo(3.5,70,29.5);motionSpider.setPersistenceRequired();level.addFreshEntity(motionSpider);
                        motionPrey=new net.minecraft.world.entity.monster.Zombie(net.minecraft.world.entity.EntityType.ZOMBIE,level);
                        motionPrey.setNoAi(true);motionPrey.setNoGravity(true);motionPrey.moveTo(18.5,70,29.5);level.addFreshEntity(motionPrey);motionSpider.setTarget(motionPrey);
                    }
                    if(age>=160&&age<280){
                        aiPlayer.teleportTo(level,motionHunter.getX()+8,70,22.5,Set.of(),0,0);
                        motionPrey.moveTo(motionSpider.getX()+15,70,29.5);
                        if(age>=185&&age<270)for(var actor:List.of(motionHunter,motionSpider)){String key=actor==motionHunter?"hunter":"spitter";Vec3 old=serverMotionPrevious.put(key,actor.position());if(old!=null)serverMotionSteps.computeIfAbsent(key,k->new ArrayList<>()).add(actor.position().subtract(old).horizontalDistance());}
                    }
                    if(age==280){
                        motionSpider.setNoAi(true);motionHunter.setNoAi(true);motionPrey.discard();
                        aiPlayer.setGameMode(GameType.CREATIVE);done=true;
                    }
                }
                require(!hunterCombatStarted||tick<300||stressStart>0||done,"Wendigo did not damage actual full-pack player");
            }
        }catch(Throwable failure){report.addProperty("error",failure.toString());done=true;}
    }
    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event){
        if(!ENABLED||stage==9)return;var mc=Minecraft.getInstance();
        try{
            if(!started){
                if(!(mc.screen instanceof TitleScreen))return;
                require(mc.gameDirectory.getAbsolutePath().replace('\\','/').contains("/.verification/"),"Refusing non-verification profile");
                require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/"+SAVE)),"Refusing any existing world");
                for(String id:List.of("create","aeronautics","sable","distanthorizons","ars_nouveau","mekanism","draconicevolution","jei","erezcraft_ui"))
                    require(ModList.get().isLoaded(id),"Missing full-pack component "+id);
                require(DhApi.Delayed.configs!=null,"DH not initialized");
                require(ModList.get().isLoaded("smartbrainlib"),"SmartBrainLib not loaded in full pack");
                report.addProperty("smartbrainlib_loaded",true);
                require(ModList.get().isLoaded("azurecortex"),"AzureCortex not loaded in full pack");
                report.addProperty("azurecortex_loaded",true);
                DhApi.Delayed.configs.worldGenerator().enableDistantWorldGeneration().setValue(false);
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.getEntityRenderDispatcher().setRenderHitBoxes(true);
                var rules=new GameRules();rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(12);
                report.addProperty("full_pack_dh_loaded",true);report.addProperty("owner_worlds_used",false);report.addProperty("survival_balance_verified",false);
                mc.createWorldOpenFlows().createFreshLevel(SAVE,new LevelSettings("Owned spider movement",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20261023,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
            }
            require(System.nanoTime()<deadline,"Native smoke timeout at stage "+stage);
            if(closing){
                if(settler.failed())throw new IllegalStateException(settler.failure());
                if(!settler.ready())return;
                var server=mc.getSingleplayerServer();mc.level.disconnect();mc.disconnect(new TitleScreen());
                report.addProperty("own_server_stopped",server.isStopped());report.addProperty("passed",!report.has("error")&&report.has("server_traversal_passed")&&server.isStopped());
                write(mc);stage=9;mc.stop();return;
            }
            if(mc.screen instanceof BackupConfirmScreen){for(var child:mc.screen.children())if(child instanceof Button b&&b.getMessage().getContents() instanceof TranslatableContents t&&t.getKey().equals("selectWorld.backupJoinSkipButton")){b.onPress();return;}return;}
            if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
            if(motionHunter!=null&&motionSpider!=null&&!done&&stressStart>0){
                int age=mc.getSingleplayerServer().getTickCount()-stressStart;
                if(age>=185&&age<225&&motionFrameCount<30&&lastMotionFrameTick!=age){var peer=mc.level.getEntity(motionHunter.getId());if(peer!=null){mc.options.fov().set(45);view(mc,peer.getX()+4,70.9,peer.getZ()+7,150,-2);Screenshot.grab(mc.gameDirectory,String.format(java.util.Locale.ROOT,"hunter-walk-%03d.png",motionFrameCount++),mc.getMainRenderTarget(),m->{});lastMotionFrameTick=age;}}
                for(var actor:List.of(motionHunter,motionSpider)){
                    var peer=mc.level.getEntity(actor.getId());if(peer==null)continue;
                    String kind=actor==motionHunter?"hunter":"spitter";
                    Vec3 previous=motionPrevious.put(kind,peer.position());
                    if(age>185&&age<270&&previous!=null)
                        motionSteps.computeIfAbsent(kind,k->new ArrayList<>()).add(peer.position().subtract(previous).horizontalDistance());
                }
            }
            if(stage==0){
                verifyHunterAudio(mc);
                var server=mc.getSingleplayerServer();var uuid=mc.getUser().getProfileId();
                preparation=server.submit(()->{
                    level=server.getLevel(IslandWorld.TENSION_WORLD);
                    require(level.getChunkSource().getGenerator() instanceof IslandChunkGenerator g&&g.terrainRevision()==6,"Wrong V6 generator");
                    for(int x=-6;x<=20;x++)for(int z=-6;z<=30;z++)for(int y=69;y<=80;y++)level.setBlock(new BlockPos(x,y,z),(y==69?Blocks.STONE:Blocks.AIR).defaultBlockState(),3);
                    for(int i=0;i<3;i++){
                        int z=3+i*7;
                        for(int y=70;y<=77;y++)for(int dz=-2;dz<=2;dz++)level.setBlock(new BlockPos(1,y,z+dz),Blocks.GLOWSTONE.defaultBlockState(),3);
                        for(int x=1;x<=12;x++)for(int dz=-2;dz<=2;dz++)level.setBlock(new BlockPos(x,77,z+dz),Blocks.GLOWSTONE.defaultBlockState(),3);
                        var s=Interstice.CAVE_RIFT_SPIDER.get().create(level);s.setVariant(CaveRiftSpiderEntity.Variant.values()[i]);s.setPersistenceRequired();s.moveTo(3.5,70,z+.5);level.addFreshEntity(s);
                        Vec3 goal=new Vec3(9.5,77-s.getBbHeight()/2,z+.5);s.setNoAi(true);spiders.add(s);goals.add(goal);
                    }
                    hunter=StrongRealmFauna.VAULT_HUNTER.get().create(level);hunter.setNoAi(true);hunter.setPersistenceRequired();hunter.moveTo(6.5,70,22.5);level.addFreshEntity(hunter);
                    var player=server.getPlayerList().getPlayer(uuid);player.teleportTo(level,11.5,73,24.5,Set.of(),155,12);player.hasChangedDimension();player.setNoGravity(true);
                    level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(0,69,0,15,81,26)).forEach(net.minecraft.world.entity.Entity::discard);
                    return true;
                });stage=1;return;
            }
            if(!preparation.isDone())return;preparation.join();
            if(!mc.level.dimension().equals(IslandWorld.TENSION_WORLD)||mc.screen!=null)return;
            if(stage==1){
                mc.options.hideGui=true;view(mc,6,72,8.5,140,-12);
                if(++viewWait<35)return;
                preparation=mc.getSingleplayerServer().submit(()->{
                    for(int i=0;i<3;i++){spiders.get(i).setNoAi(false);require(spiders.get(i).crawlTo(goals.get(i)),"Native graph did not find route");}
                    startTick=mc.getSingleplayerServer().getTickCount();return true;
                });stage=2;viewWait=0;return;
            }
            require(mc.getResourceManager().getResource(VaultHunterRenderer.TEXTURE).isPresent(),"Wendigo texture absent");
            if(mc.level.getEntity(hunter.getId()) instanceof pro.erez.interstice.fauna.VaultHunter peer){
                if(!modelVerified){ verifyModel(peer);modelVerified=true; }
                float strike=peer.attackProgress(0);
                if(hunterCombatStarted&&!done&&stressStart==0){
                    mc.getEntityRenderDispatcher().setRenderHitBoxes(false);view(mc,8.5,69.6,26.5,153,0);strikeCameraTicks++;
                }
                if(strike>.25F&&strikeCameraTicks>=3&&!strikeScreenshot){
                    report.addProperty("client_wendigo_strike_synced",true);
                    Screenshot.grab(mc.gameDirectory,"wendigo-strike.png",mc.getMainRenderTarget(),message->{});strikeScreenshot=true;
                }
            }
            for(var s:spiders){
                var client=mc.level.getEntity(s.getId());
                if(!(client instanceof CaveRiftSpiderEntity))return;
                var peer=(CaveRiftSpiderEntity)client;
                require(Math.abs(peer.getBbHeight()-s.getBbHeight())<.001,"Synced client variant hitbox differs");
                if(peer.isClimbingWall()&&peer.surfaceTransition(1)>.99&&Math.abs(peer.getCrawlForward().y)>.9&&!wallHeadings.contains(s.getId())){
                    verifyWallHeading(mc,peer);wallHeadings.add(s.getId());
                }
                if(done)require(peer.getSurface()==s.getSurface(),"Synced attachment differs from server");
            }
            if(viewWait>0){viewWait--;return;}
            if(shot==0&&elapsed>20||shot==1&&elapsed>100||shot>=2&&done){
                Screenshot.grab(mc.gameDirectory,"spider-stage-"+shot+".png",mc.getMainRenderTarget(),message->{});
                shot++;
                if(shot==1) view(mc,9.5,72,16,180,-30);
                if(shot==3) view(mc,9.5,72,23,180,-30);
                if(shot==4) view(mc,9.5,72,9,180,-30);
                if(shot==5) {mc.getEntityRenderDispatcher().setRenderHitBoxes(false);view(mc,6.5,69.6,27,180,0);}
                viewWait=12;
            }
            if(done&&shot>=6){
                var motion=new JsonObject();
                for(var entry:motionSteps.entrySet()){
                    var row=new JsonObject();var values=entry.getValue();
                    require(values.size()>=40,"Insufficient network movement samples");
                    double mean=values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
                    row.addProperty("samples",values.size());row.addProperty("mean_blocks_per_tick",mean);
                    row.addProperty("stationary_ticks",values.stream().filter(v->v<.001).count());
                    row.addProperty("max_step",values.stream().mapToDouble(Double::doubleValue).max().orElseThrow());
                    row.addProperty("coefficient_of_variation",Math.sqrt(values.stream().mapToDouble(v->(v-mean)*(v-mean)).average().orElseThrow())/Math.max(.0001,mean));
                    motion.add(entry.getKey(),row);
                }
                require(motion.size()==2,"Missing rendered motion actors");for(var entry:motionSteps.entrySet()){var stats=motionStats(entry.getValue());require(stats.get("travelled").getAsDouble()>8&&stats.get("longest_idle_ticks").getAsInt()<=10,"Client motion stalled "+entry.getKey()+" "+stats);}
                report.add("client_motion",motion);var serverMotion=new JsonObject();for(var entry:serverMotionSteps.entrySet()){var stats=motionStats(entry.getValue());require(stats.get("samples").getAsInt()>=60&&stats.get("travelled").getAsDouble()>8,"Insufficient real server movement "+entry.getKey()+" "+stats);require(stats.get("longest_idle_ticks").getAsInt()<=3,"Server motion pauses "+entry.getKey()+" "+stats);serverMotion.add(entry.getKey(),stats);}require(serverMotion.size()==2,"Missing server motion actors");require(motionFrameCount>=20,"Missing visible hunter gait frames");report.add("server_continuous_motion",serverMotion);report.addProperty("hunter_gait_frames",motionFrameCount);
                require(wallHeadings.size()==3,"Not all client wall headings were checked");report.addProperty("wall_heading_verified",true);report.addProperty("client_variant_dimensions_verified",true);report.addProperty("client_surface_state_verified",true);closing=true;settler=new NativeChunkSettler.Session("spider-movement-close",2400);write(mc);
            }

        }catch(Throwable failure){
            report.addProperty("error",failure.toString());report.addProperty("passed",false);write(mc);
            if(mc.getSingleplayerServer()!=null&&!closing){closing=true;done=true;settler=new NativeChunkSettler.Session("failed-spider-close",2400);}
            else{stage=9;mc.stop();}
        }
    }
    private static void view(Minecraft mc,double x,double y,double z,float yaw,float pitch){
        // Client-only observation camera. No movement packets or server rubber-banding.
        if(camera==null)camera=new net.minecraft.world.entity.decoration.ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND,mc.level);
        camera.setPos(x,y,z);camera.xo=x;camera.yo=y;camera.zo=z;
        camera.setYRot(yaw);camera.yRotO=yaw;camera.yHeadRot=camera.yHeadRotO=yaw;camera.yBodyRot=camera.yBodyRotO=yaw;camera.setXRot(pitch);camera.xRotO=pitch;
        mc.setCameraEntity(camera);
    }
    private static void write(Minecraft mc){try{Files.writeString(mc.gameDirectory.toPath().resolve("spider-movement-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception error){throw new IllegalStateException(error);}}
    private static void verifyWallHeading(Minecraft mc,CaveRiftSpiderEntity peer) throws Exception{
        var renderer=mc.getEntityRenderDispatcher().getRenderer(peer);
        var method=pro.erez.interstice.entity.client.CaveRiftSpiderRenderer.class.getDeclaredMethod("setupRotations",CaveRiftSpiderEntity.class,com.mojang.blaze3d.vertex.PoseStack.class,float.class,float.class,float.class,float.class);method.setAccessible(true);
        var expected=peer.getCrawlForward().normalize();
        double worst=0;
        for(float yaw:new float[]{peer.yBodyRot,0,45,123}){
            var pose=new com.mojang.blaze3d.vertex.PoseStack();method.invoke(renderer,peer,pose,0F,yaw,1F,1F);
            var heading=pose.last().pose().transformDirection(new org.joml.Vector3f(0,0,-1)).normalize();
            double dot=heading.dot(expected.toVector3f());worst=Math.max(worst,1-dot);
            require(dot>.999,"Actual renderer ground yaw twists vertical wall body: "+peer.getVariant()+" dot="+dot);
        }
        var rows=report.has("wall_heading_frames")?report.getAsJsonArray("wall_heading_frames"):new JsonArray();var row=new JsonObject();
        row.addProperty("variant",peer.getVariant().name());row.addProperty("forward",expected.toString());row.addProperty("worst_dot_error",worst);row.addProperty("ground_yaws_tested",4);rows.add(row);report.add("wall_heading_frames",rows);
    }
    private static void verifyHunterAudio(Minecraft mc)throws Exception{
        var rows=new JsonArray();String[] names={"ambient","alert","windup","attack","hurt","death"};double[] expected={.8,.72,.28,.22,.36,1.2};
        for(int i=0;i<names.length;i++){
            var path=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,"sounds/vault_hunter/"+names[i]+".ogg");
            try(var input=mc.getResourceManager().open(path);var sound=new net.minecraft.client.sounds.JOrbisAudioStream(input)){
                var format=sound.getFormat();var pcm=sound.readAll().order(java.nio.ByteOrder.LITTLE_ENDIAN);int bytes=pcm.remaining(),peak=0;double squares=0;int count=0;
                while(pcm.remaining()>=2){int value=pcm.getShort();peak=Math.max(peak,Math.abs(value));squares+=(double)value*value;count++;}
                double duration=bytes/(format.getFrameRate()*format.getFrameSize());require(format.getChannels()==1&&format.getSampleRate()==44100&&Math.abs(duration-expected[i])<.05,"Invalid native hunter audio "+names[i]);require(peak>128&&peak<32750,"Silent/clipped hunter sound "+names[i]);
                require(mc.getSoundManager().getSoundEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,"vault_hunter."+names[i]))!=null,"Unregistered hunter sound");
                var row=new JsonObject();row.addProperty("sound",names[i]);row.addProperty("seconds",duration);row.addProperty("peak_pcm16",peak);row.addProperty("rms_pcm16",Math.sqrt(squares/count));rows.add(row);
            }
        }report.add("hunter_audio_native_decode",rows);
    }
    private static JsonObject motionStats(List<Double> values){
        var row=new JsonObject();double sum=values.stream().mapToDouble(Double::doubleValue).sum(),mean=sum/values.size();int streak=0,longest=0;for(double step:values){streak=step<.005?streak+1:0;longest=Math.max(longest,streak);}row.addProperty("samples",values.size());row.addProperty("travelled",sum);row.addProperty("mean",mean);row.addProperty("max_step",values.stream().mapToDouble(Double::doubleValue).max().orElse(0));row.addProperty("longest_idle_ticks",longest);return row;
    }
    private static void verifyModel(pro.erez.interstice.fauna.VaultHunter peer){
        require(peer.getBrain() instanceof net.tslat.smartbrainlib.api.core.SmartBrain,"Hunter is not using a real SmartBrain");
        report.addProperty("hunter_uses_smartbrain",true);
        var model=new VaultHunterModel();model.setupAnim(peer,0,0,peer.tickCount,0,0);
        var still=model.root().getAllParts().map(p->List.of(p.x,p.y,p.z,p.xRot,p.yRot,p.zRot)).toList();
        var transform=new com.mojang.blaze3d.vertex.PoseStack();
        transform.scale(VaultHunterModel.SCALE,-VaultHunterModel.SCALE,VaultHunterModel.SCALE);transform.translate(0,-1.501,0);
        double[] bounds={Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY};int[] cubes={0};
        model.root().visit(transform,(pose,path,index,cube)->{
            cubes[0]++;
            for(float x:new float[]{cube.minX,cube.maxX})for(float y:new float[]{cube.minY,cube.maxY})for(float z:new float[]{cube.minZ,cube.maxZ}){
                var p=pose.pose().transformPosition(x/16,y/16,z/16,new org.joml.Vector3f());
                require(Float.isFinite(p.x)&&Float.isFinite(p.y)&&Float.isFinite(p.z),"Nonfinite imported geometry");
                bounds[0]=Math.min(bounds[0],p.y);bounds[1]=Math.max(bounds[1],p.y);
            }
        });
        require(cubes[0]==84,"Lost source cubes during import: "+cubes[0]);
        require(bounds[0]>-.05&&bounds[0]<.05&&bounds[1]>2&&bounds[1]<=peer.getBbHeight(),"Wendigo scale/floor/hitbox invalid: "+Arrays.toString(bounds));
        model.setupAnim(peer,2,.8F,peer.tickCount+10,15,0);
        var moving=model.root().getAllParts().map(p->List.of(p.x,p.y,p.z,p.xRot,p.yRot,p.zRot)).toList();
        require(!still.equals(moving),"Gameplay animation did not move the source skeleton");
        report.addProperty("wendigo_original_cubes",cubes[0]);report.addProperty("wendigo_model_min_y",bounds[0]);report.addProperty("wendigo_model_max_y",bounds[1]);
        report.addProperty("wendigo_geometry_and_animation_verified",true);
    }
}
