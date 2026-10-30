package pro.erez.interstice.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.*;
import pro.erez.interstice.worldgen.*;

/** Observe the real AI, without injecting targets, crawl routes or attack calls. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class SpiderBehaviourNative {
    private static final boolean ACTIVE=System.getProperty("interstice.studioSmoke","").equals("spiderbehaviour");
    private static final String SAVE="spider-behaviour-owned";
    private static final JsonObject REPORT=new JsonObject();
    private static final JsonArray RESULTS=new JsonArray(),SERIES=new JsonArray(),CHECKS=new JsonArray(),SOUNDS=new JsonArray(),DAMAGE=new JsonArray(),SHOTS=new JsonArray();
    private enum Stage {
        IDLE(400),CREATIVE(60),SPECTATOR(60),SURVIVAL_FAR(400),SURVIVAL_NEAR(180),HIDDEN(240),REACQUIRE(100),DISABLED(80),RESUME(100),DEAD(60);
        final int duration;Stage(int duration){this.duration=duration;}
    }
    private static volatile boolean started,prepared,closing,finished;
    private static volatile String capture;
    private static volatile int variantIndex,stageIndex;
    private static long deadline;
    private static ServerLevel level;
    private static ServerPlayer player;
    private static CaveRiftSpiderEntity spider;
    private static ArmorStand camera;
    private static Stage stage;
    private static int stageStart,movingTicks,samples,targetTicks,bursts,wallTicks,ceilingTicks,shots,hits,soundCount,ambient,firstTargetTick;
    private static double travel,peakSound,peakQuietSound,maxStep;
    private static int impactSounds;
    private static Vec3 previous,remembered;
    private static BlockPos hiding;
    private static final Set<String> tactics=new LinkedHashSet<>();
    private static NativeChunkSettler.Session settling;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static void check(String name,boolean ok,String detail){var row=new JsonObject();row.addProperty("variant",spider.getVariant().name());row.addProperty("stage",stage.name());row.addProperty("check",name);row.addProperty("passed",ok);row.addProperty("detail",detail);CHECKS.add(row);}
    private static JsonArray point(Vec3 p){var a=new JsonArray();a.add(p.x);a.add(p.y);a.add(p.z);return a;}
    private static void write(){try{REPORT.add("results",RESULTS);REPORT.add("checks",CHECKS);REPORT.add("samples",SERIES);REPORT.add("sounds",SOUNDS);REPORT.add("damage",DAMAGE);REPORT.add("screenshots",SHOTS);Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("spider-behaviour.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void finish(String error){
        if(closing)return;closing=true;REPORT.addProperty("error",error);REPORT.addProperty("passed",error.isEmpty()&&CHECKS.asList().stream().allMatch(x->x.getAsJsonObject().get("passed").getAsBoolean()));
        REPORT.addProperty("owner_profile_used",false);REPORT.addProperty("prepared_fixture_not_survival",true);REPORT.addProperty("automatic_targeting_and_attacks",true);REPORT.addProperty("target_attack_route_injection",false);REPORT.addProperty("server_pid",ProcessHandle.current().pid());
        write();settling=new NativeChunkSettler.Session("spider_behaviour_shutdown",2200);
    }
    private static void prepare(Minecraft mc){
        var server=mc.getSingleplayerServer();level=server.getLevel(IslandWorld.TENSION_WORLD);
        require(level.getChunkSource().getGenerator() instanceof IslandChunkGenerator g&&g.terrainRevision()==6,"Actual V6 required");
        REPORT.addProperty("actual_dimension",level.dimension().location().toString());REPORT.addProperty("terrain_revision",6);
        player=server.getPlayerList().getPlayer(mc.player.getUUID());player.getInventory().clearContent();
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);player.setHealth(1000);
        player.setNoGravity(true);player.teleportTo(level,23.5,70,20.5,Set.of(),180,0);player.hasChangedDimension();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,server);
        variantIndex=0;newVariant();prepared=true;
    }
    private static void clearProjectiles(){if(spider!=null)level.getEntitiesOfClass(ToxinSpitEntity.class,spider.getBoundingBox().inflate(96),e->e.getOwner()==spider).forEach(Entity::discard);}
    private static void newVariant(){
        player.setGameMode(GameType.CREATIVE);player.removeAllEffects();clearProjectiles();if(spider!=null)spider.discard();
        for(int x=-12;x<=40;x++)for(int z=-12;z<=40;z++)for(int y=69;y<=80;y++)
            level.setBlock(new BlockPos(x,y,z),(y==69?(x%4==0&&z%4==0?Blocks.SEA_LANTERN:Blocks.SMOOTH_STONE):Blocks.AIR).defaultBlockState(),3);
        var variant=CaveRiftSpiderEntity.Variant.values()[variantIndex];
        if(variant==CaveRiftSpiderEntity.Variant.LURKER){
            for(int y=70;y<=77;y++)for(int z=-2;z<=14;z++)level.setBlock(new BlockPos(0,y,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
            for(int x=0;x<=16;x++)for(int z=-2;z<=14;z++)level.setBlock(new BlockPos(x,77,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
        }
        spider=Interstice.CAVE_RIFT_SPIDER.get().create(level);spider.setVariant(variant);spider.setPersistenceRequired();spider.moveTo(3.5,70,5.5);level.addFreshEntity(spider);
        stageIndex=0;enter(Stage.IDLE);
    }
    private static void placePlayer(double offset){
        player.removeAllEffects();player.setHealth(1000);player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(level,spider.getX()+offset,70,spider.getZ(),Set.of(),180,0);
    }
    private static void cover(boolean build){
        if(hiding==null)return;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=0;dy<=5;dy++)
            if(Math.abs(dx)==1||Math.abs(dz)==1||dy==5)level.setBlock(hiding.offset(dx,dy,dz),(build?Blocks.SMOOTH_STONE:Blocks.AIR).defaultBlockState(),3);
    }
    private static void enter(Stage next){
        stage=next;stageStart=level.getServer().getTickCount();movingTicks=samples=targetTicks=bursts=wallTicks=ceilingTicks=shots=hits=soundCount=ambient=0;travel=peakSound=peakQuietSound=maxStep=0;impactSounds=0;firstTargetTick=-1;tactics.clear();previous=spider.position();
        switch(stage){
            case IDLE -> {player.setGameMode(GameType.CREATIVE);placePlayer(18);}
            case CREATIVE -> {player.setGameMode(GameType.CREATIVE);placePlayer(2);}
            case SPECTATOR -> {player.setGameMode(GameType.SPECTATOR);placePlayer(2);}
            case SURVIVAL_FAR -> {player.setGameMode(GameType.SURVIVAL);placePlayer(9);}
            case SURVIVAL_NEAR -> placePlayer(2);
            case HIDDEN -> {
                remembered=SpiderBrain.observed(spider);hiding=BlockPos.containing(spider.getX()+8,70,spider.getZ()+6);cover(true);
                player.teleportTo(level,hiding.getX()+.5,70,hiding.getZ()+.5,Set.of(),0,0);player.setDeltaMovement(Vec3.ZERO);
            }
            case REACQUIRE -> {cover(false);placePlayer(2);}
            case DISABLED -> {clearProjectiles();spider.setNoAi(true);}
            case RESUME -> {spider.setNoAi(false);placePlayer(2);}
            case DEAD -> {clearProjectiles();spider.kill();}
        }
        System.out.println("SPIDER_BEHAVIOUR "+spider.getVariant()+" "+stage+" tick="+stageStart+" position="+spider.position());
        write();
    }
    private static void sample(int elapsed){
        double step=previous.distanceTo(spider.position());previous=spider.position();travel+=step;maxStep=Math.max(maxStep,step);if(step>.003)movingTicks++;samples++;
        if(spider.getTarget()==player){targetTicks++;if(firstTargetTick<0)firstTargetTick=elapsed;}
        if(spider.isBursting())bursts++;if(spider.isClimbingWall())wallTicks++;if(spider.isClimbingCeiling())ceilingTicks++;
        tactics.add(spider.combatTactic());
        if(elapsed%10==0){var row=new JsonObject();row.addProperty("variant",spider.getVariant().name());row.addProperty("stage",stage.name());row.addProperty("elapsed",elapsed);row.add("position",point(spider.position()));row.addProperty("target_player",spider.getTarget()==player);row.addProperty("tactic",spider.combatTactic());row.addProperty("surface",spider.getSurface().name());row.addProperty("burst",spider.isBursting());row.addProperty("navigation_done",spider.getNavigation().isDone());row.addProperty("player_health",player.getHealth());row.add("player_position",point(player.position()));row.addProperty("distance_to_player",spider.distanceTo(player));if(SpiderBrain.observed(spider)!=null)row.add("remembered",point(SpiderBrain.observed(spider)));SERIES.add(row);}
        if(stage==Stage.HIDDEN&&elapsed==15){check("hidden_identity_erased",spider.getTarget()==null,"Target must disappear behind sealed cover");var obs=SpiderBrain.observed(spider);check("hidden_position_not_tracked",obs==null||remembered!=null&&obs.distanceTo(remembered)<.01,"Last seen="+remembered+" current="+obs);}
        if(elapsed==stage.duration-20&&stage!=Stage.DISABLED&&stage!=Stage.DEAD)capture=spider.getVariant().name().toLowerCase(Locale.ROOT)+"-"+stage.name().toLowerCase(Locale.ROOT);
    }
    private static void endStage(){
        var row=new JsonObject();row.addProperty("variant",spider.getVariant().name());row.addProperty("stage",stage.name());row.addProperty("ticks",samples);row.addProperty("travel_blocks",travel);row.addProperty("moving_ticks",movingTicks);row.addProperty("moving_fraction",(double)movingTicks/Math.max(1,samples));row.addProperty("max_step_blocks",maxStep);row.addProperty("target_ticks",targetTicks);row.addProperty("first_target_tick",firstTargetTick);row.addProperty("burst_ticks",bursts);row.addProperty("wall_ticks",wallTicks);row.addProperty("ceiling_ticks",ceilingTicks);row.addProperty("projectiles_spawned",shots);row.addProperty("damage_events",hits);row.addProperty("sound_events",soundCount);row.addProperty("peak_sound_volume",peakSound);row.addProperty("peak_vocal_and_step_volume",peakQuietSound);row.addProperty("physical_impact_events",impactSounds);row.add("tactics",new Gson().toJsonTree(tactics));RESULTS.add(row);
        check("no_periodic_ambient_hiss",ambient==0,"Ambient events="+ambient);
        check("quiet_vocal_and_step_volume",peakQuietSound<=.15001,"Vocal/step peak="+peakQuietSound+"; physical impacts="+impactSounds+"; raw peak="+peakSound);
        switch(stage){
            case IDLE -> {check("no_target_without_enemy",targetTicks==0&&hits==0&&shots==0,"Only creative observer present");if(spider.getVariant()==CaveRiftSpiderEntity.Variant.LURKER)check("autonomous_ceiling_ambush",ceilingTicks>60&&!spider.isBursting(),"Ceiling ticks="+ceilingTicks);}
            case CREATIVE,SPECTATOR -> check("ignore_non_survival_player",targetTicks==0&&hits==0&&shots==0,"target ticks="+targetTicks);
            case SURVIVAL_FAR -> {check("variant_detection_range",spider.getVariant()==CaveRiftSpiderEntity.Variant.LURKER?targetTicks==0:targetTicks>0,"target ticks="+targetTicks);if(spider.getVariant()==CaveRiftSpiderEntity.Variant.SKIRMISHER)check("pursuit_reaches_real_damage",hits>0,"Damage events="+hits+" distance="+spider.distanceTo(player));}
            case SURVIVAL_NEAR -> {check("automatic_close_detection",targetTicks>0,"First target at="+firstTargetTick);if(spider.getVariant()==CaveRiftSpiderEntity.Variant.LURKER)check("ambush_burst_and_drop",bursts>0&&spider.getY()<73,"Burst ticks="+bursts+" y="+spider.getY());if(spider.getVariant()==CaveRiftSpiderEntity.Variant.SPITTER)check("ranged_retreat_and_shot",shots>0&&tactics.contains("RETREAT"),"shots="+shots+" tactics="+tactics);else check("real_automatic_melee_damage",hits>0,"Damage events="+hits);}
            case HIDDEN -> check("finite_last_seen_memory",spider.getTarget()==null&&SpiderBrain.observed(spider)==null&&spider.combatTactic().equals("WAIT"),"tactic="+spider.combatTactic()+" observed="+SpiderBrain.observed(spider));
            case REACQUIRE,RESUME -> check("automatic_reacquisition",targetTicks>0,"Target ticks="+targetTicks);
            case DISABLED,DEAD -> check("no_attacks_after_stop",shots==0&&hits==0,"shots="+shots+" hits="+hits);
        }
        write();
    }
    private static boolean emittedBy(PlayLevelSoundEvent event){
        return event instanceof PlayLevelSoundEvent.AtEntity e&&e.getEntity()==spider
            ||event instanceof PlayLevelSoundEvent.AtPosition pos&&pos.getPosition().distanceToSqr(spider.position())<.000001;
    }
    @SubscribeEvent public static void sound(PlayLevelSoundEvent event){
        if(!ACTIVE||!prepared||closing||event.getLevel().isClientSide||!emittedBy(event))return;
        soundCount++;peakSound=Math.max(peakSound,event.getNewVolume());String id=event.getSound()==null?"null":event.getSound().value().getLocation().toString();if(id.equals("minecraft:entity.spider.ambient"))ambient++;
        boolean impact=id.endsWith(".fall")||id.equals("minecraft:entity.hostile.small_fall")||id.equals("minecraft:entity.hostile.big_fall");if(impact)impactSounds++;else peakQuietSound=Math.max(peakQuietSound,event.getNewVolume());
        var row=new JsonObject();row.addProperty("variant",spider.getVariant().name());row.addProperty("stage",stage.name());row.addProperty("tick",level.getServer().getTickCount());row.addProperty("sound",id);row.addProperty("volume",event.getNewVolume());SOUNDS.add(row);
    }
    @SubscribeEvent public static void damage(LivingDamageEvent.Post event){
        if(!ACTIVE||!prepared||closing||event.getEntity()!=player||event.getSource().getEntity()!=spider)return;
        if(event.getSource().getMsgId().endsWith("spider_bite"))check("native_melee_faces_victim",spider.isFacingAttackTarget(player),"Alignment="+spider.attackAlignment(player));
        hits++;var row=new JsonObject();row.addProperty("variant",spider.getVariant().name());row.addProperty("stage",stage.name());row.addProperty("tick",level.getServer().getTickCount());row.addProperty("type",event.getSource().getMsgId());row.addProperty("damage",event.getNewDamage());row.addProperty("attack_alignment",spider.attackAlignment(player));row.add("attack_forward",point(spider.getAttackForward()));DAMAGE.add(row);
    }
    @SubscribeEvent public static void spawn(EntityJoinLevelEvent event){if(ACTIVE&&prepared&&!closing&&event.getLevel()==level&&event.getEntity() instanceof ToxinSpitEntity spit&&spit.getOwner()==spider){shots++;check("native_projectile_launched_frontally",spider.isFacingAttackTarget(player),"Alignment="+spider.attackAlignment(player));}}
    @SubscribeEvent public static void server(ServerTickEvent.Post event){
        if(!ACTIVE||finished)return;
        if(closing){settling.tick(event.getServer());if(settling.ready()||settling.failed()){REPORT.add("settled",settling.report());if(settling.failed()){REPORT.addProperty("passed",false);REPORT.addProperty("error",settling.failure());}finished=true;write();Minecraft.getInstance().stop();}return;}
        if(!prepared)return;
        try {
            int elapsed=event.getServer().getTickCount()-stageStart;sample(elapsed);
            if(elapsed>=stage.duration){endStage();if(++stageIndex<Stage.values().length)enter(Stage.values()[stageIndex]);else if(++variantIndex<CaveRiftSpiderEntity.Variant.values().length)newVariant();else finish("");}
        }catch(Throwable error){error.printStackTrace();finish(error.toString());}
    }
    private static void view(Minecraft mc){
        if(spider==null||mc.level.getEntity(spider.getId())==null)return;var peer=mc.level.getEntity(spider.getId());
        if(camera==null)camera=new ArmorStand(EntityType.ARMOR_STAND,mc.level);camera.setInvisible(true);
        double x=peer.getX()+6,z=peer.getZ()+7,y=peer.getY()+3;
        if(spider.isClimbingCeiling())y=73.5;
        var delta=peer.getBoundingBox().getCenter().subtract(x,y,z);float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance()));
        double feet=y-camera.getEyeHeight();camera.setPos(x,feet,z);camera.xo=x;camera.yo=feet;camera.zo=z;camera.setYRot(yaw);camera.yRotO=yaw;camera.yHeadRot=camera.yHeadRotO=yaw;camera.yBodyRot=camera.yBodyRotO=yaw;camera.setXRot(pitch);camera.xRotO=pitch;mc.setCameraEntity(camera);
    }
    @SubscribeEvent public static void client(ClientTickEvent.Post event){
        if(!ACTIVE||finished)return;var mc=Minecraft.getInstance();
        try {
            if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                require(mc.gameDirectory.toPath().toRealPath().toString().replace('\\','/').contains("/.verification/"),"Owned profile only");
                require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/"+SAVE)),"Fresh world only");
                for(String id:List.of("distanthorizons","create","sable","smartbrainlib","azurecortex"))require(ModList.get().isLoaded(id),"Missing full-pack mod "+id);
                started=true;deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(12);REPORT.addProperty("distant_horizons_loaded",true);REPORT.addProperty("mod_count",ModList.get().getMods().size());REPORT.addProperty("version",ModList.get().getModContainerById(Interstice.ID).orElseThrow().getModInfo().getVersion().toString());
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.fov().set(60);mc.options.bobView().set(false);
                var rules=new GameRules();rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel(SAVE,new LevelSettings("Owned spider behaviour",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(2026102802L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
            }
            if(!started||closing)return;require(System.nanoTime()<deadline,"Native deadline");
            if(mc.getOverlay()!=null||SmokeWorldPrompts.advance(mc)||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;mc.getToasts().clear();
            if(!prepared){if(level==null){level=mc.getSingleplayerServer().overworld();mc.getSingleplayerServer().execute(()->{try{prepare(mc);}catch(Throwable error){error.printStackTrace();finish(error.toString());}});}return;}
            if(!mc.level.dimension().equals(IslandWorld.TENSION_WORLD))return;view(mc);
            var requested=capture;if(requested!=null){capture=null;String filename="spider-behaviour-"+requested+".png";Screenshot.grab(mc.gameDirectory,filename,mc.getMainRenderTarget(),unused->{});synchronized(SHOTS){SHOTS.add(filename);}}
        }catch(Throwable error){error.printStackTrace();if(mc.getSingleplayerServer()!=null)mc.getSingleplayerServer().execute(()->finish(error.toString()));else{REPORT.addProperty("passed",false);REPORT.addProperty("error",error.toString());write();mc.stop();}}
    }
}
