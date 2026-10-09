package pro.erez.interstice.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.gear.*;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.tide.*;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.worldgen.RealmStructuresV5;

/** Prepared Survival interaction proof; resources, platforms, contact resets and operator tide are explicit fixtures. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class GearVisualSmoke {
    private static final String MODE=System.getProperty("interstice.gearSmoke","");
    private static final String WORLD="prepared-gear-and-structure-proof";
    private static final String WEAR="interstice_ballast_wear_tick",PARTIAL="interstice_ballast_active_ticks";
    private static boolean started,finished,closing,passed;
    private static int stage,ticks,structureIndex;
    private static long deadline,wearStartTick;
    private static String reason;
    private static BlockPos base;
    private static CompletableFuture<?> work;
    private static volatile NativeChunkSettler.Session settling;
    private static JsonObject data=new JsonObject();
    private static Field overlay;
    private static int wearStartDamage,wearStartPartial;
    private static float beforeHealth;
    private static int beforeCharges;
    private static List<BlockPos> structureCameras=List.of();
    private static CompoundTag savedPlayer,savedBeacon;
    private GearVisualSmoke() {}

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){var s=settling;if(!MODE.isEmpty()&&s!=null)s.tick(event.getServer());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(MODE.isEmpty()||finished)return;var mc=Minecraft.getInstance();
        try{
            if(closing){shutdown(mc);return;}
            if(!started&&mc.screen instanceof TitleScreen){
                started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(15);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);
                if(MODE.equals("create")){
                    require(!Files.exists(save(mc).resolve("level.dat")),"Disposable gear save already exists");
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings("Prepared native gear proof",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261006L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
                }else{
                    require(MODE.equals("reload"),"Unknown gear smoke mode");data=JsonParser.parseString(Files.readString(report(mc,"create"))).getAsJsonObject();
                    require(data.get("passed").getAsBoolean()&&data.get("creator_pid").getAsLong()!=ProcessHandle.current().pid(),"Cold proof needs a passed creation and different JVM");
                    base=new BlockPos(data.get("base_x").getAsInt(),data.get("base_y").getAsInt(),data.get("base_z").getAsInt());readSavedDisk(mc);mc.createWorldOpenFlows().openWorld(WORLD,()->{});
                }
                return;
            }
            if(!started||SmokeWorldPrompts.advance(mc))return;
            require(System.nanoTime()<deadline,"Gear smoke deadline stage "+stage);
            if(mc.player==null||mc.level==null||mc.getConnection()==null)return;
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            if(stage==0){
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);if(MODE.equals("create"))prepare(p);else verifyCold(p);return true;});stage=MODE.equals("create")?1:50;ticks=0;return;
            }
            if(mc.screen!=null)return;
            if(stage==1&&done()&&++ticks>=80){select(mc,0);useOn(mc,beacon().below());stage=2;ticks=0;}
            else if(stage==2&&++ticks>=20){work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);var b=beaconEntity(p.serverLevel());require(p.getInventory().items.get(0).isEmpty()&&id.equals(b.owner()),"Native BlockItem packet did not place/own the beacon");data.addProperty("ordinary_beacon_blockitem_placement_and_owner",true);return true;});stage=3;}
            else if(stage==3&&done()){select(mc,1);useOn(mc,beacon());stage=4;ticks=0;}
            else if(stage==4&&++ticks>=20){work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);var b=beaconEntity(p.serverLevel());require(p.getInventory().items.get(1).getCount()==1&&b.fuelTicks()>RealmGear.BEACON_FUEL_TICKS-80&&b.fuelTicks()<=RealmGear.BEACON_FUEL_TICKS&&p.serverLevel().getBlockState(beacon()).getValue(RouteBeaconBlock.LIT),"Native crystal packet did not pay one finite beacon fuel charge");data.addProperty("ordinary_one_crystal_fuels_beacon",true);data.addProperty("beacon_fuel_after_client_click",b.fuelTicks());return true;});stage=5;}
            else if(stage==5&&done()){require(mc.level.getBlockState(beacon()).is(RealmGear.BEACON.get())&&mc.level.getBlockState(beacon()).getValue(RouteBeaconBlock.LIT),"Beacon light state did not synchronize to client");select(mc,0);useOn(mc,beacon());stage=6;ticks=0;}
            else if(stage==6&&++ticks>=10){String message=overlayText(mc);require(message.contains(Integer.toString(beacon().getX()))&&message.contains(Integer.toString(beacon().getY()))&&message.contains(Integer.toString(beacon().getZ())),"Ordinary owner click did not show beacon coordinates: "+message);data.addProperty("native_beacon_coordinate_overlay",message);shot(mc,"gear-route-beacon.png");select(mc,2);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);stage=7;ticks=0;}
            else if(stage==7&&++ticks>=15){work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);checkArmor(p.getOffhandItem(),true);require(p.getMainHandItem().is(RealmGear.PROTECTIVE_COATING.get())&&p.getMainHandItem().getCount()==1,"Native coating RMB did not consume exactly one dose");data.addProperty("ordinary_coating_RMB_preserves_name_enchantment_damage_and_data",true);return true;});stage=8;}
            else if(stage==8&&done()){move(mc,45,6);stage=9;ticks=0;}
            else if(stage==9&&++ticks>=15){work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);checkArmor(p.getItemBySlot(EquipmentSlot.CHEST),true);data.addProperty("ordinary_inventory_packets_equip_coated_chestplate",true);contactStart(p,false);return true;});stage=10;ticks=0;}
            else if(stage==10&&done()&&++ticks>=8){mc.options.keyUp.setDown(true);stage=11;ticks=0;work=null;}
            else if(stage==11){require(++ticks<100,"Coated native heavy contact did not happen");if(work==null||work.isDone()){if(work!=null&&Boolean.TRUE.equals(work.join())){mc.options.keyUp.setDown(false);move(mc,6,40);work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);cleanupContact(p);return true;});stage=12;ticks=0;return;}work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);int charges=RealmGear.coatingCharges(p.getItemBySlot(EquipmentSlot.CHEST));if(charges==RealmGear.COATING_CHARGES)return false;require(p.getHealth()==20,"Coated lower sea still dealt native damage");data.addProperty("actual_heavy_contact_protected_health",p.getHealth());data.addProperty("actual_heavy_contact_remaining_charges",charges);return true;});}}
            else if(stage==12&&done()&&++ticks>=25){work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);require(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),"Inventory packets did not stow the coated chestplate");contactStart(p,false);return true;});stage=13;ticks=0;}
            else if(stage==13&&done()&&++ticks>=8){mc.options.keyUp.setDown(true);stage=14;ticks=0;work=null;}
            else if(stage==14){require(++ticks<100,"Bare native heavy contact did not hurt");if(work==null||work.isDone()){if(work!=null&&Boolean.TRUE.equals(work.join())){mc.options.keyUp.setDown(false);move(mc,40,6);work=server.submit(()->{cleanupContact(server.getPlayerList().getPlayer(id));return true;});stage=15;ticks=0;return;}work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);if(p.getHealth()>=20)return false;data.addProperty("actual_uncoated_lower_sea_health",p.getHealth());return true;});}}
            else if(stage==15&&done()&&++ticks>=25){work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);require(!p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),"Native chestplate re-equip failed");beforeCharges=RealmGear.coatingCharges(p.getItemBySlot(EquipmentSlot.CHEST));contactStart(p,true);return true;});stage=16;ticks=0;}
            else if(stage==16&&done()&&++ticks>=8){mc.options.keyUp.setDown(true);stage=17;ticks=0;work=null;}
            else if(stage==17){require(++ticks<100,"Coated native upper toxin did not hurt");if(work==null||work.isDone()){if(work!=null&&Boolean.TRUE.equals(work.join())){mc.options.keyUp.setDown(false);work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);cleanupContact(p);prepareTide(p);return true;});stage=18;ticks=0;return;}work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);if(p.getHealth()>=20)return false;require(RealmGear.coatingCharges(p.getItemBySlot(EquipmentSlot.CHEST))==beforeCharges,"Upper toxin wrongly spent/granted a coating interval");data.addProperty("actual_coated_upper_sea_still_hurts",p.getHealth());return true;});}}
            else if(stage==18&&done()&&mc.level.dimension().equals(IslandWorld.VANILLA_WORLD)&&++ticks>=25){select(mc,3);KeyMapping.click(InputConstants.getKey(org.lwjgl.glfw.GLFW.GLFW_KEY_F,-1));stage=19;ticks=0;}
            else if(stage==19&&++ticks>=15){require(mc.player.getOffhandItem().is(RealmGear.BALLAST_BELT.get()),"Native F packet did not hold the belt offhand");data.addProperty("native_F_swap_places_belt_in_offhand",true);mc.getConnection().sendCommand("interstice tide set surge");stage=20;ticks=0;work=null;}
            else if(stage==20){require(++ticks<300,"Operator SURGE/client counterweight attribute did not reach full intensity");if(!nearGravity(mc,.01304))return;
                if(work==null)work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);require(TideManager.getState(server).phase()==TidePhase.SURGE&&TideManager.getState(server).buoyancyIntensity()>.999&&Math.abs(p.getAttributeValue(Attributes.GRAVITY)-.01304)<.00001,"Server does not corroborate native client counterweight gravity");var belt=p.getOffhandItem();var tag=belt.get(DataComponents.CUSTOM_DATA).copyTag();wearStartTick=tag.getLong(WEAR);wearStartDamage=belt.getDamageValue();wearStartPartial=tag.getInt(PARTIAL);return true;});
                if(done()){data.addProperty("client_full_surge_offhand_belt_gravity",mc.player.getAttributeValue(Attributes.GRAVITY));stage=21;ticks=0;work=null;}
            }
            else if(stage==21){require(++ticks<240,"A hundred actual active belt ticks did not complete");if(work==null||work.isDone()){if(work!=null&&Boolean.TRUE.equals(work.join())){require(nearGravity(mc,.01304),"Client counterweight disappeared during active wear");shot(mc,"gear-native-ballast-surge.png");select(mc,0);move(mc,45,39);stage=22;ticks=0;work=null;return;}work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);var belt=p.getOffhandItem();var tag=belt.get(DataComponents.CUSTOM_DATA).copyTag();long elapsed=tag.getLong(WEAR)-wearStartTick;if(elapsed<100)return false;int active=(belt.getDamageValue()-wearStartDamage)*20+tag.getInt(PARTIAL)-wearStartPartial;require(active==elapsed&&belt.getDamageValue()>=wearStartDamage+5,"Belt wear does not count loaded active server ticks exactly");data.addProperty("actual_active_belt_server_ticks",elapsed);data.addProperty("actual_belt_damage_after_ticks",belt.getDamageValue());data.addProperty("actual_belt_partial_ticks",tag.getInt(PARTIAL));return true;});}}
            else if(stage==22&&++ticks>=10){if(!nearGravity(mc,-.028))return;data.addProperty("client_full_surge_stowed_belt_gravity",mc.player.getAttributeValue(Attributes.GRAVITY));shot(mc,"gear-native-unballasted-surge.png");mc.getConnection().sendCommand("interstice tide set calm");stage=23;ticks=0;}
            else if(stage==23&&++ticks>=15){if(!nearGravity(mc,.08))return;work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);require(TideManager.getState(server).phase()==TidePhase.CALM,"Operator did not reset the prepared SURGE before saving");p.teleportTo(server.overworld(),base.getX()+4.5,base.getY()+1,base.getZ()+3.5,Set.of(),0,0);p.setDeltaMovement(0,0,0);p.fallDistance=0;prepareStructures(p.serverLevel());return true;});stage=24;ticks=0;}
            else if(stage==24&&done()&&mc.level.dimension().equals(Level.OVERWORLD)){structureIndex=0;work=server.submit(()->{structurePose(server.getPlayerList().getPlayer(id),0);return true;});stage=25;ticks=0;}
            else if(stage==25&&done()&&++ticks>=60){shot(mc,"structures-"+RealmStructuresV5.Family.values()[structureIndex].name().toLowerCase(java.util.Locale.ROOT)+"-two-variants.png");
                if(++structureIndex<4){work=server.submit(()->{structurePose(server.getPlayerList().getPlayer(id),structureIndex);return true;});ticks=0;}
                else{work=server.submit(()->{var p=server.getPlayerList().getPlayer(id);p.setGameMode(GameType.SURVIVAL);p.removeAllEffects();p.teleportTo(p.server.overworld(),base.getX()+4.5,base.getY()+1,base.getZ()+3.5,Set.of(),0,0);p.setDeltaMovement(0,0,0);p.fallDistance=0;return true;});stage=26;ticks=0;}
            }
            else if(stage==26&&done()&&++ticks>=30){work=server.submit(()->{snapshot(server.getPlayerList().getPlayer(id));return true;});stage=27;}
            else if(stage==27&&done()){shot(mc,"gear-prepared-survival-saved.png");finish(mc,true,"Native Survival item/menu/movement packets verified three optional gear benefits; eight authored templates displayed only on prepared gallery pads");}
            else if(stage==50&&done()&&++ticks>=40){select(mc,0);useOn(mc,beacon());stage=51;ticks=0;}
            else if(stage==51&&++ticks>=10){require(mc.level.getBlockState(beacon()).getValue(RouteBeaconBlock.LIT),"Cold beacon light did not reach actual client");data.addProperty("cold_native_beacon_coordinate_overlay",overlayText(mc));shot(mc,"gear-cold-beacon-and-components.png");finish(mc,true,"Different JVM retained exact saved armour/belt components and finite beacon owner/fuel; ordinary beacon click works after reload");}
        }catch(Throwable failure){failure.printStackTrace();finish(mc,false,failure.toString());}
    }

    private static BlockPos beacon(){return base.offset(4,1,5);}
    private static RouteBeaconEntity beaconEntity(ServerLevel level){require(level.getBlockEntity(beacon()) instanceof RouteBeaconEntity,"Beacon block entity is missing");return(RouteBeaconEntity)level.getBlockEntity(beacon());}
    private static void prepare(ServerPlayer p){
        var level=p.serverLevel();base=p.blockPosition().below().offset(18,0,18);
        for(int x=-3;x<=17;x++)for(int z=-3;z<=12;z++){level.setBlock(base.offset(x,0,z),Interstice.RIFTSTONE.get().defaultBlockState(),3);for(int y=1;y<=10;y++)level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        p.teleportTo(level,base.getX()+4.5,base.getY()+1,base.getZ()+3.5,Set.of(),0,0);p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.setHealth(20);p.getFoodData().setFoodLevel(20);
        p.getInventory().items.set(0,new ItemStack(RealmGear.BEACON_ITEM.get()));p.getInventory().items.set(1,new ItemStack(MineralEcology.PHOSPHORITE_CRYSTAL.get(),2));p.getInventory().items.set(2,new ItemStack(RealmGear.PROTECTIVE_COATING.get(),2));
        var belt=new ItemStack(RealmGear.BALLAST_BELT.get());belt.set(DataComponents.CUSTOM_NAME,Component.literal("Native saved ballast"));p.getInventory().items.set(3,belt);
        var armor=new ItemStack(Items.DIAMOND_CHESTPLATE);armor.setDamageValue(73);armor.set(DataComponents.CUSTOM_NAME,Component.literal("Native coated expedition armour"));var note=new CompoundTag();note.putInt("gear_marker",917);armor.set(DataComponents.CUSTOM_DATA,CustomData.of(note));armor.enchant(p.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING),3);p.setItemInHand(InteractionHand.OFF_HAND,armor);
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();data.addProperty("creator_pid",ProcessHandle.current().pid());data.addProperty("base_x",base.getX());data.addProperty("base_y",base.getY());data.addProperty("base_z",base.getZ());
        data.addProperty("scope","Fresh disposable Overworld; prepared platform/items and comparative toxin resets. Native Survival RMB, inventory, movement and F-key packets. Operator SURGE and prepared V5 exposure pad. Prepared eight-template model gallery is not natural generation or autonomous Survival gathering.");
    }
    private static void checkArmor(ItemStack armor,boolean fresh){require(armor.is(Items.DIAMOND_CHESTPLATE)&&armor.getHoverName().getString().equals("Native coated expedition armour")&&armor.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("gear_marker")==917&&armor.get(DataComponents.ENCHANTMENTS).entrySet().stream().anyMatch(e->e.getIntValue()==3),"Coating changed original armour identity/name/enchantment/data");if(fresh)require(armor.getDamageValue()==73&&RealmGear.coatingCharges(armor)==12,"Coating changed damage or initial twelve charges");}
    private static void contactStart(ServerPlayer p,boolean light){cleanupContact(p);int x=light?12:8;var level=p.serverLevel();p.teleportTo(level,base.getX()+x+.5,base.getY()+1,base.getZ()+2.5,Set.of(),0,0);p.setDeltaMovement(0,0,0);p.fallDistance=0;p.setHealth(20);p.removeAllEffects();level.setBlock(base.offset(x,1,6),(light?Interstice.LIGHT_BLOCK:Interstice.HEAVY_BLOCK).get().defaultBlockState(),3);data.addProperty("prepared_comparative_contact_resets",true);}
    private static void cleanupContact(ServerPlayer p){var level=p.server.overworld();for(int x=5;x<=15;x++)for(int z=1;z<=11;z++)for(int y=1;y<=3;y++)if(level.getFluidState(base.offset(x,y,z)).getFluidType()==Interstice.HEAVY_TYPE.get()||level.getFluidState(base.offset(x,y,z)).getFluidType()==Interstice.LIGHT_TYPE.get())level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);p.removeAllEffects();p.setHealth(20);p.setDeltaMovement(0,0,0);p.fallDistance=0;}
    private static void prepareTide(ServerPlayer p){var level=p.server.getLevel(IslandWorld.VANILLA_WORLD);require(level!=null,"V5 exposure fixture is absent");for(int x=78;x<=82;x++)for(int z=78;z<=82;z++){level.setBlock(new BlockPos(x,99,z),Interstice.RIFTSTONE.get().defaultBlockState(),3);for(int y=100;y<=207;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}p.teleportTo(level,80.5,100,80.5,Set.of(),0,0);p.setDeltaMovement(0,0,0);p.fallDistance=0;data.addProperty("prepared_v5_exposure_pad_and_operator_tide",true);}
    private static void select(Minecraft mc,int slot){mc.player.getInventory().selected=slot;mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));}
    private static void useOn(Minecraft mc,BlockPos pos){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,.45,0),Direction.UP,pos,false));}
    private static void move(Minecraft mc,int from,int to){mc.setScreen(new InventoryScreen(mc.player));mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId,from,0,ClickType.PICKUP,mc.player);mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId,to,0,ClickType.PICKUP,mc.player);mc.player.closeContainer();}
    private static boolean nearGravity(Minecraft mc,double expected){return mc.player.getAttribute(Attributes.GRAVITY)!=null&&Math.abs(mc.player.getAttributeValue(Attributes.GRAVITY)-expected)<.0001;}
    private static String overlayText(Minecraft mc)throws ReflectiveOperationException{if(overlay==null){overlay=Gui.class.getDeclaredField("overlayMessageString");overlay.setAccessible(true);}var value=overlay.get(mc.gui);return value instanceof Component c?c.getString():"";}
    private static void snapshot(ServerPlayer p){checkArmor(p.getItemBySlot(EquipmentSlot.CHEST),false);var belt=p.getInventory().items.get(3);require(belt.is(RealmGear.BALLAST_BELT.get())&&belt.getDamageValue()>=5&&p.getOffhandItem().isEmpty(),"Ballast native stow/save failed");var b=beaconEntity(p.serverLevel());require(b.fuelTicks()>0&&b.fuelTicks()<2400&&p.getUUID().equals(b.owner()),"Beacon did not preserve only its remaining paid fuel");data.addProperty("armor_snbt",p.getItemBySlot(EquipmentSlot.CHEST).save(p.registryAccess()).toString());data.addProperty("belt_snbt",belt.save(p.registryAccess()).toString());data.addProperty("beacon_snapshot_fuel",b.fuelTicks());data.addProperty("snapshot_world_tick",p.serverLevel().getGameTime());data.addProperty("operator_calm_reset_before_cold_save",true);}
    private static void readSavedDisk(Minecraft mc)throws Exception{
        var root=NbtIo.readCompressed(save(mc).resolve("level.dat"),NbtAccounter.unlimitedHeap());savedPlayer=root.getCompound("Data").getCompound("Player");require(!savedPlayer.isEmpty(),"Cold saved player record is absent");
        int cx=beacon().getX()>>4,cz=beacon().getZ()>>4;Path folder=save(mc).resolve("region"),file=folder.resolve("r."+Math.floorDiv(cx,32)+"."+Math.floorDiv(cz,32)+".mca");
        require(Files.exists(file),"Cold beacon region file is absent");
        try(var region=new RegionFile(new RegionStorageInfo(WORLD,Level.OVERWORLD,"chunk"),file,folder,false);var input=region.getChunkDataInputStream(new ChunkPos(cx,cz))){require(input!=null,"Cold beacon region is missing");var chunk=NbtIo.read(input);for(var tag:chunk.getList("block_entities",Tag.TAG_COMPOUND)){var t=(CompoundTag)tag;if(t.getInt("x")==beacon().getX()&&t.getInt("y")==beacon().getY()&&t.getInt("z")==beacon().getZ())savedBeacon=t.copy();}}
        require(savedBeacon!=null&&savedBeacon.getInt("FuelTicks")>0&&savedBeacon.getInt("FuelTicks")<=data.get("beacon_snapshot_fuel").getAsInt(),"Saved beacon refilled or lost its paid finite fuel");
        CompoundTag armor=null,belt=null;for(var tag:savedPlayer.getList("Inventory",Tag.TAG_COMPOUND)){var t=(CompoundTag)tag;int slot=t.getByte("Slot")&255;var copy=t.copy();copy.remove("Slot");if(slot==102)armor=copy;if(slot==3)belt=copy;}
        require(TagParser.parseTag(data.get("armor_snbt").getAsString()).equals(armor)&&TagParser.parseTag(data.get("belt_snbt").getAsString()).equals(belt),"Exact saved armour/belt components differ before cold world loading");data.addProperty("disk_exact_armor_and_belt_components_before_open",true);data.addProperty("disk_beacon_fuel_remaining",savedBeacon.getInt("FuelTicks"));
    }
    private static void verifyCold(ServerPlayer p){require(p.serverLevel().dimension().equals(Level.OVERWORLD),"Saved gear player returned in wrong dimension");checkArmor(p.getItemBySlot(EquipmentSlot.CHEST),false);require(p.getInventory().items.get(3).save(p.registryAccess()).equals(TagParserSafe(data.get("belt_snbt").getAsString())),"Cold belt wear/name/partial ticks did not remain exact");var b=beaconEntity(p.serverLevel());require(b.fuelTicks()>0&&b.fuelTicks()<=savedBeacon.getInt("FuelTicks")&&p.getUUID().equals(b.owner()),"Cold beacon reset fuel or owner");require(TideManager.getState(p.server).phase()==TidePhase.CALM,"Prepared SURGE persisted despite explicit reset");data.addProperty("reload_pid",ProcessHandle.current().pid());data.addProperty("cold_beacon_runtime_remaining_fuel",b.fuelTicks());data.addProperty("cold_belt_and_coating_and_beacon_owner_preserved",true);}
    private static CompoundTag TagParserSafe(String text){try{return TagParser.parseTag(text);}catch(Exception e){throw new IllegalStateException(e);}}

    private static void prepareStructures(ServerLevel level){var points=new ArrayList<BlockPos>();var evidence=new JsonArray();int family=0;
        for(var kind:RealmStructuresV5.Family.values()){BlockPos origin=base.offset(48+family*48,1,0);
            for(int x=-2;x<=34;x++)for(int z=-25;z<=16;z++){level.setBlock(origin.offset(x,-1,z),Interstice.RIFTSTONE.get().defaultBlockState(),3);for(int y=0;y<=18;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
            for(int variant=0;variant<2;variant++){
                var at=origin.offset(variant*18,0,0);
                var template=level.getStructureManager().get(kind.template(variant)).orElseThrow();var size=template.getSize();
                // Read every authored container position, instead of assuming that cargo stations
                // have the same single barrel as refuges/laboratories/observation posts.
                var authored=new java.util.HashSet<BlockPos>();
                for(var entry:template.save(new CompoundTag()).getList("blocks",Tag.TAG_COMPOUND)){
                    var block=(CompoundTag)entry;if(!block.contains("nbt"))continue;
                    var nbt=block.getCompound("nbt");if(!nbt.contains("LootTable"))continue;
                    require(nbt.getString("id").equals("minecraft:barrel")&&nbt.getString("LootTable").equals(kind.lootTable().toString()),"Authored template contains unexpected loot metadata");
                    var position=block.getList("pos",Tag.TAG_INT);var point=at.offset(position.getInt(0),position.getInt(1),position.getInt(2));
                    require(authored.add(point),"Authored template repeats a container position");
                }
                int intended=kind==RealmStructuresV5.Family.CARGO_STATION?2:1;
                require(authored.size()==intended,"Authored template changed its documented barrel count: "+kind.template(variant));
                require(template.placeInWorld(level,at,at,new StructurePlaceSettings().setRotation(Rotation.NONE).setIgnoreEntities(true),level.random,3),"Authored template preview could not be physically placed");
                int containers=0;
                for(int x=0;x<size.getX();x++)for(int y=0;y<size.getY();y++)for(int z=0;z<size.getZ();z++){
                    var point=at.offset(x,y,z);var be=level.getBlockEntity(point);
                    if(be instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity chest){
                        require(authored.contains(point)&&chest.getLootTable()!=null&&chest.getLootTable().location().equals(kind.lootTable()),"Physical template lost or duplicated an authored loot container at "+point);containers++;
                    }
                }
                require(containers==authored.size(),"Physical template container count differs from its exact NBT contract");
                var o=new JsonObject();o.addProperty("template",kind.template(variant).toString());o.addProperty("x",at.getX());o.addProperty("y",at.getY());o.addProperty("z",at.getZ());o.addProperty("size_x",size.getX());o.addProperty("size_y",size.getY());o.addProperty("size_z",size.getZ());
                o.addProperty("physical_native_loot_table",kind.lootTable().toString());o.addProperty("authored_expected_container_count",authored.size());o.addProperty("native_container_count",containers);evidence.add(o);
            }
            points.add(origin.offset(15,8,-22));family++;}
        structureCameras=List.copyOf(points);data.add("prepared_eight_authored_template_models",evidence);data.addProperty("structure_gallery_scope","All eight actual NBT templates placed through vanilla StructureTemplate.placeInWorld on prepared Overworld pads; this is not a natural frequency or survival loot demonstration.");}
    private static void structurePose(ServerPlayer p,int which){var camera=structureCameras.get(which);p.setGameMode(GameType.CREATIVE);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));p.teleportTo(p.server.overworld(),camera.getX()+.5,camera.getY(),camera.getZ()+.5,Set.of(),0,14);p.getAbilities().flying=true;p.onUpdateAbilities();p.setDeltaMovement(0,0,0);p.fallDistance=0;}
    private static boolean done(){if(work==null||!work.isDone())return false;work.join();return true;}
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException(why);}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println("GEAR_NATIVE_SCREENSHOT "+name));}
    private static Path save(Minecraft mc){return mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);}
    private static Path report(Minecraft mc,String mode){return mc.gameDirectory.toPath().resolve("gear-"+mode+"-validation.json");}
    private static void finish(Minecraft mc,boolean success,String why){if(closing)return;closing=true;passed=success;reason=why;mc.options.keyUp.setDown(false);if(mc.player!=null)mc.player.closeContainer();mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.options.broadcastOptions();settling=new NativeChunkSettler.Session("gear_native_terminal_shutdown",2000);if(!success)write(mc,true);}
    private static void shutdown(Minecraft mc){if(mc.getSingleplayerServer()==null){finished=true;write(mc,false);mc.stop();return;}if(settling.failed()&&!data.has("quiescence_failed")){passed=false;reason=settling.failure();data.addProperty("quiescence_failed",true);write(mc,true);}if(!settling.ready())return;data.add("settle_terminal",settling.report());data.addProperty("clean_generation_before_mc_stop",true);finished=true;settling=null;write(mc,false);mc.stop();}
    private static void write(Minecraft mc,boolean pending){data.addProperty("passed",passed);data.addProperty("reason",reason);data.addProperty("stage",stage);data.addProperty("mode",MODE);data.addProperty("shutdown_pending",pending);try{Files.writeString(report(mc,MODE),new GsonBuilder().setPrettyPrinting().create().toJson(data));}catch(Exception failure){failure.printStackTrace();}System.out.println("GEAR_NATIVE_VALIDATION "+data);}
}
