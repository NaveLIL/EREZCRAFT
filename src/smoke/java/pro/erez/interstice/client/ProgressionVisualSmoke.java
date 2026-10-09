package pro.erez.interstice.client;

import com.google.gson.*;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.*;
import pro.erez.interstice.equipment.ExpeditionEquipment;
import pro.erez.interstice.gear.*;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.rift.*;
import pro.erez.interstice.worldgen.IslandWorld;

/** Prepared supplies/workshop; real Survival menu/item packets and a paid pending batch across two JVMs. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class ProgressionVisualSmoke {
    private static final String MODE=System.getProperty("interstice.v6ProgressionSmoke","");
    private static final String WORLD="v6-native-progression-check";
    private static final BlockPos TABLE=new BlockPos(8,72,8),ANVIL=new BlockPos(10,72,8),RETORT=new BlockPos(12,72,8),
            CAULDRON=new BlockPos(8,72,6),BEACON=new BlockPos(10,72,6);
    private static final ResourceLocation COUPLERS=ResourceLocation.fromNamespaceAndPath(Interstice.ID,"retort_pressure_coupler");
    private static JsonObject data=new JsonObject();
    private static boolean started,finished,closing,passed;
    private static String reason;
    private static int stage,ticks;
    private static long deadline;
    private static CompletableFuture<Boolean> work;
    private static volatile NativeChunkSettler.Session settling;
    private ProgressionVisualSmoke(){}

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){if(!MODE.isEmpty()&&settling!=null)settling.tick(event.getServer());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(MODE.isEmpty()||finished)return;var mc=Minecraft.getInstance();
        try{
            if(closing){shutdown(mc);return;}
            if(!started&&mc.screen instanceof TitleScreen){
                require(MODE.equals("create")||MODE.equals("reload"),"Progression mode must be create or reload");
                require(mc.gameDirectory.getCanonicalPath().toLowerCase().contains(".verification"),"Use an isolated .verification progression profile");
                started=true;deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(5);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);
                if(MODE.equals("create")){
                    require(!Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD).resolve("level.dat")),"Disposable progression world already exists");
                    var rules=new GameRules();rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings("Prepared native progression",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),
                            new WorldOptions(20261009L,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
                }else{
                    data=JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("v6-progression-create-validation.json"))).getAsJsonObject();
                    require(data.get("passed").getAsBoolean()&&data.get("creator_pid").getAsLong()!=ProcessHandle.current().pid(),"Reload needs successful creation from a different JVM");
                    mc.createWorldOpenFlows().openWorld(WORLD,()->{});
                }
                return;
            }
            if(!started||SmokeWorldPrompts.advance(mc))return;
            require(System.nanoTime()<deadline,"Progression deadline at stage "+stage);
            if(mc.player==null||mc.level==null||mc.getConnection()==null)return;
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();ticks++;
            if(stage==0){work=server.submit(()->{if(MODE.equals("create"))prepare(server.getPlayerList().getPlayer(id));return true;});
                settling=new NativeChunkSettler.Session("progression_initial_workshop",1200);stage=1;ticks=0;return;}
            if(stage==1&&done()&&settling.ready()&&ticks>=20){data.add("settle_initial",settling.report());settling=null;
                if(MODE.equals("create")){select(mc,6);click(mc,TABLE);next(2);}else{work=server.submit(()->verifyCold(server.getPlayerList().getPlayer(id)));stage=30;ticks=0;}}
            else if(stage==2&&mc.player.containerMenu instanceof CraftingMenu&&ticks>=15){
                take(mc,10);putOne(mc,1);putOne(mc,3);putOne(mc,8);
                take(mc,11);take(mc,2);take(mc,12);putOne(mc,4);putOne(mc,6);take(mc,13);take(mc,5);next(3);
            }else if(stage==3&&ticks>=20){
                require(mc.player.containerMenu instanceof CraftingMenu&&mc.player.containerMenu.getSlot(0).getItem().is(RiftInitiation.UNSTABLE_INITIATOR.get()),"Native crafting grid did not offer the paid external initiator");
                shot(mc,"v6-progression-native-initiator-craft.png");shift(mc,0);mc.player.closeContainer();next(4);
            }else if(stage==4&&ticks>=20){
                if(work==null)work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);survival(p);
                    require(p.getInventory().countItem(RiftInitiation.UNSTABLE_INITIATOR.get())==1&&p.getInventory().countItem(Items.AMETHYST_SHARD)==0
                            &&p.getInventory().countItem(Items.GOLD_INGOT)==0&&p.getInventory().countItem(Items.REDSTONE)==0&&p.getInventory().countItem(Items.GLASS)==0,"Craft result failed exact external payment");data.addProperty("native_external_craft_paid",true);return true;});
                else if(done()){select(mc,hotbar(mc,RiftInitiation.UNSTABLE_INITIATOR.get()));click(mc,CAULDRON);next(5);}
            }else if(stage==5&&mc.level.dimension().equals(IslandWorld.CURRENT_WORLD)&&ticks>=20){
                work=server.submit(()->entry(server.getPlayerList().getPlayer(id)));stage=6;ticks=0;
            }else if(stage==6&&done()&&ticks>=90){
                select(mc,6);click(mc,echo());next(7);
            }else if(stage==7){
                if(mc.level.dimension().equals(Level.OVERWORLD)){select(mc,6);click(mc,ANVIL);next(8);}
                else if(ticks%25==0)click(mc,echo());
            }else if(stage==8&&mc.player.containerMenu instanceof AnvilMenu&&ticks>=20){
                take(mc,31);take(mc,0);take(mc,7);for(int n=0;n<4;n++)putOne(mc,1);take(mc,7);
                mc.getConnection().send(new ServerboundRenameItemPacket("Native paid ballast"));next(9);
            }else if(stage==9&&ticks>=20){
                require(mc.player.containerMenu instanceof AnvilMenu&&mc.player.containerMenu.getSlot(2).getItem().is(RealmGear.BALLAST_BELT.get())
                        &&mc.player.containerMenu.getSlot(2).getItem().getDamageValue()==0,"Native anvil did not service the exhausted core");
                shot(mc,"v6-progression-native-ballast-service.png");shift(mc,2);mc.player.closeContainer();next(10);
            }else if(stage==10&&ticks>=20){
                if(work==null)work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);var belt=item(p,RealmGear.BALLAST_BELT.get());survival(p);
                    require(belt.getDamageValue()==0&&belt.getHoverName().getString().equals("Native paid ballast")&&note(belt).getInt("progression_core")==777
                            &&p.getInventory().countItem(RealmAgriculture.PURE_LINING.get())==4&&p.experienceLevel==16,"Anvil failed component preservation/four linings/four XP payment");data.addProperty("native_anvil_service_paid",true);return true;});
                else if(done()){select(mc,3);click(mc,BEACON.below());next(11);}
            }else if(stage==11&&ticks>=20){
                if(work==null)work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);var b=beacon(p);
                    require(b.markerName().equals("Native shared depot")&&p.getUUID().equals(b.owner())&&p.getInventory().countItem(RealmGear.BEACON_ITEM.get())==0,"Native named beacon placement failed");return true;});
                else if(done()){select(mc,2);sneak(mc,true);click(mc,BEACON);next(12);}
            }else if(stage==12&&ticks>=20){sneak(mc,false);select(mc,4);click(mc,BEACON);next(13);}
            else if(stage==13&&ticks>=20){
                if(work==null)work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);var marker=RouteMarkers.marker(item(p,Interstice.TIDE_INDICATOR.get()));var b=beacon(p);
                    require(marker!=null&&marker.id().equals(b.markerId())&&marker.name().equals("Native shared depot")&&b.fuelTicks()>0
                            &&p.getInventory().countItem(MineralEcology.PHOSPHORITE_CRYSTAL.get())==1,"Native public marker binding/fuel payment failed");data.addProperty("native_named_marker_bound",true);return true;});
                else if(done()){select(mc,2);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);shot(mc,"v6-progression-native-marker.png");select(mc,6);click(mc,RETORT);next(14);}
            }else if(stage==14&&mc.player.containerMenu instanceof RetortMenu&&ticks>=20){
                mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId,RetortMenu.fillButton(COUPLERS));shift(mc,45);next(15);
            }else if(stage==15&&ticks>=40){
                if(work==null)work=server.submit(()->snapshotWarm(server.getPlayerList().getPlayer(id)));
                else if(done()){shot(mc,"v6-progression-native-pending-couplers.png");finish(mc,true,"Prepared supplies; native paid craft/entry/recipe rewards/service/public marker and unfinished catalytic batch. Resource gathering and two-client use are not claimed.");}
            }else if(stage==30&&done()&&ticks>=20){data.addProperty("reload_pid",ProcessHandle.current().pid());select(mc,6);click(mc,RETORT);next(31);}
            else if(stage==31&&mc.player.containerMenu instanceof RetortMenu&&ticks>=20){shot(mc,"v6-progression-cold-pending-couplers.png");next(32);}
            else if(stage==32&&ticks%20==0){
                if(work==null)work=server.submit(()->complete(server.getPlayerList().getPlayer(id)));
                else if(done()){if(Boolean.TRUE.equals(work.join())){shift(mc,7);next(33);}else work=null;}
            }else if(stage==33&&ticks>=20){
                if(work==null)work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);survival(p);var machine=retort(p);
                    require(p.getInventory().countItem(Interstice.PRESSURE_COUPLER.get())==2&&machine.getItem(7).isEmpty()&&!machine.hasBatch()
                            &&machine.getItem(6).getCount()==1,"Native output extraction duplicated the catalyst/output/fuel");data.addProperty("cold_native_paid_output_extracted",true);return true;});
                else if(done()){shot(mc,"v6-progression-cold-coupler-output.png");mc.player.closeContainer();select(mc,2);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
                    finish(mc,true,"Different JVM retained component items/public marker/paid pending snapshot; native retort ticks completed exactly two membranes and actual menu extraction preserved quantities.");}
            }
        }catch(Throwable failure){failure.printStackTrace();finish(mc,false,failure.toString());}
    }

    private static void prepare(ServerPlayer p){
        p.setGameMode(GameType.CREATIVE);var level=p.server.overworld();level.getChunk(0,0);
        for(int x=4;x<=16;x++)for(int z=4;z<=12;z++){
            level.setBlock(new BlockPos(x,71,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=72;y<=77;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
            level.setBlock(new BlockPos(x,78,z),Blocks.STONE.defaultBlockState(),3);
        }
        level.setBlock(TABLE,Blocks.CRAFTING_TABLE.defaultBlockState(),3);level.setBlock(ANVIL,Blocks.ANVIL.defaultBlockState(),3);level.setBlock(RETORT,RealmAgriculture.RETORT.get().defaultBlockState(),3);
        level.setBlock(CAULDRON.below(),Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING,net.minecraft.core.Direction.UP),3);
        level.setBlock(CAULDRON,Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL,3),3);
        level.setBlock(new BlockPos(5,76,5),Blocks.SHROOMLIGHT.defaultBlockState(),3);
        p.getInventory().clearContent();p.setHealth(20);p.getFoodData().setFoodLevel(20);p.giveExperienceLevels(20-p.experienceLevel);
        var belt=new ItemStack(RealmGear.BALLAST_BELT.get());belt.setDamageValue(RealmGear.BELT_MAX_DAMAGE);belt.set(DataComponents.CUSTOM_NAME,Component.literal("Native paid ballast"));
        var note=new CompoundTag();note.putInt("progression_core",777);belt.set(DataComponents.CUSTOM_DATA,CustomData.of(note));p.getInventory().setItem(1,belt);
        p.getInventory().setItem(2,new ItemStack(Interstice.TIDE_INDICATOR.get()));var marker=new ItemStack(RealmGear.BEACON_ITEM.get());marker.set(DataComponents.CUSTOM_NAME,Component.literal("Native shared depot"));p.getInventory().setItem(3,marker);
        p.getInventory().setItem(4,new ItemStack(MineralEcology.PHOSPHORITE_CRYSTAL.get(),2));p.getInventory().setItem(5,new ItemStack(MineralEcology.UMBRAL_COAL.get(),2));
        p.getInventory().setItem(9,new ItemStack(Items.AMETHYST_SHARD,3));p.getInventory().setItem(10,new ItemStack(Items.GOLD_INGOT));p.getInventory().setItem(11,new ItemStack(Items.REDSTONE,2));p.getInventory().setItem(12,new ItemStack(Items.GLASS));
        p.getInventory().setItem(13,new ItemStack(RealmAgriculture.PURE_LINING.get(),8));var first=new ItemStack(Interstice.PRESSURE_COUPLER.get());first.set(DataComponents.CUSTOM_NAME,Component.literal("Prepared first-research catalyst"));p.getInventory().setItem(14,first);
        p.getInventory().setItem(15,new ItemStack(Interstice.RIFTSILVER_INGOT.get(),4));p.getInventory().setItem(16,new ItemStack(RealmAgriculture.PASTE.get(),4));p.getInventory().setItem(17,new ItemStack(RealmAgriculture.SORBENT.get(),4));p.getInventory().setItem(18,new ItemStack(ExpeditionEquipment.CHEMOTROPHIC_FABRIC.get(),4));
        p.teleportTo(level,9.5,72,7.5,Set.of(),0,0);p.hasChangedDimension();p.setGameMode(GameType.SURVIVAL);p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();
        data.addProperty("creator_pid",ProcessHandle.current().pid());data.addProperty("scope","Explicit Creative setup of flat workshop/materials/20XP, then native Survival craft/item/menu actions. No autonomous resource acquisition, human discovery time, or two-client claim.");
    }
    private static boolean entry(ServerPlayer p){
        survival(p);require(p.level().dimension().equals(IslandWorld.CURRENT_WORLD)&&p.getInventory().countItem(RiftInitiation.UNSTABLE_INITIATOR.get())==0,"Native initiator entry did not reach CURRENT_WORLD/consume exactly its one charge");
        for(String path:new String[]{"root","rift_discovery","arrival_guidance"}){var advancement=p.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(Interstice.ID,path));require(advancement!=null&&p.getAdvancements().getOrStartProgress(advancement).isDone(),"Missing native arrival advancement "+path);}
        require(p.getRecipeBook().contains(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"rift_frame"))&&p.getRecipeBook().contains(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"rift_lens")),"Normal changed_dimension rewards did not unlock both portal recipes");
        var link=RiftLinks.get(p.server).byId(p.getPersistentData().getUUID(RiftTravel.ACTIVE));require(link!=null,"Native entry link missing");
        data.addProperty("echo_x",link.echo().pos().getX());data.addProperty("echo_y",link.echo().pos().getY());data.addProperty("echo_z",link.echo().pos().getZ());data.addProperty("entry_dimension",p.level().dimension().location().toString());data.addProperty("native_entry_and_recipe_rewards",true);return true;
    }
    private static boolean snapshotWarm(ServerPlayer p){
        survival(p);var r=retort(p);require(r.hasBatch()&&r.data.get(1)==3200&&r.data.get(0)>0&&r.data.get(0)<600&&r.data.get(0)+r.data.get(2)==3200&&r.getItem(6).getCount()==1,"Native fill/fuel did not start exactly one paid unfinished catalytic batch");
        for(int i=0;i<6;i++)require(r.getItem(i).isEmpty(),"Reserved input remains duplicated in visible slot "+i);
        var saved=r.saveWithoutMetadata(p.registryAccess());data.addProperty("reserved",saved.getCompound("Reserved").toString());data.addProperty("pending",saved.getCompound("Pending").toString());data.addProperty("warm_progress_lower_bound",r.data.get(0));
        data.addProperty("belt",item(p,RealmGear.BALLAST_BELT.get()).save(p.registryAccess()).toString());data.addProperty("indicator",item(p,Interstice.TIDE_INDICATOR.get()).save(p.registryAccess()).toString());
        data.addProperty("beacon_id",beacon(p).markerId().toString());data.addProperty("warm_fuel_upper_bound",beacon(p).fuelTicks());data.addProperty("native_paid_pending_batch",true);return true;
    }
    private static boolean verifyCold(ServerPlayer p){try{
        survival(p);var r=retort(p);var saved=r.saveWithoutMetadata(p.registryAccess());
        require(r.hasBatch()&&r.data.get(1)==3200&&r.data.get(0)>=data.get("warm_progress_lower_bound").getAsInt()&&r.data.get(0)+r.data.get(2)==3200&&r.getItem(6).getCount()==1,"Cold pending state changed total paid work/heat/fuel");
        require(saved.getCompound("Reserved").equals(TagParser.parseTag(data.get("reserved").getAsString()))&&saved.getCompound("Pending").equals(TagParser.parseTag(data.get("pending").getAsString())),"Cold load rebuilt or changed paid reserved/pending component snapshots");
        require(ItemStack.matches(item(p,RealmGear.BALLAST_BELT.get()),ItemStack.parse(p.registryAccess(),TagParser.parseTag(data.get("belt").getAsString())).orElseThrow())
                &&ItemStack.matches(item(p,Interstice.TIDE_INDICATOR.get()),ItemStack.parse(p.registryAccess(),TagParser.parseTag(data.get("indicator").getAsString())).orElseThrow()),"Different-JVM load changed named core/public marker components");
        var b=beacon(p);require(b.markerId().toString().equals(data.get("beacon_id").getAsString())&&b.fuelTicks()>0&&b.fuelTicks()<=data.get("warm_fuel_upper_bound").getAsInt(),"Cold named beacon identity changed or finite signal refilled");
        data.addProperty("cold_paid_snapshots_preserved",true);data.addProperty("cold_start_progress",r.data.get(0));return true;
    }catch(Exception failure){throw new IllegalStateException("Native cold progression snapshot failed",failure);}}
    private static boolean complete(ServerPlayer p){var r=retort(p);if(r.hasBatch())return false;
        require(r.getItem(7).is(Interstice.PRESSURE_COUPLER.get())&&r.getItem(7).getCount()==2&&r.getItem(8).isEmpty()&&r.getItem(9).isEmpty()&&r.data.get(2)==0&&r.getItem(6).getCount()==1,"Native cold completion lost or duplicated paid output/heat/fuel");data.addProperty("cold_native_batch_completed",true);return true;}
    private static RetortBlockEntity retort(ServerPlayer p){require(p.serverLevel().getBlockEntity(RETORT) instanceof RetortBlockEntity,"Missing saved retort");return(RetortBlockEntity)p.serverLevel().getBlockEntity(RETORT);}
    private static RouteBeaconEntity beacon(ServerPlayer p){require(p.serverLevel().getBlockEntity(BEACON) instanceof RouteBeaconEntity,"Missing saved beacon");return(RouteBeaconEntity)p.serverLevel().getBlockEntity(BEACON);}
    private static ItemStack item(ServerPlayer p,Item item){for(int i=0;i<36;i++){var at=p.getInventory().getItem(i);if(at.is(item))return at;}throw new IllegalStateException("Missing owned item "+item);}
    private static CompoundTag note(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    private static void survival(ServerPlayer p){require(p.gameMode.getGameModeForPlayer()==GameType.SURVIVAL&&!p.getAbilities().flying&&!p.getAbilities().mayfly,"Procedure must use native Survival, not setup Creative/flight");}
    private static BlockPos echo(){return new BlockPos(data.get("echo_x").getAsInt(),data.get("echo_y").getAsInt(),data.get("echo_z").getAsInt());}
    private static int hotbar(Minecraft mc,Item item){for(int i=0;i<9;i++)if(mc.player.getInventory().getItem(i).is(item))return i;throw new IllegalStateException("Native output did not reach a free hotbar slot");}
    private static void select(Minecraft mc,int slot){mc.player.getInventory().selected=slot;mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));}
    private static void click(Minecraft mc,BlockPos pos){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false));}
    private static void take(Minecraft mc,int slot){mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,0,ClickType.PICKUP,mc.player);}
    private static void putOne(Minecraft mc,int slot){mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,1,ClickType.PICKUP,mc.player);}
    private static void shift(Minecraft mc,int slot){mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,0,ClickType.QUICK_MOVE,mc.player);}
    private static void sneak(Minecraft mc,boolean active){mc.player.input.shiftKeyDown=active;mc.player.setShiftKeyDown(active);mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player,active?ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));}
    private static boolean done(){if(work==null||!work.isDone())return false;work.join();return true;}
    private static void next(int value){stage=value;ticks=0;work=null;}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void shot(Minecraft mc,String file){Screenshot.grab(mc.gameDirectory,file,mc.getMainRenderTarget(),message->System.out.println("V6_PROGRESSION_SCREENSHOT "+file));}
    private static void finish(Minecraft mc,boolean success,String message){if(closing)return;closing=true;passed=success;reason=message;if(mc.player!=null)mc.player.closeContainer();
        settling=new NativeChunkSettler.Session("progression_terminal_shutdown",240);if(!success)write(mc);}
    private static void shutdown(Minecraft mc){if(mc.getSingleplayerServer()==null){finished=true;write(mc);mc.stop();return;}
        if(settling.failed()){passed=false;reason=settling.failure();}if(!settling.ready()&&!settling.failed())return;
        data.add("settle_terminal",settling.report());finished=true;write(mc);mc.stop();}
    private static void write(Minecraft mc){try{data.addProperty("passed",passed);data.addProperty("reason",reason);data.addProperty("stage",stage);data.addProperty("mode",MODE);data.addProperty("pid",ProcessHandle.current().pid());
        Files.writeString(mc.gameDirectory.toPath().resolve("v6-progression-"+MODE+"-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));}
        catch(Exception failure){failure.printStackTrace();}}
}
