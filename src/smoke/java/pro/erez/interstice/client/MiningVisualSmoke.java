package pro.erez.interstice.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.TideSproutBlock;
import pro.erez.interstice.minerals.LivingTorchBlockEntity;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.minerals.RiftOreBlock;
import pro.erez.interstice.minerals.SproutHarvestData;
import pro.erez.interstice.tide.TideManager;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.tide.TideSync;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.worldgen.RealmBiomes;

/** Natural ore identity and real mining, torch placement, furnace ticks and cycle-locked upper-bud harvest. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class MiningVisualSmoke {
    private static final String MODE=System.getProperty("interstice.miningSmoke","");
    private static final String WORLD="native-mining-light-bud-check";
    private static boolean started,finished,shutdownRequested,pendingPassed,finalCheck;
    private static String pendingReason;
    private static long deadline;
    private static int stage,ticks,placed;
    private static CompletableFuture<?> work;
    private static volatile NativeChunkSettler.Session settling;
    private static JsonObject data=new JsonObject();
    private static List<Ore> ores=List.of();
    private static Room room,livingRoom;
    private static Ore viewed;
    private static Field furnaceData;
    private record Ore(String type,BlockPos pos,BlockState state,int depth) {}
    private record Room(ServerLevel level,BlockPos base) {}
    private MiningVisualSmoke() {}

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){var session=settling;if(!MODE.isEmpty()&&session!=null)session.tick(event.getServer());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(MODE.isEmpty()||finished)return;var mc=Minecraft.getInstance();
        try {
            if(shutdownRequested){shutdown(mc);return;}
            if(!started&&mc.screen instanceof TitleScreen) {
                started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(10);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.options.renderDistance().set(MODE.equals("create")?4:2);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);
                if(MODE.equals("create")) {
                    require(!Files.exists(world(mc).resolve("level.dat")),"Disposable mining save already exists");
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings("Disposable mining light and harvest",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261006L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
                } else {
                    require(MODE.equals("reload"),"Unknown mining mode");data=JsonParser.parseString(Files.readString(report(mc,"create"))).getAsJsonObject();
                    require(data.get("passed").getAsBoolean()&&data.get("creator_pid").getAsLong()!=ProcessHandle.current().pid(),"Reload requires successful creation in another JVM");mc.createWorldOpenFlows().openWorld(WORLD,()->{});
                }return;
            }
            if(!started||SmokeWorldPrompts.advance(mc))return;require(System.nanoTime()<deadline,"Mining deadline at stage "+stage);
            if(mc.player==null||mc.level==null||mc.screen!=null||mc.getConnection()==null)return;
            var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
            if(stage==0) {
                if(MODE.equals("create")){mc.getConnection().sendCommand("interstice explore living");stage=1;ticks=0;}
                else {work=server.submit(()->{reload(server.getPlayerList().getPlayer(uuid));return true;});stage=40;ticks=0;}return;
            }
            if(stage==1&&mc.level.dimension().equals(IslandWorld.LIVING_WORLD)&&++ticks>=40) {
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);ores=survey(p.serverLevel());inspect(p,ore("silver"));return true;});stage=2;ticks=0;
            } else if(stage==2&&done()) {
                if(!oreReady(mc)){ticks=0;return;}if(++ticks<80)return;shot(mc,"mining-natural-silver-host.png");
                work=server.submit(()->{inspect(server.getPlayerList().getPlayer(uuid),ore("phosphorite"));return true;});stage=3;ticks=0;
            } else if(stage==3&&done()) {
                if(!oreReady(mc)){ticks=0;return;}if(++ticks<80)return;shot(mc,"mining-natural-phosphorite-host.png");
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);inspect(p,ore("coal"));p.server.setDifficulty(Difficulty.NORMAL,true);p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getInventory().items.set(0,new ItemStack(Items.DIAMOND_PICKAXE));p.getInventory().setChanged();p.containerMenu.broadcastChanges();return true;});stage=4;ticks=0;
            } else if(stage==4&&done()&&++ticks>=40) {
                mc.player.getInventory().selected=0;look(mc,Vec3.atCenterOf(ore("coal").pos));
                require(mc.gameMode.startDestroyBlock(ore("coal").pos,Direction.NORTH),"Ordinary client refused to start mining natural coal");stage=5;ticks=0;
            } else if(stage==5) {
                require(++ticks<300,"Natural ore did not break through client digging");look(mc,Vec3.atCenterOf(ore("coal").pos));mc.gameMode.continueDestroyBlock(ore("coal").pos,Direction.NORTH);
                if(!mc.level.getBlockState(ore("coal").pos).is(MineralEcology.UMBRAL_COAL_ORE.get())) {
                    mc.gameMode.stopDestroyBlock();work=server.submit(()->proveMining(server.getPlayerList().getPlayer(uuid)));stage=6;ticks=0;
                }
            } else if(stage==6&&done()) {
                if(Boolean.TRUE.equals(work.join())){mc.options.keyUp.setDown(true);stage=7;ticks=0;}
                else{require(++ticks<180,"Server did not confirm ore removal, one tool durability and one coal: "+data);work=server.submit(()->proveMining(server.getPlayerList().getPlayer(uuid)));}
            }
            else if(stage==7) {
                require(++ticks<100,"Natural coal drop was not picked up");
                if(mc.player.getInventory().countItem(MineralEcology.UMBRAL_COAL.get())==1) {
                    stop(mc);work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);var level=server.getLevel(IslandWorld.LIVING_WORLD);var base=IslandWorld.findLanding(level).offset(12,0,0);room=makeRoom(p,level,base);livingRoom=room;data.addProperty("ordinary_natural_coal_pickup",true);return true;});stage=8;ticks=0;
                }
            } else if(stage==8&&done()&&++ticks>=40) {placed=0;stage=9;ticks=0;}
            else if(stage==9&&++ticks>=20) {placeTorch(mc,placed);stage=10;ticks=0;}
            else if(stage==10&&++ticks>=20) {work=server.submit(()->{checkTorch(placed);return true;});stage=11;ticks=0;}
            else if(stage==11&&done()) {
                if(++placed<4){stage=9;ticks=0;}else{work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);p.removeEffect(MobEffects.DIG_SPEED);require(LivingTorchBlockEntity.reaches(room.level,torch(2),p)&&LivingTorchBlockEntity.reaches(room.level,torch(3),p),"Open living torches lack actual line of sight");return true;});stage=12;ticks=0;}
            } else if(stage==12&&done()) {
                require(++ticks<300,"Native torch ticker did not grant Haste I");
                if(mc.player.hasEffect(MobEffects.DIG_SPEED)&&mc.player.getEffect(MobEffects.DIG_SPEED).getAmplifier()==0) {
                    work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);require(p.hasEffect(MobEffects.DIG_SPEED)&&p.getEffect(MobEffects.DIG_SPEED).getAmplifier()==0,"Server lacks native Haste I");
                        data.addProperty(room.level.dimension().equals(IslandWorld.LIVING_WORLD)?"living_realm_native_haste_one":"overworld_native_haste_one",true);return true;});stage=13;ticks=0;
                }
            } else if(stage==13&&done()&&++ticks>=60) {
                shot(mc,room.level.dimension().equals(IslandWorld.LIVING_WORLD)?"mining-dual-torches-living.png":"mining-dual-torches-overworld.png");
                if(room.level.dimension().equals(IslandWorld.LIVING_WORLD)) {
                    work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);barrier(true);p.removeEffect(MobEffects.DIG_SPEED);
                        require(!LivingTorchBlockEntity.reaches(room.level,torch(2),p)&&!LivingTorchBlockEntity.reaches(room.level,torch(3),p),"Living torch crosses a solid fixture wall");data.addProperty("los_blocked_at_tick",room.level.getGameTime());return true;});stage=14;ticks=0;
                } else {work=server.submit(()->{prepareSprout(server.getPlayerList().getPlayer(uuid));return true;});stage=30;ticks=0;}
            } else if(stage==14&&done()&&++ticks>=20) {
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);if(room.level.getGameTime()-data.get("los_blocked_at_tick").getAsLong()<80)return false;
                    require(!p.hasEffect(MobEffects.DIG_SPEED),"Blocked native aura still refreshes Haste");data.addProperty("native_haste_blocked_by_real_wall",true);barrier(false);return true;});stage=15;ticks=0;
            } else if(stage==15&&done()) {if(Boolean.TRUE.equals(work.join())){stage=16;ticks=0;}else{stage=14;ticks=0;}}
            else if(stage==16&&++ticks>=20) {
                mc.player.getInventory().selected=2;var support=room.base.offset(0,-1,1);use(mc,support,Direction.UP);stage=17;ticks=0;
            } else if(stage==17&&++ticks>=20) {
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);var furnace=furnace();
                    int coalSlot=-1;for(int i=0;i<p.getInventory().items.size();i++)if(p.getInventory().items.get(i).is(MineralEcology.UMBRAL_COAL.get())){coalSlot=i;break;}
                    require(coalSlot>=0,"The actually mined coal was lost before the furnace proof");var fuel=p.getInventory().removeItem(coalSlot,1);
                    furnace.setItem(0,new ItemStack(Items.RAW_IRON));furnace.setItem(1,fuel);furnace.setChanged();p.containerMenu.broadcastChanges();
                    data.addProperty("furnace_fuel_is_naturally_mined_coal",true);data.addProperty("prepared_furnace_input","One raw iron and a furnace supplied only for the native duration proof");data.addProperty("furnace_start_tick",room.level.getGameTime());return true;});stage=18;ticks=0;
            } else if(stage==18&&done()&&++ticks>=10) {
                work=server.submit(()->{var f=furnace();var values=furnaceValues(f);if(values.get(0)<=0)return false;
                    require(values.get(1)==3200&&values.get(0)>3000,"Actual vanilla furnace did not start the 3200-tick coal burn");data.addProperty("native_furnace_burn_duration_ticks",values.get(1));data.addProperty("native_furnace_initial_lit_ticks",values.get(0));return true;});stage=19;ticks=0;
            } else if(stage==19&&done()) {if(Boolean.TRUE.equals(work.join())){stage=20;ticks=0;}else{stage=18;ticks=0;}}
            else if(stage==20&&++ticks>=20) {
                work=server.submit(()->{var f=furnace();if(!f.getItem(2).is(Items.IRON_INGOT))return false;var values=furnaceValues(f);
                    require(f.getItem(2).getCount()==1&&values.get(0)>1600,"Native coal either failed to smelt or had only vanilla coal's duration");
                    data.addProperty("native_furnace_smelted_one_raw_iron",true);data.addProperty("native_furnace_remaining_after_smelt",values.get(0));data.addProperty("native_furnace_ticks_to_smelt",room.level.getGameTime()-data.get("furnace_start_tick").getAsLong());return true;});stage=21;ticks=0;
            } else if(stage==21&&done()) {
                if(Boolean.TRUE.equals(work.join())){work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);var ow=server.overworld();var base=ow.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,ow.getSharedSpawnPos()).offset(12,0,0);room=makeRoom(p,ow,base);return true;});stage=8;ticks=0;}
                else{stage=20;ticks=0;}
            } else if(stage==30&&done()) {
                lower(mc);settling=new NativeChunkSettler.Session("before_native_upper_bud_harvest",2000);stage=31;ticks=0;
            } else if(stage==31) {
                require(!settling.failed(),"Pre-harvest settling failed: "+settling.failure());if(!settling.ready())return;
                data.add("settle_before_harvest",settling.report());settling=null;
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);if(!openSprout(p.serverLevel()))return false;approachTop(p);return true;});stage=32;ticks=0;
            } else if(stage==32&&done()) {
                if(!Boolean.TRUE.equals(work.join())){work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);if(!openSprout(p.serverLevel()))return false;approachTop(p);return true;});return;}
                if(++ticks<40)return;mc.player.getInventory().selected=3;use(mc,top(),Direction.UP);stage=33;ticks=0;
            } else if(stage==33&&++ticks>=30) {
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);proveHarvest(p);return true;});stage=34;ticks=0;
            } else if(stage==34&&done()&&++ticks>=60) {
                shot(mc,"mining-original-opened-upper-bud.png");finish(mc,true,"Natural hosted ores and ordinary coal mining, floor/wall torches in both dimensions, native furnace and original upper-bud harvest passed");
            } else if(stage==40&&done()&&++ticks>=40) {mc.player.getInventory().selected=3;use(mc,top(),Direction.UP);stage=41;ticks=0;}
            else if(stage==41&&++ticks>=30) {
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);require(p.getInventory().countItem(MineralEcology.LUMINOUS_BUD.get())==1&&p.getInventory().items.get(3).getDamageValue()==1,"Cold restart allowed another same-SURGE bud or durability loss");
                    require(SproutHarvestData.get(p.serverLevel()).locked(position("sprout_root"),TideManager.getState(server).totalCycles()),"Repeated native use lost the saved lock");data.addProperty("ordinary_repeated_top_use_rejected_after_cold_restart",true);return true;});stage=42;ticks=0;
            } else if(stage==42&&done())finish(mc,true,"Cold restart preserved the original plant and its same-SURGE harvest lock; ordinary repeated shears use yielded no second bud");
        } catch(Throwable error){error.printStackTrace();finish(mc,false,error.toString());}
    }

    private static List<Ore> survey(ServerLevel level) {
        require(((IslandChunkGenerator)level.getChunkSource().getGenerator()).terrainRevision()==4,"Mining smoke entered an archived generator");
        var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();var profile=generator.geometry();var sampler=level.getChunkSource().randomState().sampler();Ore[] found=new Ore[4];int generated=0;
        String[] names={"silver","coal","phosphorite","vitriolite"};
        for(int radius=0;radius<=32;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            require(System.nanoTime()<deadline,"Natural mineral survey deadline");if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            int x=dx*64,z=dz*64;boolean needsDeep=found[3]==null;
            if(needsDeep&&!generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),12,QuartPos.fromBlock(z),sampler).is(RealmBiomes.STONE_VAULTS))continue;
            require(generated<48,"Natural ore survey exceeded its bounded candidate budget");var chunk=level.getChunk(x>>4,z>>4);generated++;
            for(int px=chunk.getPos().getMinBlockX();px<=chunk.getPos().getMaxBlockX();px++)for(int pz=chunk.getPos().getMinBlockZ();pz<=chunk.getPos().getMaxBlockZ();pz++) {
                int ceiling=-1;for(int y=profile.maxLand();y>=profile.minY()+6;y--)if(RiftOreBlock.Host.from(chunk.getBlockState(new BlockPos(px,y,pz)))!=null){ceiling=y;break;}
                for(int y=profile.minY()+6;y<=profile.maxLand();y++) {
                    var pos=new BlockPos(px,y,pz);var state=chunk.getBlockState(pos);if(!(state.getBlock() instanceof RiftOreBlock))continue;
                    int kind=state.is(MineralEcology.RIFTSILVER_SEAM.get())?0:state.is(MineralEcology.UMBRAL_COAL_ORE.get())?1:state.is(MineralEcology.PHOSPHORITE_ORE.get())?2:state.is(MineralEcology.VITRIOLITE_ORE.get())?3:-1;
                    if(kind>=0&&found[kind]==null)found[kind]=new Ore(names[kind],pos.immutable(),state,ceiling-y);
                }
            }
            boolean all=true;for(var o:found)all&=o!=null;
            if(all){var entries=new JsonArray();for(var o:found){var j=new JsonObject();j.addProperty("type",o.type);j.addProperty("actual_block",BuiltInRegistries.BLOCK.getKey(o.state.getBlock()).toString());j.addProperty("host",o.state.getValue(RiftOreBlock.HOST).getSerializedName());j.addProperty("host_stone",BuiltInRegistries.BLOCK.getKey(o.state.getValue(RiftOreBlock.HOST).stone().getBlock()).toString());position(j,"ore",o.pos);j.addProperty("depth_below_host_top",o.depth);j.addProperty("naturally_generated",true);entries.add(j);}
                data.add("natural_ores",entries);data.addProperty("creator_pid",ProcessHandle.current().pid());data.addProperty("natural_ore_candidate_chunks",generated);data.addProperty("seed",level.getSeed());
                data.addProperty("scope","Disposable native mechanics proof with prepared inventory, inspection windows, supported rooms/scaffold and explicit SURGE; not an autonomous Survival progression route");return List.of(found);}
        }throw new IllegalStateException("Four naturally generated mineral types not found");
    }

    private static Ore ore(String type){return ores.stream().filter(o->o.type.equals(type)).findFirst().orElseThrow();}
    private static boolean proveMining(ServerPlayer p){
        var at=ore("coal").pos;var replacement=p.serverLevel().getBlockState(at);
        int loot=p.getInventory().countItem(MineralEcology.UMBRAL_COAL.get())+p.serverLevel().getEntitiesOfClass(ItemEntity.class,new AABB(at).inflate(4),e->e.getItem().is(MineralEcology.UMBRAL_COAL.get())).stream().mapToInt(e->e.getItem().getCount()).sum();
        int damage=p.getMainHandItem().getDamageValue();
        data.addProperty("server_mined_cell_replacement",BuiltInRegistries.BLOCK.getKey(replacement.getBlock()).toString());data.addProperty("server_mining_tool_damage",damage);data.addProperty("server_mining_observed_loot",loot);
        require(damage<=1&&loot<=1,"Natural coal mining duplicated loot or repeated the tool action");
        // A mined cell can immediately become fluid, and client prediction precedes server acknowledgement.
        // Exact server loot and durability still prove the single ordinary mining action.
        if(replacement.is(MineralEcology.UMBRAL_COAL_ORE.get())||damage==0||loot==0)return false;
        require(damage==1&&loot==1,"Native natural mining lacks its exact useful coal/tool result");
        data.addProperty("ordinary_client_natural_coal_mining",true);data.addProperty("natural_coal_loot_count",loot);return true;
    }
    private static void inspect(ServerPlayer p,Ore ore) {
        var level=p.serverLevel();viewed=ore;
        require(level.getBlockState(ore.pos).equals(ore.state),"Natural ore changed before inspection");
        // Seal the disposable inspection box before draining it. A backing layer behind the
        // natural ore keeps its removal from opening the proof room to the exterior lower sea.
        var host=ore.state.getValue(RiftOreBlock.HOST).stone();
        for(int dx=-2;dx<=2;dx++)for(int dz=-4;dz<=1;dz++)for(int dy=-2;dy<=3;dy++){
            var cell=ore.pos.offset(dx,dy,dz);
            boolean shell=Math.abs(dx)==2||dz==-4||dz>=0||dy==-2||dy==3;
            if(shell&&!(level.getBlockState(cell).getBlock() instanceof RiftOreBlock))level.setBlock(cell,host,3);
        }
        for(int dx=-1;dx<=1;dx++)for(int dz=-3;dz<=-1;dz++)for(int dy=-1;dy<=2;dy++){
            var cell=ore.pos.offset(dx,dy,dz);if(!(level.getBlockState(cell).getBlock() instanceof RiftOreBlock))level.setBlock(cell,Blocks.AIR.defaultBlockState(),3);
        }
        var reference=ore.pos.east();if(!(level.getBlockState(reference).getBlock() instanceof RiftOreBlock))level.setBlock(reference,ore.state.getValue(RiftOreBlock.HOST).stone(),3);
        data.addProperty("prepared_inspection_windows","Sealed dry comparison boxes were prepared only in the disposable save; natural ore cells were neither placed nor changed. Surrounding host blocks and the backing layer are inspection fixtures.");
        data.addProperty("prepared_dry_ore_inspection_boxes",true);
        p.setGameMode(GameType.CREATIVE);p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,12000,0,false,false));
        camera(p,level,ore.pos.getX()+.5,ore.pos.getY()-1,ore.pos.getZ()-2.5,Vec3.atCenterOf(ore.pos));
    }
    private static boolean oreReady(Minecraft mc){return mc.level.hasChunkAt(viewed.pos)&&mc.level.getBlockState(viewed.pos).equals(viewed.state)&&mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(viewed.pos))<20&&mc.player.hasEffect(MobEffects.NIGHT_VISION);}

    private static Room makeRoom(ServerPlayer p,ServerLevel level,BlockPos base) {
        // Preserve the physically mined coal before replacing the prepared hotbar tools.
        ItemStack minedCoal=ItemStack.EMPTY;
        for(int slot=0;slot<p.getInventory().items.size();slot++)if(p.getInventory().items.get(slot).is(MineralEcology.UMBRAL_COAL.get())){
            minedCoal=p.getInventory().removeItem(slot,p.getInventory().items.get(slot).getCount());break;
        }
        for(int cx=(base.getX()>>4)-1;cx<=(base.getX()>>4)+1;cx++)for(int cz=(base.getZ()>>4)-1;cz<=(base.getZ()>>4)+1;cz++)level.getChunk(cx,cz);
        var stone=level.dimension().equals(IslandWorld.LIVING_WORLD)?Interstice.RIFTSTONE.get().defaultBlockState():Blocks.STONE.defaultBlockState();
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=-1;y<=4;y++)level.setBlock(base.offset(x,y,z),y==-1||y==4||Math.abs(x)==3||Math.abs(z)==3?stone:Blocks.AIR.defaultBlockState(),3);
        var r=new Room(level,base);p.setGameMode(GameType.SURVIVAL);p.removeAllEffects();p.setHealth(20);p.getFoodData().setFoodLevel(20);
        p.getInventory().items.set(0,new ItemStack(MineralEcology.COAL_TORCH_ITEM.get(),2));p.getInventory().items.set(1,new ItemStack(MineralEcology.LIVING_TORCH_ITEM.get(),2));
        p.getInventory().items.set(2,new ItemStack(Items.FURNACE));p.getInventory().items.set(3,new ItemStack(Items.SHEARS));p.getInventory().setChanged();p.containerMenu.broadcastChanges();
        if(!minedCoal.isEmpty()){p.getInventory().items.set(8,minedCoal);p.getInventory().setChanged();p.containerMenu.broadcastChanges();}
        camera(p,level,base.getX()+.5,base.getY(),base.getZ()-1.5,new Vec3(base.getX()+.5,base.getY()+1,base.getZ()+.5));
        position(data,level.dimension().equals(IslandWorld.LIVING_WORLD)?"living_room":"overworld_room",base);data.addProperty("prepared_lighting_rooms",true);return r;
    }
    private static BlockPos torch(int index){return room.base.offset(index==0?-1:index==1?-2:index==2?1:2,index==1||index==3?1:0,0);}
    private static void placeTorch(Minecraft mc,int index) {
        mc.player.getInventory().selected=index<2?0:1;
        if(index==0||index==2)use(mc,torch(index).below(),Direction.UP);
        else use(mc,torch(index).relative(index==1?Direction.WEST:Direction.EAST),index==1?Direction.EAST:Direction.WEST);
    }
    private static void checkTorch(int index) {
        var state=room.level.getBlockState(torch(index));var expected=index==0?MineralEcology.COAL_TORCH.get():index==1?MineralEcology.COAL_WALL_TORCH.get():index==2?MineralEcology.LIVING_TORCH.get():MineralEcology.LIVING_WALL_TORCH.get();
        require(state.is(expected)&&state.getLightEmission(room.level,torch(index))==(index<2?12:13),"Ordinary item use did not place the correct floor/wall torch");
        data.addProperty((room.level.dimension().equals(IslandWorld.LIVING_WORLD)?"living":"overworld")+"_ordinary_torch_placement_"+index,true);
    }
    private static void barrier(boolean blocked){for(int x=-2;x<=2;x++)for(int y=0;y<3;y++)room.level.setBlock(room.base.offset(x,y,-1),blocked?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);}
    private static FurnaceBlockEntity furnace(){var entity=room.level.getBlockEntity(room.base.offset(0,0,1));require(entity instanceof FurnaceBlockEntity,"Ordinary item use did not place a native vanilla furnace");return (FurnaceBlockEntity)entity;}
    private static ContainerData furnaceValues(FurnaceBlockEntity furnace){try{if(furnaceData==null){furnaceData=AbstractFurnaceBlockEntity.class.getDeclaredField("dataAccess");furnaceData.setAccessible(true);}return (ContainerData)furnaceData.get(furnace);}catch(ReflectiveOperationException error){throw new IllegalStateException("Native furnace data inspection failed",error);}}

    private static void prepareSprout(ServerPlayer p) {
        room=livingRoom;var level=room.level;var root=room.base.offset(8,0,0);level.getChunk(root.getX()>>4,root.getZ()>>4);
        for(int y=0;y<=6;y++)level.setBlock(root.above(y),Blocks.AIR.defaultBlockState(),3);
        level.setBlock(root.below(),Interstice.ABYSSAL_TURF.get().defaultBlockState(),3);level.setBlock(root,Interstice.TIDE_SPROUT.get().defaultBlockState(),3);
        TideManager.getSavedData(p.server).setPhase(TidePhase.SURGE,10000);TideSync.broadcast(TideManager.getState(p.server));
        p.getInventory().items.set(3,new ItemStack(Items.SHEARS));p.getInventory().setChanged();p.containerMenu.broadcastChanges();
        camera(p,level,room.base.getX()+.5,room.base.getY(),room.base.getZ()-1.5,new Vec3(room.base.getX()+.5,room.base.getY()+1,room.base.getZ()+.5));
        position(data,"sprout_root",root);data.addProperty("prepared_original_closed_sprout_and_surging_tide",true);data.addProperty("growth_scope","Only the closed original root and explicit tide phase were prepared; normal scheduled ticks grow all upper sections");
    }
    private static boolean openSprout(ServerLevel level) {
        var root=position("sprout_root");var state=level.getBlockState(root);int expected=2+Math.abs((root.getX()*31+root.getZ()*17)%5);
        return state.is(Interstice.TIDE_SPROUT.get())&&state.getValue(TideSproutBlock.HEIGHT)==expected&&state.getValue(TideSproutBlock.BLOOMED)
                &&level.getBlockState(root.above(expected)).is(Interstice.TIDE_SPROUT.get())&&level.getBlockState(root.above(expected)).getValue(TideSproutBlock.SECTION)==3;
    }
    private static void approachTop(ServerPlayer p) {
        var level=p.serverLevel();var root=position("sprout_root");int height=level.getBlockState(root).getValue(TideSproutBlock.HEIGHT);position(data,"sprout_top",root.above(height));
        var feet=root.offset(2,height-1,-1);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){level.setBlock(feet.offset(x,-1,z),Interstice.RIFTSTONE.get().defaultBlockState(),3);level.setBlock(feet.offset(x,3,z),Interstice.RIFTSTONE.get().defaultBlockState(),3);}
        p.removeAllEffects();camera(p,level,feet.getX()+.5,feet.getY(),feet.getZ()+.5,Vec3.atCenterOf(root.above(height)));data.addProperty("prepared_upper_flower_approach_scaffold",true);
    }
    private static BlockPos top(){return position("sprout_top");}
    private static void proveHarvest(ServerPlayer p) {
        var level=p.serverLevel();var state=level.getBlockState(position("sprout_root"));var tide=TideManager.getState(p.server);
        require(p.getInventory().countItem(MineralEcology.LUMINOUS_BUD.get())==1&&p.getInventory().items.get(3).getDamageValue()==1,"Ordinary shears top-click did not give exactly one bud and durability");
        require(state.getValue(TideSproutBlock.HARVESTED)&&level.getBlockState(top()).getValue(TideSproutBlock.HARVESTED)&&SproutHarvestData.get(level).locked(position("sprout_root"),tide.totalCycles()),"Native upper harvest did not persist a same-cycle root lock");
        require(level.getBlockState(top()).getLightEmission(level,top())==0,"Cut upper blossom still emits its original full light");
        data.addProperty("ordinary_client_shears_open_original_top",true);data.addProperty("luminous_bud_count",1);data.addProperty("shears_durability_used",1);data.addProperty("harvest_cycle",tide.totalCycles());data.addProperty("saved_harvest_root_and_cycle_lock",true);data.addProperty("cut_upper_blossom_light_zero",true);
    }
    private static void reload(ServerPlayer p) {
        require(p!=null&&p.serverLevel().dimension().equals(IslandWorld.LIVING_WORLD)&&!p.isCreative(),"Cold restart did not restore the prepared Survival player");
        var tide=TideManager.getState(p.server);require(tide.phase()==TidePhase.SURGE&&tide.totalCycles()==data.get("harvest_cycle").getAsLong(),"Cold restart did not preserve the same SURGE/cycle");
        require(p.serverLevel().getBlockState(position("sprout_root")).getValue(TideSproutBlock.HARVESTED)&&SproutHarvestData.get(p.serverLevel()).locked(position("sprout_root"),tide.totalCycles()),"Cold restart lost the harvested marker/lock");
        require(p.serverLevel().getBlockState(top()).getValue(TideSproutBlock.HARVESTED)&&p.serverLevel().getBlockState(top()).getLightEmission(p.serverLevel(),top())==0,"Cold restart restored an uncut glowing flower tip");
        require(p.getInventory().countItem(MineralEcology.LUMINOUS_BUD.get())==1&&p.getInventory().items.get(3).getDamageValue()==1,"Cold restart lost bud/tool durability");
        data.addProperty("reload_pid",ProcessHandle.current().pid());data.addProperty("cold_restart_same_surge_lock_preserved",true);
    }

    private static void use(Minecraft mc,BlockPos support,Direction face){look(mc,Vec3.atCenterOf(support));var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5),face,support,false));require(result.consumesAction(),"Ordinary client item interaction failed at "+support);}
    private static void look(Minecraft mc,Vec3 target){var delta=target.subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));}
    private static void camera(ServerPlayer p,ServerLevel level,double x,double y,double z,Vec3 target){double dx=target.x-x,dz=target.z-z,dy=target.y-(y+p.getEyeHeight());p.teleportTo(level,x,y,z,Set.of(),(float)(Math.toDegrees(Math.atan2(dz,dx))-90),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));p.setDeltaMovement(0,0,0);p.resetFallDistance();p.getAbilities().flying=false;p.onUpdateAbilities();}
    private static void position(JsonObject o,String key,BlockPos p){o.addProperty(key+"_x",p.getX());o.addProperty(key+"_y",p.getY());o.addProperty(key+"_z",p.getZ());}
    private static BlockPos position(String key){return new BlockPos(data.get(key+"_x").getAsInt(),data.get(key+"_y").getAsInt(),data.get(key+"_z").getAsInt());}
    private static boolean done(){if(!work.isDone())return false;work.join();return true;}
    private static void stop(Minecraft mc){mc.options.keyUp.setDown(false);mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);if(mc.gameMode!=null)mc.gameMode.stopDestroyBlock();}
    private static void lower(Minecraft mc){stop(mc);mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.options.broadcastOptions();}
    private static java.nio.file.Path world(Minecraft mc){return mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);}
    private static java.nio.file.Path report(Minecraft mc,String mode){return mc.gameDirectory.toPath().resolve("mining-"+mode+"-validation.json");}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),msg->System.out.println("MINING_SCREENSHOT "+name));}
    private static void require(boolean yes,String reason){if(!yes)throw new IllegalStateException(reason);}
    private static void finish(Minecraft mc,boolean passed,String reason) {
        if(finished)return;if(shutdownRequested){if(!passed){pendingPassed=false;pendingReason=reason;write(mc,false,true);}return;}
        shutdownRequested=true;pendingPassed=passed;pendingReason=reason;lower(mc);settling=new NativeChunkSettler.Session("before_mining_terminal_shutdown",160);
        if(!passed)write(mc,false,true);
    }
    private static void shutdown(Minecraft mc) {
        if(mc.getSingleplayerServer()==null){finished=true;write(mc,pendingPassed,false);mc.stop();return;}
        var session=settling;
        if(session.failed()&&!data.has("shutdown_quiescence_failed")){pendingPassed=false;pendingReason=session.failure();data.addProperty("shutdown_quiescence_failed",true);data.add("settle_terminal",session.report());write(mc,false,true);}
        if(!session.ready())return;
        if(!finalCheck){finalCheck=true;var uuid=mc.player.getUUID();work=mc.getSingleplayerServer().submit(()->{data.add("settle_terminal",session.report());if(pendingPassed){var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);require(TideManager.getState(p.server).phase()==TidePhase.SURGE&&SproutHarvestData.get(p.serverLevel()).locked(position("sprout_root"),data.get("harvest_cycle").getAsLong()),"Terminal saving lost the same-SURGE harvest lock");}return true;});return;}
        if(!work.isDone())return;try{work.join();}catch(Throwable error){pendingPassed=false;pendingReason=error.toString();}
        data.addProperty("clean_generation_before_mc_stop",true);finished=true;settling=null;write(mc,pendingPassed,false);mc.stop();
    }
    private static void write(Minecraft mc,boolean passed,boolean pending){data.addProperty("passed",passed);data.addProperty("mode",MODE);data.addProperty("stage",stage);data.addProperty("reason",pendingReason);data.addProperty("shutdown_pending",pending);try{Files.writeString(report(mc,MODE),new GsonBuilder().setPrettyPrinting().create().toJson(data));}catch(Exception error){error.printStackTrace();}System.out.println("MINING_VALIDATION "+data);}
}
