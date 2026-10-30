package pro.erez.interstice.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.CRC32;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.*;
import pro.erez.interstice.worldgen.*;

/** Owned full-pack large-area still/real-flow albedo trial; not a Survival acceptance. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class LowerOceanNative {
    private static final boolean ACTIVE=System.getProperty("interstice.studioSmoke","").equals("lowerocean");
    private static final JsonObject REPORT=new JsonObject();private static final JsonArray SHOTS=new JsonArray();
    private static boolean started,finished,closing,released;private static int phase,ticks,waits,preparedTick;private static long deadline,animationBefore;
    private static CompletableFuture<?> job;private static ServerLevel level;private static LivingEntity camera;private static NativeChunkSettler.Session settling;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static void shot(Minecraft mc,String name){String file="lower-ocean-"+name+".png";Screenshot.grab(mc.gameDirectory,file,mc.getMainRenderTarget(),unused->{});SHOTS.add(file);REPORT.addProperty(name+"_observed_fps",mc.getFps());}
    private static void view(Minecraft mc,double x,double y,double z,float yaw,float pitch){
        if(camera==null)camera=new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE,mc.level);camera.setInvisible(true);camera.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,200000,0,false,false));require(camera.hasEffect(MobEffects.NIGHT_VISION),"Camera actually has night vision");
        double feet=y-camera.getEyeHeight();camera.setPos(x,feet,z);camera.xo=x;camera.yo=feet;camera.zo=z;camera.setYRot(yaw);camera.yRotO=yaw;camera.yHeadRot=camera.yHeadRotO=yaw;camera.yBodyRot=camera.yBodyRotO=yaw;camera.setXRot(pitch);camera.xRotO=pitch;mc.setCameraEntity(camera);
    }
    private static void write(Minecraft mc){try{Files.writeString(mc.gameDirectory.toPath().resolve("lower-ocean.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void finish(Minecraft mc,boolean passed,String reason){if(closing||finished)return;REPORT.addProperty("passed",passed);REPORT.addProperty("reason",reason);REPORT.addProperty("phase",phase);REPORT.addProperty("pid",ProcessHandle.current().pid());REPORT.addProperty("owner_profile_used",false);REPORT.addProperty("prepared_fixture_not_survival",true);REPORT.add("screenshots",SHOTS);REPORT.addProperty("sodium_quads_uv_only",LowerOceanUv.SODIUM_QUADS.sum());REPORT.addProperty("vanilla_quads_uv_only",LowerOceanUv.VANILLA_QUADS.sum());REPORT.addProperty("geometry_errors",LowerOceanUv.GEOMETRY_ERRORS.sum());write(mc);closing=true;settling=new NativeChunkSettler.Session("lower_ocean_shutdown",3200);}
    private static ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static void submit(Minecraft mc,Runnable action){job=mc.getSingleplayerServer().submit(()->{action.run();return true;});ticks=0;}
    private static void block(int x,int y,int z,net.minecraft.world.level.block.state.BlockState state){level.setBlock(new BlockPos(x,y,z),state,2);}
    private static void prepare(Minecraft mc){
        var server=mc.getSingleplayerServer();level=server.getLevel(IslandWorld.TENSION_WORLD);
        require(level.getChunkSource().getGenerator() instanceof IslandChunkGenerator g&&g.terrainRevision()==6,"Actual V6 required");
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);for(int cx=6;cx<=8;cx++)for(int cz=1;cz<=2;cz++)level.setChunkForced(cx,cz,true);REPORT.addProperty("test_forced_chunks",6);
        var stone=Blocks.SMOOTH_STONE.defaultBlockState();var air=Blocks.AIR.defaultBlockState();var heavy=Interstice.HEAVY_BLOCK.get().defaultBlockState();
        for(int x=-1;x<=96;x++)for(int z=-1;z<=96;z++)for(int y=33;y<=58;y++)block(x,y,z,y==33||((x==-1||x==96||z==-1||z==96)&&y==34)?stone:y==34?heavy:air);
        for(int x=105;x<=142;x++)for(int z=17;z<=32;z++)for(int y=33;y<=57;y++)block(x,y,z,y==33||((x==105||x==142||z==17||z==32)&&y<=54)?stone:air);
        // Actual 6-block-wide source-fed trough, 18-block fall and receiving pool.
        for(int x=106;x<=141;x++)for(int z=18;z<=31;z++)block(x,34,z,heavy);
        for(int x=107;x<=113;x++)for(int z=21;z<=28;z++)block(x,51,z,stone);
        for(int x=107;x<=113;x++)for(int y=52;y<=53;y++){block(x,y,21,stone);block(x,y,28,stone);}
        for(int z=21;z<=28;z++)for(int y=52;y<=53;y++)block(107,y,z,stone);
        for(int z=22;z<=27;z++)level.setBlock(new BlockPos(108,52,z),heavy,3);
        var p=player(mc);p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();p.setInvisible(true);p.setNoGravity(true);p.getAbilities().flying=true;p.onUpdateAbilities();p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,200000,0,false,false));p.teleportTo(level,48,42,95,Set.of(),180,10);p.hasChangedDimension();preparedTick=server.getTickCount();
        REPORT.addProperty("still_pool_blocks",96*96);REPORT.addProperty("pool_width_blocks",96);REPORT.addProperty("pool_length_blocks",96);REPORT.addProperty("source_y",34);REPORT.addProperty("cascade_source_y",52);
    }
    private static TextureAtlasSprite sprite(Minecraft mc){return mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"block/heavy_oc01"));}
    private static class Counter implements VertexConsumer {
        int vertices;final List<float[]> data=new ArrayList<>();float[] current;
        public VertexConsumer addVertex(float x,float y,float z){vertices++;current=new float[]{x,y,z,0,0,0,0,0,0,0,0,0,0,0};data.add(current);return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){current[3]=r;current[4]=g;current[5]=b;current[6]=a;return this;}
        public VertexConsumer setUv(float u,float v){current[7]=u;current[8]=v;return this;}
        public VertexConsumer setUv1(int u,int v){current[9]=u;current[10]=v;return this;}public VertexConsumer setUv2(int u,int v){current[11]=u;current[12]=v;return this;}
        public VertexConsumer setNormal(float x,float y,float z){require(Float.isFinite(x+y+z),"Finite normal");return this;}
    }
    private static void inspect(Minecraft mc,String label){
        var s=sprite(mc);require(s.contents().width()==1280&&s.contents().height()==1280,"Full original-pixel repeated frame");require(s.contents().getUniqueFrames().count()==16,"16 authored animation keyframes");
        var image=s.contents().getOriginalImage();require(image.getWidth()==1280&&image.getHeight()==20480,"Animation source dimensions");
        int samples=0;for(int f=0;f<16;f++)for(int x=3;x<640;x+=97)for(int y=5;y<640;y+=89){int a=image.getPixelRGBA(x,y+f*1280);require(a==image.getPixelRGBA(x+640,y+f*1280)&&a==image.getPixelRGBA(x,y+640+f*1280)&&a==image.getPixelRGBA(x+640,y+640+f*1280),"Identical unedited repeated pixels");samples++;}
        int edges=0;for(var profile:List.of(GeometryProfile.LEGACY,GeometryProfile.TALL))for(int x:new int[]{-65,-17,-1,0,15,16,63,64,127}){
            float h=8f/9;var a=LowerOceanUv.coordinates(new BlockPos(x,34,16),new float[]{0,0,1,1},new float[]{h,h,h,h},new float[]{0,1,1,0},profile,s);
            var b=LowerOceanUv.coordinates(new BlockPos(x+1,34,16),new float[]{0,0,1,1},new float[]{h,h,h,h},new float[]{0,1,1,0},profile,s);
            double da=(a[4]-b[2])/(s.getU1()-s.getU0())*1280,db=(a[6]-b[0])/(s.getU1()-s.getU0())*1280;
            require(Math.abs(da/640-Math.rint(da/640))<.0001&&Math.abs(db/640-Math.rint(db/640))<.0001,"Shared edge has identical source pixel phase, including wrap");require(Math.abs(a[5]-b[3])<.00001&&Math.abs(a[7]-b[1])<.00001,"Shared edge V");edges++;
        }
        Counter out=new Counter();var c=new LowerOceanUv.Consumer(out,new BlockPos(15,34,15),GeometryProfile.TALL,s);
        for(int i=0;i<4;i++)c.addVertex(15+(i>1?1:0),2.75f,15+(i==1||i==2?1:0)).setColor(11,22,33,244).setUv(.2f,.4f).setUv1(7,8).setUv2(16,32).setNormal(0,1,0);c.finish();require(out.vertices==4,"Vanilla quad emitted");
        for(int i=0;i<4;i++){var p=out.data.get(i);require(p[0]==15+(i>1?1:0)&&p[1]==2.75f&&p[2]==15+(i==1||i==2?1:0)&&p[3]==11&&p[4]==22&&p[5]==33&&p[6]==244&&p[9]==7&&p[10]==8&&p[11]==16&&p[12]==32,"Vanilla position/color/light/overlay retained");}
        var actual=new Counter();BlockPos pos=new BlockPos(48,34,48);mc.getBlockRenderer().renderLiquid(pos,mc.level,actual,mc.level.getBlockState(pos),mc.level.getFluidState(pos));require(actual.vertices>=4,"Actual vanilla fluid tessellator traversed");
        require(LowerOceanUv.SODIUM_QUADS.sum()>0&&LowerOceanUv.GEOMETRY_ERRORS.sum()==0,"Actual Sodium UV-only renderer");
        var row=new JsonObject();row.addProperty("source_pixel_checks",samples);row.addProperty("shared_edges_legacy_tall",edges);row.addProperty("actual_vanilla_vertices",actual.vertices);row.addProperty("sodium_quads",LowerOceanUv.SODIUM_QUADS.sum());row.addProperty("geometry_errors",LowerOceanUv.GEOMETRY_ERRORS.sum());REPORT.add(label,row);
    }
    private static long gpu(Minecraft mc){var s=sprite(mc);var atlas=mc.getTextureManager().getTexture(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);var buffer=MemoryUtil.memAlloc(64*64*4);int previous=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),fbo=GL30.glGenFramebuffers();try{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,atlas.getId(),0);require(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"Atlas readback framebuffer complete");GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadPixels(s.getX()+550,s.getY()+550,64,64,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buffer);require(GL11.glGetError()==GL11.GL_NO_ERROR,"Atlas readback");var crc=new CRC32();for(int i=0;i<buffer.capacity();i++)crc.update(buffer.get(i)&255);return crc.getValue();}finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,previous);GL30.glDeleteFramebuffers(fbo);MemoryUtil.memFree(buffer);}}
    private static void flow(){int flowing=0,falling=0,minY=100,maxY=-100;for(int x=106;x<=141;x++)for(int z=18;z<=31;z++)for(int y=35;y<=53;y++){var f=level.getFluidState(new BlockPos(x,y,z));if(f.getFluidType()!=Interstice.HEAVY_TYPE.get()||f.isSource())continue;flowing++;if(f.getValue(FlowingFluid.FALLING)){falling++;minY=Math.min(minY,y);maxY=Math.max(maxY,y);}}require(flowing>50&&falling>50&&maxY-minY>=14,"Natural flowing and falling toxin reached basin: "+flowing+"/"+falling+"/"+minY+".."+maxY);REPORT.addProperty("actual_flowing_blocks",flowing);REPORT.addProperty("actual_falling_blocks",falling);REPORT.addProperty("actual_falling_min_y",minY);REPORT.addProperty("actual_falling_max_y",maxY);}
    private static void shaders(boolean enabled)throws Exception{var iris=Class.forName("net.irisshaders.iris.Iris");if(enabled){var cfg=iris.getMethod("getIrisConfig").invoke(null);cfg.getClass().getMethod("setShaderPackName",String.class).invoke(cfg,"ComplementaryReimagined_r5.9.3.zip");}iris.getMethod("toggleShaders",Minecraft.class,boolean.class).invoke(null,Minecraft.getInstance(),enabled);}
    private static void inspectShaders()throws Exception{var iris=Class.forName("net.irisshaders.iris.Iris");require(!(boolean)iris.getMethod("isFallback").invoke(null),"No Iris fallback");require(((Optional<?>)iris.getMethod("getStoredError").invoke(null)).isEmpty(),"No shader compilation error");require((boolean)iris.getMethod("isPackInUseQuick").invoke(null),"Real shader pack in use");require(V6ShaderBridge.materialsActive(),"Real V6 shader materials active");var nv=Class.forName("net.irisshaders.iris.uniforms.CommonUniforms").getDeclaredMethod("getNightVision");nv.setAccessible(true);float strength=(float)nv.invoke(null);require(strength>.99f,"Actual Iris camera night vision uniform: "+strength);REPORT.addProperty("iris_night_vision_uniform",strength);REPORT.addProperty("complementary_original_pack_active",true);REPORT.addProperty("shader_fallback",false);}
    @SubscribeEvent public static void server(ServerTickEvent.Post event){if(!ACTIVE||finished)return;if(closing){if(!released&&level!=null){for(int cx=6;cx<=8;cx++)for(int cz=1;cz<=2;cz++)level.setChunkForced(cx,cz,false);REPORT.addProperty("test_forced_chunks_released",true);released=true;}settling.tick(event.getServer());if(settling.ready()||settling.failed()){if(settling.failed()){REPORT.addProperty("passed",false);REPORT.addProperty("reason",settling.failure());}REPORT.add("settled",settling.report());finished=true;var mc=Minecraft.getInstance();write(mc);mc.stop();}}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ACTIVE||finished)return;var mc=Minecraft.getInstance();try{if(closing)return;
            if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                require(mc.gameDirectory.toPath().toRealPath().toString().replace('\\','/').contains("/.verification/"),"Owned profile required");for(var id:List.of("distanthorizons","create","sable","sodium","iris"))require(ModList.get().isLoaded(id),"Full pack required: "+id);require(!Files.exists(mc.gameDirectory.toPath().resolve("saves/lower-ocean-owned")),"Fresh world required");started=true;deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(18);
                REPORT.addProperty("distant_horizons_loaded",true);REPORT.addProperty("mod_count",ModList.get().getMods().size());REPORT.addProperty("version",ModList.get().getModContainerById(Interstice.ID).orElseThrow().getModInfo().getVersion().toString());mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(6);mc.options.simulationDistance().set(6);mc.options.fov().set(70);mc.options.bobView().set(false);
                var rules=new GameRules();rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc.createWorldOpenFlows().createFreshLevel("lower-ocean-owned",new LevelSettings("Owned OC01 still and flow",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(20261028L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
            }
            if(!started)return;require(System.nanoTime()<deadline,"Deadline phase "+phase);if(mc.getOverlay()!=null||SmokeWorldPrompts.advance(mc)||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;mc.getToasts().clear();if(job!=null&&!job.isDone())return;if(job!=null)job.join();if(++ticks<60)return;ticks=0;
            REPORT.addProperty("live_phase",phase);write(mc);
            if(phase==0){shaders(false);submit(mc,()->prepare(mc));phase=1;}
            else if(phase==1){if(!mc.level.dimension().equals(IslandWorld.TENSION_WORLD))return;view(mc,48,43,91,180,9);mc.levelRenderer.allChanged();phase=2;}
            else if(phase==2){if(++waits<5||mc.getSingleplayerServer().getTickCount()-preparedTick<250)return;waits=0;inspect(mc,"initial");REPORT.addProperty("actual_dimension",mc.level.dimension().location().toString());shot(mc,"wide-still-96x96");animationBefore=gpu(mc);phase=3;}
            else if(phase==3){long after=gpu(mc);require(after!=animationBefore,"Actual GPU atlas animation advances");REPORT.addProperty("gpu_animation_crc_before",animationBefore);REPORT.addProperty("gpu_animation_crc_after",after);shot(mc,"wide-still-animation");view(mc,32,55,32,0,90);phase=4;}
            else if(phase==4){shot(mc,"chunk-edge-detail");submit(mc,()->{player(mc).teleportTo(level,136,44,32,Set.of(),90,0);});phase=5;}
            else if(phase==5){view(mc,130,43,30,125,0);mc.levelRenderer.allChanged();phase=6;}
            else if(phase==6){if(++waits<3)return;waits=0;submit(mc,LowerOceanNative::flow);phase=14;}
            else if(phase==14){shot(mc,"cascade");view(mc,116,56,30,180,28);phase=7;}
            else if(phase==7){shot(mc,"flowing-channel");job=mc.reloadResourcePacks();phase=8;}
            else if(phase==8){inspect(mc,"after_reload");shot(mc,"after-resource-reload");REPORT.addProperty("resource_reload_checked",true);shaders(true);phase=9;}
            else if(phase==9){if(++waits<3)return;waits=0;inspectShaders();shot(mc,"shader-flowing-channel");view(mc,130,43,30,125,0);phase=10;}
            else if(phase==10){inspectShaders();shot(mc,"shader-cascade");submit(mc,()->player(mc).teleportTo(level,48,42,76,Set.of(),180,0));phase=11;}
            else if(phase==11){view(mc,48,43,91,180,9);phase=12;}
            else if(phase==12){if(++waits<2)return;waits=0;inspectShaders();shot(mc,"shader-wide-still");shaders(false);camera.removeEffect(MobEffects.NIGHT_VISION);submit(mc,()->player(mc).removeEffect(MobEffects.NIGHT_VISION));phase=13;}
            else if(phase==13){if(++waits<2)return;shot(mc,"darkness-no-night-vision");require(LowerOceanUv.GEOMETRY_ERRORS.sum()==0,"No geometry/light/normal mutation");finish(mc,true,"");}
        }catch(Throwable e){e.printStackTrace();if(mc.level!=null)shot(mc,"failure");finish(mc,false,e.toString());}
    }
}
