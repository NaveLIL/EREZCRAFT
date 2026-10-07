package pro.erez.interstice.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.IslandWorld;

/** Save and reopen only this fixture, in separate game/server JVMs. Never selects an existing user world. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class PersistenceVisualSmoke {
    private static final String MODE=System.getProperty("interstice.persistenceSmoke","");
    private static final BlockPos MARKER=new BlockPos(5,50,5);
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static boolean started,finished;
    private static long began;
    private static int stage,ticks;
    private static Fixture fixture;
    private static CompletableFuture<?> work;
    private record Fixture(String world,String player,long creatorPid,String returnPoint,String seaHash) {}
    private static void require(boolean value,String message) {if(!value) throw new IllegalStateException(message);}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(MODE.isEmpty() || finished) return;
        Minecraft mc=Minecraft.getInstance();
        if(began==0) began=System.nanoTime();
        try {
            require(System.nanoTime()-began<900_000_000_000L,"Persistence check initialization/operation timed out");
            if(!started && mc.screen instanceof TitleScreen) {
                started=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);
                mc.options.framerateLimit().set(60);
                if(MODE.equals("create")) {
                    String world="persistence-check-"+UUID.randomUUID();
                    require(!Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(world)),"Fixture path already exists");
                    fixture=new Fixture(world,"",ProcessHandle.current().pid(),"","");
                    mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings("Disposable persistence fixture",GameType.SURVIVAL,
                            false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                            new WorldOptions(20261006,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
                } else {
                    require(MODE.equals("reload"),"Unknown persistence mode");
                    fixture=JSON.fromJson(Files.readString(mc.gameDirectory.toPath().resolve("persistence-fixture.json")),Fixture.class);
                    require(fixture.world.matches("persistence-check-[0-9a-f-]{36}"),"Not a disposable fixture name");
                    require(fixture.creatorPid!=ProcessHandle.current().pid(),"Reload must use another JVM");
                    require(Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(fixture.world).resolve("level.dat")),"Fixture was not saved");
                    mc.createWorldOpenFlows().openWorld(fixture.world,()->report(mc,false,"World reopen cancelled"));
                }
                return;
            }
            if(mc.player==null || mc.level==null || mc.getConnection()==null || mc.screen!=null) return;
            if(stage==0) {
                if(MODE.equals("create")) {
                    if(!mc.player.onGround()) return;
                    mc.getConnection().sendCommand("interstice explore tall");
                } else require(mc.player.getUUID().toString().equals(fixture.player),"Saved player identity changed");
                stage=1;ticks=0;return;
            }
            if(stage<=3 && !mc.level.dimension().equals(IslandWorld.TALL_WORLD)) return;
            ticks++;
            if(stage==1 && ticks>=100) {
                require(mc.level.getHeight()==256 && GeometryProfiles.get(mc.level).equals(GeometryProfile.TALL),"Tall client profile lost at login");
                require(Math.abs(SeaSurface.heightAt(GeometryProfiles.get(mc.level),7.375,9.625,true)
                        - (0x1.6a424fd30a82fp6+128)) < 1e-12, "Saved client surface changed");
                var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
                work=server.submit(()-> {
                    var player=server.getPlayerList().getPlayer(uuid);
                    validateServer(server,player);
                    ServerLevel world=server.getLevel(IslandWorld.TALL_WORLD);
                    if(MODE.equals("create")) {
                        world.setBlock(MARKER,Blocks.GOLD_BLOCK.defaultBlockState(),3);
                        // The test owns this newly created save and acknowledges its custom-world warning.
                        if(server.getWorldData() instanceof PrimaryLevelData data) data.withConfirmedWarning(true);
                        return new Fixture(fixture.world,uuid.toString(),ProcessHandle.current().pid(),
                                player.getPersistentData().getCompound("interstice:return").toString(),seaHash(world));
                    }
                    require(world.getBlockState(MARKER).is(Blocks.GOLD_BLOCK),"Changed block was regenerated instead of loaded");
                    require(fixture.returnPoint.equals(player.getPersistentData().getCompound("interstice:return").toString()),"Return state was lost across JVM restart");
                    require(fixture.seaHash.equals(seaHash(world)),"Saved ocean shape/blocks changed on reload");
                    // Generate a previously untouched chunk after reopening the same saved generator.
                    var fresh=world.getChunk(18,0);
                    require(fresh.getHeight()==256 && fresh.getBlockState(new BlockPos(288,255,0)).is(Blocks.BEDROCK),"New chunk has wrong saved bounds");
                    return fixture;
                });
                stage=2;ticks=0;
            } else if(stage==2 && work.isDone()) {
                fixture=(Fixture)work.join();
                capture(mc,MODE+"-tall-world.png");
                if(MODE.equals("create")) {
                    Files.writeString(mc.gameDirectory.toPath().resolve("persistence-fixture.json"),JSON.toJson(fixture));
                    report(mc,true,"fixture created; normal game shutdown saves it");
                } else {
                    require(mc.level.getBlockState(MARKER).is(Blocks.GOLD_BLOCK),"Client did not receive persisted marker");
                    mc.getConnection().sendCommand("interstice explore legacy");stage=4;ticks=0;
                }
            } else if(stage==4 && mc.level.dimension().equals(IslandWorld.WORLD) && ticks>=80) {
                require(GeometryProfiles.get(mc.level).equals(GeometryProfile.LEGACY) && mc.level.getHeight()==128,"Legacy world changed after reopen");
                mc.getConnection().sendCommand("interstice leave");stage=5;ticks=0;
            } else if(stage==5 && mc.level.dimension().equals(Level.OVERWORLD) && ticks>=80) {
                var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
                var point=net.minecraft.nbt.TagParser.parseTag(fixture.returnPoint);
                work=server.submit(()-> {
                    ServerPlayer player=server.getPlayerList().getPlayer(uuid);
                    require(player.level().dimension().location().toString().equals(point.getString("dimension")),"Returned to wrong external world");
                    require(Math.abs(player.getX()-point.getDouble("x"))<.1 && Math.abs(player.getY()-point.getDouble("y"))<.1
                            && Math.abs(player.getZ()-point.getDouble("z"))<.1,"Saved external destination changed");
                    require(!player.getPersistentData().contains("interstice:return"),"Consumed return state was not cleared");
                    return true;
                });stage=6;
            } else if(stage==6 && work.isDone()) {
                work.join();capture(mc,"reload-return-to-overworld.png");report(mc,true,"saved world reopened in a new JVM; profiles, blocks and return preserved");
            }
        } catch(Throwable error) {error.printStackTrace();report(mc,false,error.toString());}
    }
    private static void validateServer(MinecraftServer server,ServerPlayer player) {
        require(player!=null && player.level().dimension().equals(IslandWorld.TALL_WORLD),"Player was not restored in tall world");
        ServerLevel tall=server.getLevel(IslandWorld.TALL_WORLD),legacy=server.getLevel(IslandWorld.WORLD);
        require(tall.getChunkSource().getGenerator() instanceof IslandChunkGenerator generator && generator.geometry().equals(GeometryProfile.TALL)
                && tall.getHeight()==256,"Saved server generator profile changed");
        require(GeometryProfiles.get(legacy).equals(GeometryProfile.LEGACY) && legacy.getHeight()==128,"Legacy profile changed");
        require(player.getPersistentData().contains("interstice:return"),"Original expedition return missing");
    }
    private static String seaHash(ServerLevel world) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for(int x=3;x<=11;x++) for(int z=5;z<=13;z++) for(int y=211;y<=255;y++)
                digest.update(world.getBlockState(new BlockPos(x,y,z)).toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch(java.security.NoSuchAlgorithmException error) {throw new IllegalStateException(error);}
    }
    private static void capture(Minecraft mc,String name) {
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println("PERSISTENCE_VISUAL "+name));
    }
    private static void report(Minecraft mc,boolean passed,String reason) {
        finished=true;
        Map<String,Object> report=new LinkedHashMap<>();report.put("passed",passed);report.put("reason",reason);
        report.put("mode",MODE);report.put("pid",ProcessHandle.current().pid());report.put("fixture",fixture);
        report.put("duration_seconds",(System.nanoTime()-began)/1_000_000_000L);
        try {Files.writeString(mc.gameDirectory.toPath().resolve(MODE.equals("create")?"persistence-create.json":"persistence-validation.json"),JSON.toJson(report));}
        catch(Exception error) {throw new IllegalStateException(error);}
        System.out.println("PERSISTENCE_VALIDATION "+JSON.toJson(report));mc.stop();
    }
}
