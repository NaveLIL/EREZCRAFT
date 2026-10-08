package pro.erez.interstice.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.*;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.worldgen.IslandWorld;

/** Ordinary client clicks and inventory packets, with a cold JVM restart mid-retort batch. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class AgricultureVisualSmoke {
    private static final String MODE=System.getProperty("interstice.agricultureSmoke","");
    private static final String WORLD="native-toxic-farming-check";
    private static boolean started,finished,quitting,passed;
    private static String reason;
    private static int stage,ticks,fertilized;
    private static long deadline;
    private static CompletableFuture<?> work;
    private static volatile NativeChunkSettler.Session settling;
    private static JsonObject data=new JsonObject();
    private static BlockPos base;
    private static RetortBlockEntity machine(ServerLevel level){return (RetortBlockEntity)level.getBlockEntity(base.offset(5,1,4));}
    private static BlockPos crop(){return base.offset(2,1,2);}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){var s=settling;if(!MODE.isEmpty()&&s!=null)s.tick(event.getServer());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(MODE.isEmpty()||finished)return;var mc=Minecraft.getInstance();
        try{
            if(quitting){shutdown(mc);return;}
            if(!started&&mc.screen instanceof TitleScreen){started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(8);mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);
                if(MODE.equals("create")){
                    var settings=new LevelSettings("Disposable toxic farming",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,settings,new WorldOptions(20261006L,true,false),WorldPresets::createNormalWorldDimensions,mc.screen);
                }else{data=JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("agriculture-create-validation.json"))).getAsJsonObject();require(data.get("passed").getAsBoolean()&&data.get("creator_pid").getAsLong()!=ProcessHandle.current().pid(),"Missing successful different-JVM creation");base=new BlockPos(data.get("base_x").getAsInt(),data.get("base_y").getAsInt(),data.get("base_z").getAsInt());mc.createWorldOpenFlows().openWorld(WORLD,()->{});}
                return;
            }
            if(!started||SmokeWorldPrompts.advance(mc))return;require(System.nanoTime()<deadline,"Agriculture stage deadline "+stage);
            if(mc.player==null||mc.level==null||mc.getConnection()==null)return;
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            if(stage==0){if(MODE.equals("create")){mc.getConnection().sendCommand("interstice explore living");stage=1;}else{
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);var tile=machine(p.serverLevel());require(tile!=null,"Saved retort is absent");
                    var tag=tile.saveWithoutMetadata(p.registryAccess());require(tag.getBoolean("Batch")&&tile.data.get(0)>=data.get("saved_progress").getAsInt()&&tile.data.get(0)<200,"Cold restart lost or prematurely completed the saved batch");
                    var reserved=NonNullList.withSize(6,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag.getCompound("Reserved"),reserved,p.registryAccess());
                    require(reserved.getFirst().getCount()==2&&reserved.getFirst().getHoverName().getString().equals("native saved grain"),"Cold reserved item components were lost");
                    require(p.serverLevel().getBlockState(crop()).is(RealmAgriculture.GRAIN_CROP.get()),"Saved crop disappeared");
                    data.addProperty("reload_pid",ProcessHandle.current().pid());data.addProperty("cold_reserved_components_and_progress_preserved",true);return true;
                });stage=30;}ticks=0;return;}
            if(stage==1&&mc.level.dimension().equals(IslandWorld.LIVING_WORLD)&&++ticks>=40){work=server.submit(()->{prepare(server.getPlayerList().getPlayer(id));return true;});settling=new NativeChunkSettler.Session("before_native_agriculture",2000);stage=2;ticks=0;}
            else if(stage==2&&done()&&settling.ready()&&++ticks>=20){data.add("settle_before_interactions",settling.report());settling=null;use(mc,base.offset(2,0,2),0);stage=3;ticks=0;}
            else if(stage==3&&++ticks>=15){work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);require(p.serverLevel().getBlockState(crop().below()).is(RealmAgriculture.FARMLAND.get())&&p.getInventory().items.get(0).getDamageValue()==1,"Actual client hoe did not till own soil");return true;});stage=4;ticks=0;}
            else if(stage==4&&done()){use(mc,crop().below(),1);stage=5;ticks=0;}
            else if(stage==5&&++ticks>=15){work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);require(p.serverLevel().getBlockState(crop()).is(RealmAgriculture.GRAIN_CROP.get())&&p.getInventory().items.get(1).getCount()==3,"Actual seed placement failed");data.addProperty("ordinary_hoe_and_native_seed_placement",true);data.addProperty("actual_crop_block_light",p.serverLevel().getMaxLocalRawBrightness(crop()));return true;});stage=6;ticks=0;}
            else if(stage==6&&done()){use(mc,crop(),2);stage=7;ticks=0;}
            else if(stage==7&&++ticks>=12){
                int expected=++fertilized;work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);require(RealmAgriculture.GRAIN_CROP.get().getAge(p.serverLevel().getBlockState(crop()))==expected,"Native fertilizer did not advance exactly one stage");return true;});stage=expected<4?6:8;ticks=0;
            }else if(stage==8&&done()&&++ticks>=15){shot(mc,"agriculture-ready-crops.png");use(mc,crop(),6);stage=9;ticks=0;}
            else if(stage==9&&++ticks>=15){work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);require(RealmAgriculture.GRAIN_CROP.get().getAge(p.serverLevel().getBlockState(crop()))==0&&p.getInventory().items.get(6).getDamageValue()==1,"Actual mature harvest did not replant using one seed");
                int drops=p.getInventory().countItem(RealmAgriculture.GRAIN.get())-3+p.serverLevel().getEntitiesOfClass(ItemEntity.class,new AABB(crop()).inflate(2),e->e.getItem().is(RealmAgriculture.GRAIN.get())).stream().mapToInt(e->e.getItem().getCount()).sum();require(drops>=1&&drops<=3,"Client harvest lost its actual net crop loot");data.addProperty("ordinary_fertilizer_four_stages_and_cultivator",true);data.addProperty("net_harvest_loot",drops);
                p.teleportTo(p.serverLevel(),base.getX()+3.5,base.getY()+1,base.getZ()+3.5,Set.of(),0,0);return true;});stage=10;ticks=0;}
            else if(stage==10&&done()&&++ticks>=20){use(mc,base.offset(5,0,4),4);stage=11;ticks=0;}
            else if(stage==11&&++ticks>=20){use(mc,base.offset(5,1,4),4);stage=12;ticks=0;}
            else if(stage==12&&mc.screen instanceof RetortScreen&&++ticks>=20){
                require(mc.level.getRecipeManager().getAllRecipesFor(RealmAgriculture.RETORT_RECIPE_TYPE.get()).size()==7,"Client custom recipes were not synchronized");
                deposit(mc,RealmAgriculture.GRAIN.get(),2);shift(mc,45);data.addProperty("ordinary_block_item_retort_placement_and_menu_open",true);data.addProperty("client_custom_recipe_count",7);stage=13;ticks=0;
            }else if(stage==13&&++ticks>=20){shot(mc,"agriculture-retort-running.png");mc.player.closeContainer();work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);var tile=machine(p.serverLevel());require(tile!=null&&tile.data.get(0)>0&&tile.data.get(0)<200,"Native menu packets did not start a pending counted recipe");data.addProperty("native_inventory_packets_started_retort",true);return true;});stage=14;ticks=0;}
            else if(stage==14&&done()){finish(mc,true,"Native planting, fertilizer, harvest and menu processing passed; saved mid-batch for cold restart");}
            else if(stage==30&&done()){
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);return machine(p.serverLevel()).getItem(7).getCount()==2;});stage=31;ticks=0;
            }else if(stage==31&&done()){
                if(!Boolean.TRUE.equals(work.join())){require(++ticks<400,"Saved retort did not finish through ordinary ticks");work=server.submit(()->machine(server.getPlayerList().getPlayer(id).serverLevel()).getItem(7).getCount()==2);return;}
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);var tile=machine(p.serverLevel());require(tile.getItem(7).is(RealmAgriculture.FLOUR.get())&&tile.getItem(8).is(RealmAgriculture.FIBER.get())&&tile.getItem(8).getCount()==1&&tile.data.get(2)==3000,"Saved batch has duplicated/missing outputs or incorrect heat");
                    // Explicit extra flour makes a full four-bread batch; not an autonomous Survival route.
                    p.getInventory().items.set(8,new ItemStack(RealmAgriculture.FLOUR.get(),2));p.getInventory().setChanged();p.containerMenu.broadcastChanges();data.addProperty("prepared_additional_flour_for_full_food_batch",2);data.addProperty("cold_native_batch_completed_once",true);return true;});stage=32;ticks=0;
            }else if(stage==32&&done()&&++ticks>=20){use(mc,base.offset(5,1,4),4);stage=33;ticks=0;}
            else if(stage==33&&mc.screen instanceof RetortScreen&&++ticks>=15){shift(mc,7);shift(mc,8);deposit(mc,RealmAgriculture.FLOUR.get(),4);deposit(mc,RealmAgriculture.SORBENT.get(),1);stage=34;ticks=0;}
            else if(stage==34&&++ticks>=20){shot(mc,"agriculture-food-purification.png");work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);return machine(p.serverLevel()).getItem(7).is(RealmAgriculture.BREAD.get());});stage=35;ticks=0;}
            else if(stage==35&&done()){
                if(!Boolean.TRUE.equals(work.join())){require(++ticks<1600,"Native food recipe never finished");work=server.submit(()->machine(server.getPlayerList().getPlayer(id).serverLevel()).getItem(7).is(RealmAgriculture.BREAD.get()));return;}
                shift(mc,7);mc.player.closeContainer();work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);require(p.getInventory().countItem(RealmAgriculture.BREAD.get())==4,"Native output pickup failed");require(machine(p.serverLevel()).data.get(2)==2200,"Two actual operations spent wrong heat");p.getFoodData().setFoodLevel(0);data.addProperty("actual_retort_purified_bread_count",4);return true;});stage=36;ticks=0;
            }else if(stage==36&&done()&&++ticks>=20){eat(mc,RealmAgriculture.ROOT.get());stage=37;ticks=0;}
            else if(stage==37&&++ticks>=48){mc.options.keyUse.setDown(false);work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);require(p.hasEffect(MobEffects.POISON)&&p.hasEffect(MobEffects.WEAKNESS)&&p.getFoodData().getFoodLevel()==3,"Actual client raw-food eating lacks nutrition or toxicity");p.removeEffect(MobEffects.POISON);p.removeEffect(MobEffects.WEAKNESS);data.addProperty("prepared_effect_reset_between_raw_and_safe_meals",true);data.addProperty("ordinary_raw_food_eating_is_toxic",true);return true;});stage=38;ticks=0;}
            else if(stage==38&&done()&&++ticks>=20){eat(mc,RealmAgriculture.BREAD.get());stage=39;ticks=0;}
            else if(stage==39&&++ticks>=48){mc.options.keyUse.setDown(false);work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);require(!p.hasEffect(MobEffects.POISON)&&p.getFoodData().getFoodLevel()==9&&p.getInventory().countItem(RealmAgriculture.BREAD.get())==3,"Purified client meal is toxic or not actually consumed");data.addProperty("ordinary_purified_food_eating_safe",true);return true;});stage=40;ticks=0;}
            else if(stage==40&&done()){shot(mc,"agriculture-purified-meal.png");finish(mc,true,"Cold saved batch finished once, food purification used native menu packets and real raw/safe eating passed");}
        }catch(Throwable failure){failure.printStackTrace();finish(mc,false,failure.toString());}
    }
    private static void prepare(ServerPlayer player){
        var level=player.serverLevel();base=IslandWorld.findLanding(level).below().offset(12,0,0);
        for(int x=0;x<=10;x++)for(int z=0;z<=8;z++){level.setBlock(base.offset(x,0,z),MineralEcology.ROOT_LOAM.get().defaultBlockState(),3);for(int y=1;y<=3;y++)level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        level.setBlock(base.offset(5,0,2),Interstice.HEAVY_BLOCK.get().defaultBlockState(),3);
        for(int i=0;i<5;i++){var soil=base.offset(1+i,0,7);level.setBlock(soil,RealmAgriculture.FARMLAND.get().defaultBlockState().setValue(ToxicFarmlandBlock.MOISTURE,7),3);level.setBlock(soil.above(),RealmAgriculture.GRAIN_CROP.get().getStateForAge(i),3);}
        for(int i=0;i<4;i++){var soil=base.offset(6+i,0,7);level.setBlock(soil,RealmAgriculture.FARMLAND.get().defaultBlockState().setValue(ToxicFarmlandBlock.MOISTURE,7),3);level.setBlock(soil.above(),RealmAgriculture.ROOT_CROP.get().getStateForAge(i),3);}
        player.setGameMode(GameType.SURVIVAL);player.getAbilities().flying=false;player.onUpdateAbilities();player.getInventory().clearContent();
        player.getInventory().items.set(0,new ItemStack(Items.IRON_HOE));var grain=new ItemStack(RealmAgriculture.GRAIN.get(),4);grain.set(DataComponents.CUSTOM_NAME,Component.literal("native saved grain"));player.getInventory().items.set(1,grain);
        player.getInventory().items.set(2,new ItemStack(RealmAgriculture.FERTILIZER.get(),8));player.getInventory().items.set(3,new ItemStack(RealmAgriculture.ROOT.get(),4));player.getInventory().items.set(4,new ItemStack(RealmAgriculture.RETORT.get()));player.getInventory().items.set(5,new ItemStack(MineralEcology.UMBRAL_COAL.get(),2));player.getInventory().items.set(6,new ItemStack(RealmAgriculture.CULTIVATOR.get()));player.getInventory().items.set(7,new ItemStack(RealmAgriculture.SORBENT.get()));player.getInventory().setChanged();player.containerMenu.broadcastChanges();
        player.teleportTo(level,base.getX()+2.5,base.getY()+1,base.getZ()+.5,Set.of(),0,0);player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,12000,0,false,false));
        data.addProperty("creator_pid",ProcessHandle.current().pid());data.addProperty("base_x",base.getX());data.addProperty("base_y",base.getY());data.addProperty("base_z",base.getZ());
        data.addProperty("scope","Prepared disposable Survival platform, inventory and crop-stage display; actual ordinary client actions; not an autonomous Survival expedition");
    }
    private static void use(Minecraft mc,BlockPos pos,int slot){mc.player.getInventory().selected=slot;look(mc,Vec3.atCenterOf(pos));mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(pos.getX()+.5,pos.getY()+.96,pos.getZ()+.5),Direction.UP,pos,false));}
    private static void look(Minecraft mc,Vec3 at){var d=at.subtract(mc.player.getEyePosition());mc.player.setYRot((float)(Math.toDegrees(Math.atan2(d.z,d.x))-90));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));}
    private static void click(Minecraft mc,int slot,int button,ClickType type){mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,button,type,mc.player);}
    private static void shift(Minecraft mc,int slot){click(mc,slot,0,ClickType.QUICK_MOVE);}
    private static void deposit(Minecraft mc,Item item,int needed){
        var menu=mc.player.containerMenu;require(menu instanceof RetortMenu,"No actual retort menu");
        for(int source=13;source<49&&needed>0;source++){var stack=menu.slots.get(source).getItem();if(!stack.is(item))continue;
            int take=Math.min(needed,stack.getCount());int target=-1;for(int i=0;i<6;i++)if(menu.slots.get(i).getItem().isEmpty()||ItemStack.isSameItemSameComponents(menu.slots.get(i).getItem(),stack)){target=i;break;}require(target>=0,"No native input slot");
            click(mc,source,0,ClickType.PICKUP);for(int i=0;i<take;i++)click(mc,target,1,ClickType.PICKUP);click(mc,source,0,ClickType.PICKUP);needed-=take;
        }require(needed==0&&menu.getCarried().isEmpty(),"Ordinary menu transfer lacks ingredients or left cursor stack");
    }
    private static void eat(Minecraft mc,Item item){int slot=-1;for(int i=0;i<9;i++)if(mc.player.getInventory().items.get(i).is(item)){slot=i;break;}require(slot>=0,"Native meal is not in the hotbar");mc.player.getInventory().selected=slot;mc.player.setXRot(-80);mc.options.keyUse.setDown(true);}
    private static boolean done(){if(work==null||!work.isDone())return false;work.join();return true;}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println("AGRICULTURE_SCREENSHOT "+name));}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void finish(Minecraft mc,boolean success,String why){if(quitting)return;passed=success;reason=why;quitting=true;mc.options.keyUse.setDown(false);mc.options.keyUp.setDown(false);mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.options.broadcastOptions();if(mc.player!=null)mc.player.closeContainer();settling=new NativeChunkSettler.Session("agriculture_terminal_shutdown",160);work=null;}
    private static void shutdown(Minecraft mc)throws Exception{
        if(mc.getSingleplayerServer()==null){write(mc,false,"Server unavailable during shutdown");mc.stop();finished=true;return;}
        if(settling.failed()){passed=false;reason=settling.failure();}
        if(!settling.ready())return;
        if(work==null){var server=mc.getSingleplayerServer();var id=mc.player.getUUID();work=server.submit(()->{if(MODE.equals("create")&&passed){var tile=machine(server.getPlayerList().getPlayer(id).serverLevel());require(tile.data.get(0)>0&&tile.data.get(0)<200,"Creation did not save a pending mid-batch transaction");data.addProperty("saved_progress",tile.data.get(0));data.addProperty("saved_heat",tile.data.get(2));}return true;});return;}
        if(!work.isDone())return;
        try{work.join();}catch(Throwable failure){passed=false;reason=failure.toString();}
        data.add("settle_terminal",settling.report());data.addProperty("clean_generation_before_mc_stop",true);write(mc,passed,reason);mc.stop();finished=true;
    }
    private static void write(Minecraft mc,boolean success,String why)throws Exception{data.addProperty("passed",success);data.addProperty("mode",MODE);data.addProperty("stage",stage);data.addProperty("reason",why);Files.writeString(mc.gameDirectory.toPath().resolve("agriculture-"+MODE+"-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));System.out.println("AGRICULTURE_VALIDATION "+data);}
}
