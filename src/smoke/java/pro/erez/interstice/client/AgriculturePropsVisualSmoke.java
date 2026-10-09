package pro.erez.interstice.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.*;
import pro.erez.interstice.worldgen.GardenMaterials;
import pro.erez.interstice.worldgen.IslandWorld;

/** Explicit prepared Creative prop gallery; actual client bucket fill and native model predicates. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class AgriculturePropsVisualSmoke {
    private static final boolean ENABLED=Boolean.getBoolean("interstice.agriculturePropsSmoke");
    private static final String WORLD="prepared-agriculture-props-gallery";
    private static boolean started,finished,closing,passed;
    private static String reason;
    private static int stage,ticks;
    private static long deadline,poseTick;
    private static BlockPos base;
    private static CompletableFuture<?> work;
    private static volatile NativeChunkSettler.Session settling;
    private static final JsonObject data=new JsonObject();
    private AgriculturePropsVisualSmoke(){}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){var s=settling;if(ENABLED&&s!=null)s.tick(event.getServer());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||finished)return;var mc=Minecraft.getInstance();
        try{
            if(closing){shutdown(mc);return;}
            if(!started&&mc.screen instanceof TitleScreen){
                started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);
                require(!Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD).resolve("level.dat")),"Disposable prop gallery already exists");
                mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings("Prepared native agriculture props",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261006L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
            }
            if(!started||SmokeWorldPrompts.advance(mc))return;
            require(System.nanoTime()<deadline,"Agriculture prop deadline stage "+stage);
            if(mc.player==null||mc.level==null||mc.screen!=null||mc.getConnection()==null)return;
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            if(stage==0){mc.getConnection().sendCommand("interstice explore v4");stage=1;ticks=0;return;}
            if(stage==1&&mc.level.dimension().equals(IslandWorld.LIVING_WORLD)&&++ticks>=40){
                work=server.submit(()->{prepare(server.getPlayerList().getPlayer(id));return true;});settling=new NativeChunkSettler.Session("agriculture_prop_preparation",2000);stage=2;ticks=0;
            }else if(stage==2&&done()&&settling.ready()){
                if(!mc.level.getBlockState(full()).is(RealmAgriculture.RESERVOIR.get())||++ticks<30)return;
                data.add("settle_before_interaction",settling.report());settling=null;
                mc.player.getInventory().selected=0;
                mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(full()).add(0,.45,0),Direction.UP,full(),false));stage=3;ticks=0;
            }else if(stage==3&&++ticks>=20){
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);var entity=(NutrientReservoirEntity)p.serverLevel().getBlockEntity(full());
                    require(entity.amount()==1000&&p.serverLevel().getBlockState(full()).getValue(NutrientReservoirBlock.FILLED),"Ordinary native bucket click did not fill1000mB");
                    var filled=new ItemStack(RealmAgriculture.RESERVOIR_ITEM.get());filled.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(entity.saveWithFullMetadata(p.registryAccess())));filled.set(DataComponents.MAX_STACK_SIZE,1);
                    var inventory=p.getInventory();inventory.items.set(0,new ItemStack(RealmAgriculture.WIRE.get()));inventory.items.set(1,new ItemStack(RealmAgriculture.MESH.get()));inventory.items.set(2,new ItemStack(RealmAgriculture.RESERVOIR_ITEM.get()));inventory.items.set(3,filled);inventory.items.set(4,new ItemStack(RealmAgriculture.LANTERN.get()));inventory.items.set(5,new ItemStack(RealmAgriculture.CULTIVATOR.get()));inventory.items.set(6,new ItemStack(RealmAgriculture.RETORT.get()));inventory.items.set(7,new ItemStack(RealmAgriculture.FENCE.get()));inventory.items.set(8,ItemStack.EMPTY);inventory.setChanged();p.containerMenu.broadcastChanges();
                    data.addProperty("actual_client_bucket_fill_amount",entity.amount());data.addProperty("full_item_uses_actual_reservoir_saved_data",true);pose(p);return true;});stage=4;ticks=0;
            }else if(stage==4&&done()){
                if(!ready(mc,true)){ticks=0;return;}if(++ticks<100)return;
                validateClient(mc);work=server.submit(()->verify(server.getPlayerList().getPlayer(id)));stage=5;ticks=0;
            }else if(stage==5&&done()){
                if(!Boolean.TRUE.equals(work.join())){stage=4;ticks=0;return;}
                mc.player.getInventory().selected=5;shot(mc,"agriculture-props-clear.png");
                work=server.submit(()->{server.getPlayerList().getPlayer(id).removeEffect(MobEffects.NIGHT_VISION);poseTick=server.overworld().getGameTime();return true;});stage=6;ticks=0;
            }else if(stage==6&&done()){
                if(!ready(mc,false)){ticks=0;return;}if(++ticks<100)return;
                work=server.submit(()->server.overworld().getGameTime()-poseTick>=100);stage=7;ticks=0;
            }else if(stage==7&&done()){
                if(!Boolean.TRUE.equals(work.join())){stage=6;ticks=0;return;}
                shot(mc,"agriculture-props-dark.png");data.addProperty("clear_and_ordinary_dark_scenes_waited_100_actual_server_ticks",true);
                finish(mc,true,"Prepared agriculture prop gallery; native reservoir click, filled item predicate, lantern selection shape and mixed wooden fence connections passed");
            }
        }catch(Throwable failure){failure.printStackTrace();finish(mc,false,failure.toString());}
    }
    private static BlockPos full(){return base.offset(6,1,5);}
    private static BlockPos floor(){return base.offset(10,1,4);}
    private static BlockPos hanging(){return base.offset(12,3,4);}
    private static void prepare(ServerPlayer p){
        var level=p.serverLevel();base=IslandWorld.findLanding(level).below().offset(18,0,18);
        for(int x=-2;x<=16;x++)for(int z=-10;z<=10;z++){
            level.setBlock(base.offset(x,0,z),GardenMaterials.PALEHEART_PLANKS.get().defaultBlockState(),3);
            for(int y=1;y<=7;y++)level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        level.setBlock(base.offset(1,1,5),RealmAgriculture.RETORT.get().defaultBlockState(),3);
        level.setBlock(base.offset(4,1,5),RealmAgriculture.RESERVOIR.get().defaultBlockState(),3);
        level.setBlock(full(),RealmAgriculture.RESERVOIR.get().defaultBlockState(),3);
        for(int x=1;x<=12;x++)if(x!=6)level.setBlock(base.offset(x,1,7),(x==1?GardenMaterials.PALEHEART_FENCE.get():RealmAgriculture.FENCE.get()).defaultBlockState(),3);
        level.setBlock(base.offset(6,1,7),RealmAgriculture.GATE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH),3);
        level.setBlock(floor(),RealmAgriculture.LANTERN.get().defaultBlockState(),3);
        for(int y=1;y<=4;y++)level.setBlock(base.offset(14,y,4),GardenMaterials.PALEHEART_PLANKS.get().defaultBlockState(),3);
        for(int x=11;x<=14;x++)level.setBlock(base.offset(x,4,4),GardenMaterials.PALEHEART_PLANKS.get().defaultBlockState(),3);
        level.setBlock(hanging(),RealmAgriculture.LANTERN.get().defaultBlockState().setValue(LanternBlock.HANGING,true),3);
        p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();p.getInventory().items.set(0,new ItemStack(Interstice.RIFTSILVER_HEAVY_BUCKET.get()));p.getInventory().setChanged();p.containerMenu.broadcastChanges();
        p.teleportTo(level,base.getX()+6.5,base.getY()+1,base.getZ()+3.5,Set.of(),0,0);
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,12000,0,false,false));
        data.addProperty("creator_pid",ProcessHandle.current().pid());data.addProperty("scope","Explicitly prepared disposable Creative platform, props and items; ordinary client bucket interaction. This is not natural worldgen or an autonomous Survival route.");
        data.addProperty("base_x",base.getX());data.addProperty("base_y",base.getY());data.addProperty("base_z",base.getZ());
    }
    private static void pose(ServerPlayer p){
        var level=p.serverLevel();for(var center:new BlockPos[]{base,base.offset(7,3,-8)})for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.getChunk((center.getX()>>4)+dx,(center.getZ()>>4)+dz);
        double x=base.getX()+7.5,y=base.getY()+3,z=base.getZ()-8,dx=-.5,dz=13.5,dy=base.getY()+2-(y+p.getEyeHeight());
        p.teleportTo(level,x,y,z,Set.of(),(float)(Math.toDegrees(Math.atan2(dz,dx))-90),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));p.setDeltaMovement(0,0,0);p.getAbilities().flying=true;p.onUpdateAbilities();poseTick=level.getServer().overworld().getGameTime();
    }
    private static boolean ready(Minecraft mc,boolean vision){
        if(mc.player.position().distanceToSqr(base.getX()+7.5,base.getY()+3,base.getZ()-8)>.1||mc.player.hasEffect(MobEffects.NIGHT_VISION)!=vision)return false;
        for(BlockPos center:new BlockPos[]{base,mc.player.blockPosition()})for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(!mc.level.hasChunkAt(new BlockPos((center.getX()>>4)*16+dx*16,base.getY(),(center.getZ()>>4)*16+dz*16)))return false;
        return mc.level.getBlockState(full()).is(RealmAgriculture.RESERVOIR.get())&&mc.level.getBlockState(full()).getValue(NutrientReservoirBlock.FILLED)
                &&AgricultureItems.ReservoirItem.full(mc.player.getInventory().items.get(3));
    }
    private static void validateClient(Minecraft mc){
        require(mc.level.getBlockEntity(full()) instanceof NutrientReservoirEntity reservoir&&reservoir.amount()==1000,"Filled block-entity amount did not synchronize to the actual client");
        var predicate=ItemProperties.getProperty(new ItemStack(RealmAgriculture.RESERVOIR_ITEM.get()),ResourceLocation.fromNamespaceAndPath(Interstice.ID,"filled"));require(predicate!=null,"Native filled icon predicate is missing");
        require(predicate.call(mc.player.getInventory().items.get(3),mc.level,mc.player,0)==1&&predicate.call(mc.player.getInventory().items.get(2),mc.level,mc.player,0)==0,"Native filled/empty icon predicates do not distinguish saved data");
        for(var pos:new BlockPos[]{floor(),hanging()}){var state=mc.level.getBlockState(pos);require(state.is(RealmAgriculture.LANTERN.get()),"Native lantern did not survive its support");var shape=state.getShape(mc.level,pos,CollisionContext.empty());require(shape.min(Direction.Axis.Y)==0&&shape.max(Direction.Axis.Y)==1,"Native lantern selection excludes its visible top");}
        require(!mc.level.getBlockState(floor()).getValue(LanternBlock.HANGING)&&mc.level.getBlockState(hanging()).getValue(LanternBlock.HANGING),"Native floor/hanging lantern states differ from fixture");
        data.addProperty("native_full_empty_reservoir_icon_predicates_1_0",true);data.addProperty("native_floor_hanging_lantern_shape_y_0_16",true);data.addProperty("client_target_and_camera_3x3_loaded",true);
    }
    private static boolean verify(ServerPlayer p){
        var level=p.serverLevel();if(level.getServer().overworld().getGameTime()-poseTick<100)return false;
        require(level.getBlockState(base.offset(1,1,7)).getValue(FenceBlock.EAST)&&level.getBlockState(base.offset(2,1,7)).getValue(FenceBlock.WEST),"Reinforced and own wooden fences do not connect in both directions");
        require(level.getBlockState(base.offset(5,1,7)).getValue(FenceBlock.EAST)&&level.getBlockState(base.offset(7,1,7)).getValue(FenceBlock.WEST),"Actual reinforced gate side connections are missing");
        require(level.getBlockState(floor()).getLightEmission(level,floor())==14&&level.getBlockState(hanging()).getLightEmission(level,hanging())==14,"Native lantern emissions differ from14");
        data.addProperty("native_mixed_wood_reinforced_fence_and_gate_connectivity",true);data.addProperty("native_both_lantern_emissions",14);data.addProperty("native_clear_pose_server_ticks",level.getServer().overworld().getGameTime()-poseTick);return true;
    }
    private static boolean done(){if(work==null||!work.isDone())return false;work.join();return true;}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println("AGRICULTURE_PROPS_SCREENSHOT "+name));}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void finish(Minecraft mc,boolean success,String why){if(closing)return;closing=true;passed=success;reason=why;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.options.broadcastOptions();settling=new NativeChunkSettler.Session("agriculture_props_terminal_shutdown",160);if(!success)write(mc,true);}
    private static void shutdown(Minecraft mc){if(mc.getSingleplayerServer()==null){finished=true;write(mc,false);mc.stop();return;}if(settling.failed()&&!data.has("quiescence_failed")){passed=false;reason=settling.failure();data.addProperty("quiescence_failed",true);write(mc,true);}if(!settling.ready())return;data.add("settle_terminal",settling.report());data.addProperty("clean_generation_before_mc_stop",true);finished=true;settling=null;write(mc,false);mc.stop();}
    private static void write(Minecraft mc,boolean pending){data.addProperty("passed",passed);data.addProperty("reason",reason);data.addProperty("stage",stage);data.addProperty("shutdown_pending",pending);try{Files.writeString(mc.gameDirectory.toPath().resolve("agriculture-props-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));}catch(Exception failure){failure.printStackTrace();}System.out.println("AGRICULTURE_PROPS_VALIDATION "+data);}
}
