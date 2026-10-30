package pro.erez.interstice.client;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.client.LozhnokorenModel;
import pro.erez.interstice.fauna.*;
import pro.erez.interstice.worldgen.*;

@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class LozhnokorenNative {
    private static final boolean ACTIVE=System.getProperty("interstice.studioSmoke","").equals("lozhnokoren");
    private static final boolean REVIEW=Boolean.getBoolean("interstice.lozhnokorenReview");
    private static final JsonObject REPORT=new JsonObject();private static final JsonArray SHOTS=new JsonArray();
    private static boolean started,finished,closing,pauseWarning,pauseImpact;private static int phase,ticks,waits;private static long deadline,seaSince;
    private static volatile float peakTurnBend;private static boolean turnPhoto;private static float peakClientBend;
    private static CompletableFuture<?> job;private static ServerLevel level;private static LivingEntity camera;private static Lozhnokoren root;private static Zombie prey;private static double walkStart;private static float seaHealth;private static NativeChunkSettler.Session settling;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static void shot(Minecraft mc,String label){String f="lozhnokoren-"+label+".png";Screenshot.grab(mc.gameDirectory,f,mc.getMainRenderTarget(),unused->{});SHOTS.add(f);}
    private static void view(Minecraft mc,double x,double y,double z,float yaw,float pitch){if(camera==null)camera=new Zombie(EntityType.ZOMBIE,mc.level);camera.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,200000,0,false,false));double feet=y-camera.getEyeHeight();camera.setPos(x,feet,z);camera.xo=x;camera.yo=feet;camera.zo=z;camera.setYRot(yaw);camera.yRotO=yaw;camera.yBodyRot=camera.yBodyRotO=camera.yHeadRot=camera.yHeadRotO=yaw;camera.setXRot(pitch);camera.xRotO=pitch;mc.setCameraEntity(camera);}
    private static void write(Minecraft mc){try{Files.writeString(mc.gameDirectory.toPath().resolve("lozhnokoren.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}
    private static ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static void submit(Minecraft mc,Runnable action){job=mc.getSingleplayerServer().submit(()->{action.run();return true;});ticks=0;}
    private static void finish(Minecraft mc,boolean passed,String reason){if(closing||finished)return;REPORT.addProperty("passed",passed);REPORT.addProperty("reason",reason);REPORT.addProperty("pid",ProcessHandle.current().pid());REPORT.addProperty("phase",phase);REPORT.addProperty("owner_profile_used",false);REPORT.addProperty("prepared_fixture_not_survival",true);REPORT.add("screenshots",SHOTS);write(mc);closing=true;settling=new NativeChunkSettler.Session("lozhnokoren_shutdown",2400);}
    private static void prepare(Minecraft mc){var server=mc.getSingleplayerServer();level=server.getLevel(IslandWorld.TENSION_WORLD);require(level.getChunkSource().getGenerator() instanceof IslandChunkGenerator g&&g.terrainRevision()==6,"Actual V6 required");level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        for(int x=-12;x<=32;x++)for(int z=-12;z<=36;z++)for(int y=69;y<=78;y++)level.setBlock(new BlockPos(x,y,z),(y==69?(x%4==0&&z%4==0?Blocks.SEA_LANTERN:Blocks.SMOOTH_STONE):Blocks.AIR).defaultBlockState(),2);
        root=LozhnokorenFauna.TYPE.get().create(level);root.moveTo(8.5,70,8.5);root.setYRot(0);root.setNoAi(true);root.setPersistenceRequired();level.addFreshEntity(root);
        var p=player(mc);p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();p.getAbilities().flying=true;p.onUpdateAbilities();p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,200000,0,false,false));p.teleportTo(level,15.5,70,8.5,Set.of(),0,0);p.hasChangedDimension();
    }
    private static class Counter implements VertexConsumer {int vertices;float minY=Float.POSITIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;boolean finite=true;public VertexConsumer addVertex(float x,float y,float z){vertices++;minY=Math.min(minY,y);maxY=Math.max(maxY,y);finite&=Float.isFinite(x+y+z);return this;}public VertexConsumer setColor(int r,int g,int b,int a){return this;}public VertexConsumer setUv(float u,float v){finite&=u>=0&&u<=1&&v>=0&&v<=1;return this;}public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}public VertexConsumer setNormal(float x,float y,float z){finite&=Math.abs(x*x+y*y+z*z-1)<.001;return this;}}
    private static List<List<Float>> pose(LozhnokorenModel m){return m.root().getAllParts().map(p->List.of(p.x,p.y,p.z,p.xRot,p.yRot,p.zRot,p.xScale,p.yScale,p.zScale)).toList();}
    private static void debugBounds(Minecraft mc,Lozhnokoren peer){
        try {
            var draw=net.minecraft.client.renderer.entity.EntityRenderDispatcher.class.getDeclaredMethod("renderHitbox",PoseStack.class,VertexConsumer.class,Entity.class,float.class,float.class,float.class,float.class);draw.setAccessible(true);
            for(float partial:new float[]{0,.25f,.5f,.75f,1}){
                var c=new Counter(){@Override public VertexConsumer setNormal(float x,float y,float z){return this;}};draw.invoke(null,new PoseStack(),c,peer,partial,1f,1f,1f);
                require(c.vertices>=256&&c.finite&&c.minY>-2&&c.maxY<6,"F3+B vertices="+c.vertices+" finite="+c.finite+" localY="+c.minY+".."+c.maxY);
            }
            REPORT.addProperty("actual_f3b_renderer_five_partial_frames",true);
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    private static void animationTransitions(Minecraft mc,LozhnokorenModel model){
        try{
            var actor=LozhnokorenFauna.TYPE.get().create(mc.level);
            var change=Lozhnokoren.class.getDeclaredMethod("phase",Lozhnokoren.Phase.class);change.setAccessible(true);
            float time=0,previous=0,maxStep=0;int samples=0;
            for(var state:List.of(Lozhnokoren.Phase.CAMOUFLAGED,Lozhnokoren.Phase.WARNING,Lozhnokoren.Phase.PURSUIT,Lozhnokoren.Phase.STRIKE,Lozhnokoren.Phase.RECOVER,Lozhnokoren.Phase.WARNING,Lozhnokoren.Phase.RETURN,Lozhnokoren.Phase.CAMOUFLAGED)){
                actor.tickCount=(int)time;change.invoke(actor,state);
                int duration=state==Lozhnokoren.Phase.WARNING?32:state==Lozhnokoren.Phase.STRIKE?20:16;
                for(int frame=0;frame<duration*4;frame++){
                    model.setupAnim(actor,time*.3f,.1f,time,0,0);float angle=model.joint("left_grasp_shoulder").xRot;
                    if(samples++>0){float step=Math.abs(angle-previous);maxStep=Math.max(step,maxStep);require(step<.12f,"Claw discontinuity in "+state+": "+step);}
                    if(state==Lozhnokoren.Phase.PURSUIT&&frame>=32)require(Math.abs(angle+(float)Math.toRadians(45))<.001,"Pursuit dropped raised claw");
                    if(state==Lozhnokoren.Phase.STRIKE&&frame==32)require(Math.abs(angle)<.001,"Claw did not reach impact pose at tick8");
                    previous=angle;time+=.25f;
                }
            }
            var result=new JsonObject();result.addProperty("samples",samples);result.addProperty("max_shoulder_step_radians",maxStep);result.addProperty("warning_pursuit_strike_recovery_cancel",true);REPORT.add("animation_continuity",result);
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    private static void inspect(Minecraft mc,String label){require(mc.level.getEntity(root.getId()) instanceof Lozhnokoren,"Synced client entity");var peer=(Lozhnokoren)mc.level.getEntity(root.getId());var renderer=(LozhnokorenClient.Renderer)mc.getEntityRenderDispatcher().getRenderer(peer);var model=renderer.getModel();require(model.sourceCubes()==357&&model.sourceQuads()==2142&&model.root().getAllParts().count()==88,"Complete authored LK01");model.root().getAllParts().forEach(net.minecraft.client.model.geom.ModelPart::resetPose);var rest=pose(model);model.clip("Crawl",.43f,1);require(!rest.equals(pose(model)),"Source crawl animates native joints");model.root().getAllParts().forEach(net.minecraft.client.model.geom.ModelPart::resetPose);model.clip("Threat",.7f,1);require(Math.abs(model.joint("left_grasp_shoulder").xRot+Math.toRadians(45))<.0001,"Exact original threat shoulder key");require(model.root().getAllParts().allMatch(p->p.xScale==1&&p.yScale==1&&p.zScale==1),"No scaling/deforming of original geometry");var c=new Counter();model.renderToBuffer(new PoseStack(),c,0xf000f0,0,-1);require(c.vertices==8568&&c.finite,"All2142 authored faces render");require(peer.getParts().length==15,"Client multipart body");for(int i=0;i<15;i++)require(peer.getParts()[i].getId()==peer.getId()+i+1,"Client/server part ids agree");var row=new JsonObject();row.addProperty("cuboids",357);row.addProperty("bones",88);row.addProperty("vertices",c.vertices);row.addProperty("source_sha256",model.sourceHash());row.addProperty("mesh_sha256",model.meshHash());row.addProperty("source_threat_key_exact",true);row.addProperty("parts",15);REPORT.add(label,row);animationTransitions(mc,model);debugBounds(mc,peer);mc.getEntityRenderDispatcher().setRenderHitBoxes(true);}
    private static void target(){root.setNoAi(false);root.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);prey=new Zombie(EntityType.ZOMBIE,level);prey.setNoAi(true);prey.setNoGravity(true);prey.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.CARVED_PUMPKIN));prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);prey.setHealth(1000);prey.moveTo(8.5,70,13.5);level.addFreshEntity(prey);root.setTarget(prey);for(int y=70;y<=72;y++)level.setBlock(new BlockPos(8,y,14),Interstice.GLOOMCROWN_LOG.get().defaultBlockState(),2);level.setBlock(new BlockPos(8,73,14),Interstice.GLOOMCROWN_LEAVES.get().defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT,false).setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE,1),2);pauseWarning=true;}
    private static void sea(boolean upper){root.setNoAi(true);root.setNoGravity(true);root.setTarget(null);root.moveTo(-6,70,11);root.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);for(int x=-10;x<=-2;x++)for(int z=5;z<=17;z++)for(int y=69;y<=74;y++)level.setBlock(new BlockPos(x,y,z),(y==69||x==-10||x==-2||z==5||z==17?Blocks.SMOOTH_STONE.defaultBlockState():y<=73?(upper?Interstice.LIGHT_BLOCK:Interstice.HEAVY_BLOCK).get().defaultBlockState():Blocks.AIR.defaultBlockState()),2);seaHealth=root.getHealth();seaSince=level.getGameTime();}
    private static void seaResult(boolean upper){require(level.getGameTime()-seaSince>=80,"Actual server contact duration");REPORT.addProperty(upper?"upper_contact_server_ticks":"lower_contact_server_ticks",level.getGameTime()-seaSince);require(level.getFluidState(root.blockPosition()).getFluidType()==(upper?Interstice.LIGHT_TYPE:Interstice.HEAVY_TYPE).get(),"Actual sea contact");require(root.getHealth()==seaHealth&&root.getActiveEffects().isEmpty(),"Sea toxin, vapor or drowning harmed root");REPORT.addProperty(upper?"upper_sea_contact_80_ticks":"lower_sea_contact_80_ticks",true);}
    private static void prepareNaturalSpawn(Minecraft mc){
        if(prey!=null)prey.discard();root.discard();
        for(int x=0;x<=16;x++)for(int z=0;z<=16;z++)level.setBlock(new BlockPos(x,69,z),Interstice.ABYSSAL_TURF.get().defaultBlockState(),2);
        var source=level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();level.getServer().getCommands().performPrefixedCommand(source,"fillbiome -4 64 -4 20 80 20 interstice:crimson_thickets");
        var p=player(mc);p.setGameMode(GameType.SURVIVAL);p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(level,8.5,75,50,Set.of(),180,0);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());
    }
    private static void verifyNaturalSpawn(Minecraft mc){
        var at=new BlockPos(8,70,8);require(LozhnokorenFauna.habitat(level,at),"Actual V6 fixture forest habitat");require(LozhnokorenFauna.trySpawn(level,at),"Real registered natural-spawn path with budget/player/footprint gates");
        root=level.getEntitiesOfClass(Lozhnokoren.class,new net.minecraft.world.phys.AABB(at).inflate(5)).stream().filter(Entity::isAlive).findFirst().orElseThrow();root.setPersistenceRequired();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());player(mc).setGameMode(GameType.CREATIVE);REPORT.addProperty("natural_spawn_pipeline_controlled_forest_fixture",true);REPORT.addProperty("natural_spawn_rate_or_survival_acceptance",false);
    }
    private static void reviewReady(Minecraft mc){if(prey!=null)prey.discard();root.setNoAi(false);root.setNoGravity(false);root.setTarget(null);root.setHealth(root.getMaxHealth());root.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.16);root.moveTo(8.5,70,8.5);root.setYRot(0);root.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        var p=player(mc);p.getInventory().clearContent();p.getInventory().add(new ItemStack(Items.NETHERITE_SWORD));p.getInventory().add(new ItemStack(Items.SHIELD));p.getInventory().add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE,8));p.getInventory().add(new ItemStack(LozhnokorenFauna.EGG.get(),4));p.getInventory().add(new ItemStack(Items.NETHERITE_HELMET));p.getInventory().add(new ItemStack(Items.NETHERITE_CHESTPLATE));p.getInventory().add(new ItemStack(Items.NETHERITE_LEGGINGS));p.getInventory().add(new ItemStack(Items.NETHERITE_BOOTS));p.teleportTo(level,8.5,73,24,Set.of(),180,10);p.getAbilities().flying=true;p.onUpdateAbilities();REPORT.addProperty("review_world_ready",true);
    }
    @SubscribeEvent public static void server(ServerTickEvent.Post e){if(!ACTIVE||REVIEW||finished)return;if(closing){settling.tick(e.getServer());if(settling.ready()||settling.failed()){if(settling.failed()){REPORT.addProperty("passed",false);REPORT.addProperty("reason",settling.failure());}REPORT.add("settled",settling.report());finished=true;write(Minecraft.getInstance());Minecraft.getInstance().stop();}return;}if(root==null)return;
        if(phase==18){float bend=0;for(int i=1;i<11;i++)bend+=root.bodyJointAngle(i,1);if(Math.abs(bend)>Math.abs(peakTurnBend))peakTurnBend=bend;}
        if(pauseWarning&&root.phase()==Lozhnokoren.Phase.WARNING&&root.phaseAge(0)>=15){root.setNoAi(true);pauseWarning=false;}
        if(pauseImpact&&root.landedStrikes>0){root.setNoAi(true);pauseImpact=false;}
        if(phase==11||phase==13){root.setPos(-6,70,11);root.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){if(!ACTIVE||finished)return;var mc=Minecraft.getInstance();try{if(closing)return;
        if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){require(mc.gameDirectory.toPath().toRealPath().toString().replace('\\','/').contains("/.verification/"),"Owned profile required");for(var id:List.of("distanthorizons","create","sable","sodium","iris"))require(ModList.get().isLoaded(id),"Full pack: "+id);started=true;deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(15);
            if(REVIEW){require(Files.exists(mc.gameDirectory.toPath().resolve("lozhnokoren.json")),"Passed owned scene required");var old=JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("lozhnokoren.json"))).getAsJsonObject();require(old.get("passed").getAsBoolean(),"Passed scene required");mc.options.hideGui=false;mc.options.pauseOnLostFocus=false;mc.createWorldOpenFlows().openWorld("lozhnokoren-owned",()->{});return;}
            require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/lozhnokoren-owned")),"Fresh world required");REPORT.addProperty("distant_horizons_loaded",true);REPORT.addProperty("mod_count",ModList.get().getMods().size());REPORT.addProperty("version",ModList.get().getModContainerById(Interstice.ID).orElseThrow().getModInfo().getVersion().toString());mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.options.fov().set(60);mc.options.bobView().set(false);
            var rules=new GameRules();rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc.createWorldOpenFlows().createFreshLevel("lozhnokoren-owned",new LevelSettings("LK01 owner review",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(20261028L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
        }
        if(!started)return;if(mc.getOverlay()!=null||SmokeWorldPrompts.advance(mc)||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null||mc.screen!=null)return;if(REVIEW){if(phase==0&&++ticks>=100){
                require(mc.level.dimension().equals(IslandWorld.TENSION_WORLD),"Cold review remains in V6");var peers=new ArrayList<Lozhnokoren>();for(var actor:mc.level.entitiesForRendering())if(actor instanceof Lozhnokoren r&&r.isAlive())peers.add(r);require(!peers.isEmpty(),"Saved root arrived after cold load");var peer=peers.get(0);for(int i=0;i<15;i++)require(peer.getParts()[i].getId()==peer.getId()+i+1,"Cold multipart ids");
                var proof=new JsonObject();proof.addProperty("passed",true);proof.addProperty("different_jvm_from_test",JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("lozhnokoren.json"))).getAsJsonObject().get("pid").getAsLong()!=ProcessHandle.current().pid());proof.addProperty("pid",ProcessHandle.current().pid());proof.addProperty("distant_horizons_loaded",ModList.get().isLoaded("distanthorizons"));proof.addProperty("actual_dimension",mc.level.dimension().location().toString());proof.addProperty("saved_health",peer.getHealth());proof.addProperty("multipart_ids",15);proof.addProperty("player_has_control",true);proof.addProperty("production_updated",false);Files.writeString(mc.gameDirectory.toPath().resolve("lozhnokoren-review-open.json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof));shot(mc,"review-ready");phase=1;
            }return;}require(System.nanoTime()<deadline,"Deadline phase "+phase);mc.getToasts().clear();if(job!=null&&!job.isDone())return;if(job!=null)job.join();if(phase==18&&mc.level.getEntity(root.getId()) instanceof Lozhnokoren peer){float clientBend=0;for(int i=1;i<11;i++)clientBend+=peer.bodyJointAngle(i,1);debugBounds(mc,peer);if(Math.abs(clientBend)>Math.abs(peakClientBend))peakClientBend=clientBend;if(!turnPhoto&&Math.abs(clientBend)>5){shot(mc,"curved-body");turnPhoto=true;}}if(++ticks<40)return;ticks=0;REPORT.addProperty("live_phase",phase);write(mc);
        if(phase==0){submit(mc,()->prepare(mc));phase=1;}
        else if(phase==1){if(!mc.level.dimension().equals(IslandWorld.TENSION_WORLD)||mc.level.getEntity(root.getId())==null)return;view(mc,18,76,24,150,12);mc.levelRenderer.allChanged();phase=2;}
        else if(phase==2){if(++waits<3)return;waits=0;inspect(mc,"initial");shot(mc,"gallery-scale");view(mc,8.5,73,22,180,7);phase=3;}
        else if(phase==3){shot(mc,"front");view(mc,21,74,8.5,90,10);phase=4;}
        else if(phase==4){shot(mc,"side");job=mc.reloadResourcePacks();phase=5;}
        else if(phase==5){inspect(mc,"after_reload");REPORT.addProperty("resource_reload_checked",true);submit(mc,LozhnokorenNative::target);view(mc,16,75,23,150,10);phase=6;}
        else if(phase==6){if(pauseWarning)return;require(root.phase()==Lozhnokoren.Phase.WARNING&&prey.getHealth()==1000,"Actual warning before damage");shot(mc,"warning-graspers");submit(mc,()->{root.setNoAi(false);pauseImpact=true;});phase=7;}
        else if(phase==7){if(pauseImpact)return;require(root.landedStrikes==1&&prey.getHealth()<985,"Actual frontal heavy strike");REPORT.addProperty("actual_attack_health_lost",1000-prey.getHealth());REPORT.addProperty("actual_attack_hits",root.landedStrikes);require(root.brokenVegetation>0,"Actual claw damaged tree");REPORT.addProperty("actual_tree_blocks_broken",root.brokenVegetation);shot(mc,"impact");submit(mc,()->{prey.moveTo(8.5,70,25);root.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.16);root.setNoAi(false);walkStart=root.getZ();});phase=8;}
        else if(phase==8){if(++waits<6)return;waits=0;require(root.getZ()-walkStart>1,"Actual walk forward: "+root.position());REPORT.addProperty("actual_forward_walk_blocks",root.getZ()-walkStart);shot(mc,"crawl");submit(mc,()->prey.moveTo(root.position().add(12,0,10)));view(mc,root.getX()+10,root.getY()+7,root.getZ()+10,135,22);phase=18;}
        else if(phase==18){float bend=peakTurnBend;REPORT.addProperty("turn_diagnostics",root.position()+" yaw="+root.getYRot()+" phase="+root.phase()+" target="+root.getTarget()+" peak="+bend);if(Math.abs(bend)<5){require(++waits<6,"Actual pursuit did not bend body: "+REPORT.get("turn_diagnostics"));return;}require(Math.abs(peakClientBend)>3,"Server bend not delivered to actual client");REPORT.addProperty("actual_turn_tail_lag_degrees",bend);REPORT.addProperty("actual_client_tail_lag_degrees",peakClientBend);waits=0;submit(mc,()->sea(false));view(mc,2,76,22,140,12);phase=9;}
        else if(phase==9){phase=11;}
        else if(phase==11){if(++waits<3||level.getGameTime()-seaSince<80)return;waits=0;submit(mc,()->seaResult(false));shot(mc,"lower-sea");phase=12;}
        else if(phase==12){submit(mc,()->sea(true));phase=13;}
        else if(phase==13){if(++waits<3)return;waits=0;submit(mc,()->seaResult(true));shot(mc,"upper-sea");phase=14;}
        else if(phase==14){submit(mc,()->prepareNaturalSpawn(mc));phase=16;}
        else if(phase==16){if(++waits<3)return;waits=0;submit(mc,()->verifyNaturalSpawn(mc));phase=17;}
        else if(phase==17){submit(mc,()->reviewReady(mc));phase=15;}
        else if(phase==15){mc.setCameraEntity(mc.player);REPORT.addProperty("actual_dimension",mc.level.dimension().location().toString());finish(mc,true,"");}
    }catch(Throwable error){error.printStackTrace();if(REVIEW){finished=true;throw new IllegalStateException(error);}if(mc.level!=null)shot(mc,"failure");finish(mc,false,error.toString());}}
}
