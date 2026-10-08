package pro.erez.interstice.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.food.CrownFruitBlock;
import pro.erez.interstice.food.TideHeart;
import pro.erez.interstice.worldgen.*;

/** Natural giant inspection, ordinary hand harvest/eating and real post-restart effect countdown. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class CrownFoodVisualSmoke {
    private static final String MODE=System.getProperty("interstice.crownFoodSmoke","");
    private static final String WORLD="natural-crown-food-check";
    private static boolean started,finished;
    private static long deadline;
    private static int stage,ticks;
    private static CompletableFuture<?> work;
    private static JsonObject data=new JsonObject();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(MODE.isEmpty()||finished)return;var mc=Minecraft.getInstance();
        try{
            if(!started&&mc.screen instanceof TitleScreen){
                started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(8);mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(6);mc.options.framerateLimit().set(60);
                if(MODE.equals("create")){
                    require(!Files.exists(world(mc).resolve("level.dat")),"Disposable save already exists");
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings("Disposable crown and first food",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261006L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
                }else{
                    data=JsonParser.parseString(Files.readString(report(mc,"create"))).getAsJsonObject();require(data.get("passed").getAsBoolean(),"Creation failed");require(data.get("creator_pid").getAsLong()!=ProcessHandle.current().pid(),"Reload must use another JVM");
                    mc.options.hideGui=false;mc.createWorldOpenFlows().openWorld(WORLD,()->{});
                }return;
            }
            if(!started||SmokeWorldPrompts.advance(mc))return;require(System.nanoTime()<deadline,"Crown/food smoke timed out stage "+stage);
            if(mc.player==null||mc.level==null||mc.screen!=null||mc.getConnection()==null)return;var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
            if(stage==0){
                if(MODE.equals("create")){mc.getConnection().sendCommand("interstice explore tall");stage=1;ticks=0;}
                else{work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);require(p.hasEffect(MobEffects.NIGHT_VISION)&&p.hasEffect(TideHeart.REACTION),"Positive phase was not preserved across logout");
                    require(!p.isCreative()&&!p.getAbilities().flying,"Consumption fixture reopened in Creative");
                    var fruit=position();require(p.serverLevel().getBlockState(fruit).getValue(CrownFruitBlock.AGE)<3,"Harvested pod became ripe again on restart");
                    data.addProperty("positive_phase_persisted_after_restart",true);data.addProperty("pod_harvest_persisted",true);data.addProperty("reload_pid",ProcessHandle.current().pid());data.addProperty("reaction_ticks_loaded",p.getEffect(TideHeart.REACTION).getDuration());return true;});stage=10;ticks=0;}return;
            }
            if(!mc.level.dimension().equals(IslandWorld.TALL_WORLD))return;
            if(stage==1&&++ticks>=40){work=server.submit(()->{data=find(server.getLevel(IslandWorld.TALL_WORLD),server.getPlayerList().getPlayer(uuid));return true;});stage=2;}
            else if(stage==2&&done()){stage=3;ticks=0;}
            else if(stage==3&&++ticks>=100){shot(mc,"crown-natural-darkness.png");server.execute(()->server.getPlayerList().getPlayer(uuid).addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,12000,0,false,false)));stage=4;ticks=0;}
            else if(stage==4&&++ticks>=70){
                shot(mc,"crown-natural-giant.png");
                work=server.submit(()->{inspectVine(server.getPlayerList().getPlayer(uuid));return true;});stage=16;ticks=0;
            }else if(stage==16&&done()){stage=17;ticks=0;}
            else if(stage==17&&++ticks>=70){
                shot(mc,"crown-vine-tip.png");
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);var fruit=position();camera(p,p.serverLevel(),fruit.getX()+5.5,fruit.getY()+4,fruit.getZ()-5.5,fruit.getX()+.5,fruit.getY()+.5,fruit.getZ()+.5,true);return true;});stage=14;ticks=0;
            }else if(stage==14&&done()){stage=15;ticks=0;}
            else if(stage==15&&++ticks>=70){
                shot(mc,"crown-top-fruits.png");
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);var fruit=position();
                    p.removeEffect(MobEffects.NIGHT_VISION);server.setDifficulty(Difficulty.NORMAL,true);p.setGameMode(GameType.SURVIVAL);p.getFoodData().setFoodLevel(12);p.getInventory().clearContent();p.getAbilities().flying=false;
                    camera(p,p.serverLevel(),fruit.getX()+.5,fruit.getY(),fruit.getZ()+.5,fruit.getX()+.5,fruit.getY()+.5,fruit.getZ()+1,false);
                    data.addProperty("fixture_player_positioned_on_crown",true);data.addProperty("fixture_hunger_before",12);return true;});stage=5;ticks=0;
            }else if(stage==5&&done()){stage=6;ticks=0;}
            else if(stage==6&&++ticks>=45){
                var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(position()),Direction.UP,position(),false));require(result.consumesAction(),"Ordinary client harvest interaction failed");stage=7;ticks=0;
            }else if(stage==7&&++ticks>=20){
                require(mc.player.getMainHandItem().is(TideHeart.FRUIT.get()),"Real harvest did not place the food into the empty hand");
                mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);stage=8;ticks=0;
            }else if(stage==8&&++ticks>=50){
                mc.options.keyUse.setDown(false);mc.options.hideGui=false;
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);require(p.getFoodData().getFoodLevel()>=18&&p.hasEffect(MobEffects.NIGHT_VISION)&&p.hasEffect(TideHeart.REACTION),"Actual eating did not feed and grant night vision");
                    require(p.getInventory().countItem(GardenMaterials.CROWN_SAPLING.get().asItem())>=1,"Eating did not yield the plantable seed");
                    require(!p.hasEffect(MobEffects.WEAKNESS)&&!p.hasEffect(MobEffects.GLOWING),"Penalty activated during the benefit phase");
                    data.addProperty("ordinary_client_hand_harvest",true);data.addProperty("ordinary_client_food_consumption",true);data.addProperty("hunger_after",p.getFoodData().getFoodLevel());
                    data.addProperty("night_vision_after_eating",true);data.addProperty("plantable_seed_returned",true);data.addProperty("reaction_ticks_at_save",p.getEffect(TideHeart.REACTION).getDuration());return true;});stage=9;ticks=0;
            }else if(stage==9&&done()&&++ticks>=35){shot(mc,"crown-first-meal.png");finish(mc,true,"Natural giant inspected; ordinary hand harvest and eating passed, positive reaction saved");}
            else if(stage==10&&done()){stage=11;ticks=0;}
            else if(stage==11){
                if(++ticks%40==0)System.out.println("CROWN_REACTION_WAIT elapsed_ticks="+ticks);
                // Real server ticks, no forced phase/duration changes.
                if(mc.player.hasEffect(MobEffects.WEAKNESS)&&mc.player.hasEffect(MobEffects.GLOWING)){
                    require(!mc.player.hasEffect(MobEffects.NIGHT_VISION),"Chosen night-vision benefit did not end before the cost");
                    final int waited=ticks;
                    work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);require(TideHeart.isMarkedPrey(p),"Server's future-creature target signal is absent");
                        data.addProperty("real_delayed_weakness",true);data.addProperty("real_delayed_glowing",true);data.addProperty("future_creature_signal",true);data.addProperty("real_ticks_waited_after_reload",waited);return true;});stage=12;ticks=0;
                }
            }else if(stage==12&&done()){shot(mc,"crown-delayed-cost.png");stage=13;ticks=0;}
            else if(stage==13&&++ticks>=40)finish(mc,true,"Cold restart preserved harvest and positive reaction; real countdown produced weakness and visible prey signal");
        }catch(Throwable error){error.printStackTrace();mc.options.keyUse.setDown(false);finish(mc,false,error.toString());}
    }
    private static boolean done(){if(!work.isDone())return false;work.join();return true;}
    private static JsonObject find(ServerLevel level,ServerPlayer player){
        var g=(IslandChunkGenerator)level.getChunkSource().getGenerator();var profile=g.geometry();var sampler=level.getChunkSource().randomState().sampler();
        for(int radius=0;radius<=32;radius++)for(int cx=-radius;cx<=radius;cx++)for(int cz=-radius;cz<=radius;cz++){
            require(System.nanoTime()<deadline,"Giant search deadline");if(Math.max(Math.abs(cx),Math.abs(cz))!=radius||!g.getBiomeSource().getNoiseBiome(cx*4+2,25,cz*4+2,sampler).is(RealmBiomes.PALE_GARDENS))continue;
            var random=RandomSource.create(level.getSeed()^new net.minecraft.world.level.ChunkPos(cx,cz).toLong()^0xCA015L);if(random.nextInt(8)!=0)continue;
            var chunk=level.getChunk(cx,cz);BlockPos selected=null;int count=0,rootY=-1;
            for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++)for(int y=41;y<=205;y++){
                var p=new BlockPos(x,y,z);if(chunk.getBlockState(p).is(GardenMaterials.CROWN_FRUIT.get())){
                    require(IslandChunkGenerator.landAllowed(profile,x,y,z),"Fruit violates sea clearance");count++;if(selected==null||y>selected.getY())selected=p;
                }
            }
            if(selected==null||count<2)continue;
            for(int y=41;y<selected.getY();y++){var p=new BlockPos(cx*16+7,y,cz*16+7);if(chunk.getBlockState(p).is(GardenMaterials.CROWN_LOG.get())&&StoneVaults.isGround(chunk.getBlockState(p.below())))rootY=y;}
            if(rootY<0||selected.getY()-rootY<18)continue;
            JsonObject found=new JsonObject();found.addProperty("creator_pid",ProcessHandle.current().pid());found.addProperty("naturally_generated",true);found.addProperty("seed",level.getSeed());
            found.addProperty("chunk_x",cx);found.addProperty("chunk_z",cz);found.addProperty("fruit_count",count);found.addProperty("root_y",rootY);found.addProperty("fruit_height_above_root",selected.getY()-rootY);
            found.addProperty("fruit_x",selected.getX());found.addProperty("fruit_y",selected.getY());found.addProperty("fruit_z",selected.getZ());found.addProperty("upper_sea_gap",SeaSurface.cellMinimum(profile,selected.getX(),selected.getZ(),true)-(selected.getY()+1));
            found.addProperty("scene_scope","Natural Creative visual inspection, then positioned Survival player for real harvest/eating; not an autonomous climbing route");
            boolean placed=false;
            for(int dy=0;dy<4&&!placed;dy++)for(int[] off:new int[][]{{16,-16},{-16,-16},{16,16},{-16,16}}){
                double x=cx*16+7+off[0]+.5,y=rootY+12+dy,z=cz*16+7+off[1]+.5;
                if(y+2>=SeaSurface.cellMinimum(profile,(int)Math.floor(x),(int)Math.floor(z),true)-1||!level.getBlockState(BlockPos.containing(x,y,z)).isAir()||!level.getBlockState(BlockPos.containing(x,y+1,z)).isAir())continue;
                camera(player,level,x,y,z,cx*16+7.5,rootY+15,cz*16+7.5,true);placed=true;break;
            }
            require(placed,"No clear giant-tree inspection camera");return found;
        }throw new IllegalStateException("No naturally generated fruiting giant");
    }
    private static void camera(ServerPlayer p,ServerLevel l,double x,double y,double z,double tx,double ty,double tz,boolean fly){double dx=tx-x,dz=tz-z,dy=ty-(y+p.getEyeHeight());p.teleportTo(l,x,y,z,Set.of(),(float)(Math.toDegrees(Math.atan2(dz,dx))-90),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));p.setDeltaMovement(0,0,0);p.getAbilities().flying=fly;p.onUpdateAbilities();}
    private static void inspectVine(ServerPlayer player){
        var level=player.serverLevel();int cx=data.get("chunk_x").getAsInt(),cz=data.get("chunk_z").getAsInt(),root=data.get("root_y").getAsInt();
        for(int y=root;y<position().getY();y++)for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++){
            var p=new BlockPos(x,y,z);var state=level.getBlockState(p);
            if(!state.is(GardenMaterials.PALE_VINE.get())||level.getBlockState(p.below()).is(GardenMaterials.PALE_VINE.get()))continue;
            require(state.getValue(GardenVineBlock.SECTION)==3||state.getValue(GardenVineBlock.SECTION)==4,"Natural vine has a truncated half-tip");
            for(int[] off:new int[][]{{3,-3},{-3,-3},{3,3},{-3,3}}){
                double px=x+off[0]+.5,py=y-.5,pz=z+off[1]+.5;
                if(!level.getBlockState(BlockPos.containing(px,py,pz)).isAir()||!level.getBlockState(BlockPos.containing(px,py+1,pz)).isAir())continue;
                camera(player,level,px,py,pz,x+.5,y+1,z+.5,true);data.addProperty("natural_vine_tip_section",state.getValue(GardenVineBlock.SECTION));return;
            }
        }throw new IllegalStateException("No inspectable finished vine on the natural giant");
    }
    private static BlockPos position(){return new BlockPos(data.get("fruit_x").getAsInt(),data.get("fruit_y").getAsInt(),data.get("fruit_z").getAsInt());}
    private static java.nio.file.Path world(Minecraft mc){return mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);}
    private static java.nio.file.Path report(Minecraft mc,String mode){return mc.gameDirectory.toPath().resolve("crown-"+mode+"-validation.json");}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),msg->System.out.println("CROWN_SCREENSHOT "+name));}
    private static void require(boolean yes,String reason){if(!yes)throw new IllegalStateException(reason);}
    private static void finish(Minecraft mc,boolean passed,String why){if(finished)return;finished=true;data.addProperty("passed",passed);data.addProperty("mode",MODE);data.addProperty("stage",stage);data.addProperty("reason",why);try{Files.writeString(report(mc,MODE),new GsonBuilder().setPrettyPrinting().create().toJson(data));}catch(Exception error){error.printStackTrace();}System.out.println("CROWN_VALIDATION "+data);mc.stop();}
}
