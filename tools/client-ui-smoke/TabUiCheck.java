package pro.erez.launcher.ocean;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.*;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.clientui.ClientUi;

/** Artificial player-list packets in a new, guarded flat world; no real accounts or remote login. */
@Mod("erez_tab_ui_check")
@EventBusSubscriber(modid="erez_tab_ui_check",value=Dist.CLIENT)
public final class TabUiCheck {
    private static final JsonObject report = new JsonObject();
    private static final JsonArray frames = new JsonArray();
    private static final List<UUID> fakeIds = new ArrayList<>();
    private static int stage, ticks;
    private static boolean started, finished, closing;
    private static long deadline;
    private static CompletableFuture<?> work;
    private static volatile NativeChunkSettler.Session settling;
    private static CompletableFuture<net.minecraft.client.resources.PlayerSkin> ownerSkin;
    private static GameProfile owner;
    private static volatile boolean captured;
    private static boolean scrollChecked;
    @SubscribeEvent public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        var current=settling; if(current!=null)current.tick(event.getServer());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(finished)return;
        var mc=Minecraft.getInstance();
        try {
            if(!started) {
                if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
                var path=mc.gameDirectory.getCanonicalPath().replace('\\','/');
                require(path.contains("/.local/verification/tab-ui-"),"Refuse a user profile");
                require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/tab-ui-owned")),"Never reopen a prior world");
                require(Files.isRegularFile(mc.gameDirectory.toPath().resolve("tab-ui-fixture.json")),"Missing fixture guard");
                require(ModList.get().isLoaded("distanthorizons")&&ModList.get().isLoaded(ClientUi.ID),"Full pack or UI missing");
                require(ModList.get().getMods().size()>=90,"Incomplete pack");
                var container=ModList.get().getModContainerById(ClientUi.ID).orElseThrow();
                var factory=IConfigScreenFactory.getForMod(container.getModInfo()).orElseThrow();
                require(factory.createScreen(container,mc.screen)!=null,"Missing native configuration screen");
                report.addProperty("native_config_screen_registered",true);
                report.addProperty("distant_horizons_loaded",true);
                report.addProperty("user_credentials_read",false);
                report.addProperty("owner_worlds_used",false);
                report.addProperty("client_ui_version",ModList.get().getModContainerById(ClientUi.ID).orElseThrow().getModInfo().getVersion().toString());
                report.addProperty("pid",ProcessHandle.current().pid());
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(2);
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                com.seibel.distanthorizons.api.DhApi.Delayed.configs.worldGenerator().enableDistantWorldGeneration().setValue(false);
                var publicProfile=JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("public-skin-profile.json"))).getAsJsonObject();
                String id=publicProfile.get("id").getAsString();
                owner=new GameProfile(UUID.fromString(id.replaceFirst("(........)(....)(....)(....)(............)","$1-$2-$3-$4-$5")),publicProfile.get("name").getAsString());
                for(var item:publicProfile.getAsJsonArray("properties")){
                    var p=item.getAsJsonObject(); owner.getProperties().put(p.get("name").getAsString(),new Property(p.get("name").getAsString(),p.get("value").getAsString(),p.get("signature").getAsString()));
                }
                ownerSkin=mc.getSkinManager().getOrLoad(owner);
                mc.getWindow().setWindowed(1366,768);mc.options.guiScale().set(2);mc.resizeDisplay();
                deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(7);started=true;
                var rules=new GameRules();rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel("tab-ui-owned",new LevelSettings("Owned Tab UI check",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20261021L,false,false),registries->registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
                return;
            }
            require(System.nanoTime()<deadline,"Timeout at stage "+stage);
            if(closing){shutdown(mc);return;}
            if(prompt(mc)||mc.player==null||mc.level==null||mc.screen!=null||mc.getOverlay()!=null)return;
            if(work!=null){if(!work.isDone())return;work.join();work=null;}
            var server=mc.getSingleplayerServer();require(server!=null,"Own integrated server missing");
            if(stage==0){
                require(mc.level.dimension().equals(Level.OVERWORLD),"Wrong actual dimension");
                report.addProperty("actual_dimension",mc.level.dimension().location().toString());
                if(!ownerSkin.isDone())return;
                var skin=ownerSkin.join();require(skin!=null&&skin.secure(),"EREZ ID public skin did not pass native verification");
                report.addProperty("signed_public_erez_skin_verified",true);
                report.addProperty("skin_texture",skin.texture().toString());
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(mc.getUser().getProfileId());p.teleportTo(0.5,-59,0.5);server.overworld().setDayTime(6000);});
                setPlayers(mc,4);mc.options.keyPlayerList.setDown(true);mc.player.getInventory().selected=8;stage=1;ticks=0;return;
            }
            mc.gui.getChat().clearMessages(true);mc.getToasts().clear();
            if(++ticks<35)return;
            if(stage==1){shot(mc,"tab-compact-four.png");stage=2;ticks=0;return;}
            if(!captured)return;
            if(stage==2){setPlayers(mc,10);stage=3;ticks=0;return;}
            if(stage==3){shot(mc,"tab-compact-ten.png");stage=4;ticks=0;return;}
            if(stage==4){mc.getWindow().setWindowed(854,480);mc.options.guiScale().set(3);mc.resizeDisplay();setPlayers(mc,80);stage=5;ticks=0;return;}
            if(stage==5){shot(mc,"tab-small-eighty.png");stage=6;ticks=0;return;}
            if(stage==6&&!scrollChecked){
                var scroll=new net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent(0,-1,false,false,false,0,0);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(scroll);require(scroll.isCanceled(),"Long list did not consume native mouse-scroll event");
                scrollChecked=true;report.addProperty("native_scroll_event_consumed",true);ticks=0;return;
            }
            if(stage==6){shot(mc,"tab-small-scroll.png");stage=11;ticks=0;return;}
            if(stage==11){mc.options.keyPlayerList.setDown(false);stage=12;ticks=0;return;}
            if(stage==12){mc.getWindow().setWindowed(1366,768);mc.options.guiScale().set(2);mc.resizeDisplay();setPlayers(mc,4);ClientUi.COMPACT_TAB.set(false);mc.options.keyPlayerList.setDown(true);stage=7;ticks=0;return;}
            if(stage==7){shot(mc,"tab-disabled-vanilla.png");stage=8;ticks=0;return;}
            if(stage==8){
                ClientUi.COMPACT_TAB.set(true);
                var board=mc.level.getScoreboard();var objective=board.addObjective("tab-check",ObjectiveCriteria.DUMMY,Component.literal("Test"),ObjectiveCriteria.RenderType.INTEGER,false,null);
                board.setDisplayObjective(DisplaySlot.LIST,objective);report.addProperty("scoreboard_fallback",true);stage=9;ticks=0;return;
            }
            if(stage==9){shot(mc,"tab-scoreboard-vanilla.png");stage=10;ticks=0;return;}
            if(stage==10){
                mc.level.getScoreboard().setDisplayObjective(DisplaySlot.LIST,null);mc.options.keyPlayerList.setDown(false);
                settling=new NativeChunkSettler.Session("tab-ui-shutdown",3000);closing=true;report.add("screenshots",frames);return;
            }
        }catch(Throwable error){report.addProperty("passed",false);report.addProperty("error",error.toString());error.printStackTrace();write(mc);finished=true;mc.stop();}
    }
    private static void setPlayers(Minecraft mc,int count)throws Exception{
        var connection=mc.getConnection();connection.handlePlayerInfoRemove(new ClientboundPlayerInfoRemovePacket(List.copyOf(fakeIds)));fakeIds.clear();
        var entries=new ArrayList<ClientboundPlayerInfoUpdatePacket.Entry>();
        for(int i=0;i<count-1;i++){
            GameProfile profile=i==0?owner:new GameProfile(UUID.nameUUIDFromBytes(("erez-tab-fixture-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)),i==1?"LongName16chars_":i==2?"Arcanist":"Builder_"+i);
            fakeIds.add(profile.getId());entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(profile.getId(),profile,true,i==1?1240:42+i*7,GameType.SURVIVAL,null,null));
        }
        var actions=EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE);
        var packet=new ClientboundPlayerInfoUpdatePacket(actions,List.of());
        var field=Arrays.stream(packet.getClass().getDeclaredFields()).filter(f->f.getType()==List.class).findFirst().orElseThrow();field.setAccessible(true);field.set(packet,entries);
        connection.handlePlayerInfoUpdate(packet);
        require(connection.getListedOnlinePlayers().size()==count,"Native listed player count mismatch");
    }
    private static void shot(Minecraft mc,String file){
        captured=false;Screenshot.grab(mc.gameDirectory,file,mc.getMainRenderTarget(),message->{captured=true;});
        var frame=new JsonObject();frame.addProperty("file",file);frame.addProperty("players",mc.getConnection().getListedOnlinePlayers().size());
        frame.addProperty("gui_width",mc.getWindow().getGuiScaledWidth());frame.addProperty("gui_height",mc.getWindow().getGuiScaledHeight());
        frames.add(frame);System.out.println("TAB_UI_NATIVE_FRAME "+file);
    }
    private static void shutdown(Minecraft mc){
        require(!settling.failed(),"Chunk settle failed");if(!settling.ready())return;
        var server=mc.getSingleplayerServer();mc.level.disconnect();mc.disconnect(new TitleScreen());
        require(server.isStopped(),"Own server still running");report.addProperty("own_integrated_server_stopped",true);
        report.addProperty("passed",true);write(mc);finished=true;mc.stop();
    }
    private static boolean prompt(Minecraft mc){
        if(!(mc.screen instanceof BackupConfirmScreen)||!(mc.screen.getTitle().getContents() instanceof TranslatableContents title)||!title.getKey().equals("selectWorld.backupQuestion.experimental"))return false;
        for(var child:mc.screen.children())if(child instanceof Button button&&button.getMessage().getContents() instanceof TranslatableContents text&&text.getKey().equals("selectWorld.backupJoinSkipButton")){button.onPress();return true;}return false;
    }
    private static void write(Minecraft mc){try{Files.writeString(mc.gameDirectory.toPath().resolve("tab-ui-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void require(boolean condition,String reason){if(!condition)throw new IllegalStateException(reason);}
}
