package pro.erez.interstice.client;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.*;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pro.erez.interstice.mixin.*;
import pro.erez.interstice.vehicle.*;

/** One nearby vehicle, one bounded panoramic render, three coherent monitor sectors.
 * The additional pass never changes a player's position or saved graphics settings.
 * Render-state isolation was informed by SecurityCraft's MIT FrameFeedHandler;
 * unlike its remote cameras these sensors never request or force extra chunks.
 */
public final class EitanCameras {
    public static boolean CAPTURING;
    static boolean benchmarkPaused;
    public static Quaternionf orientation=new Quaternionf();
    public static long captures,totalCpuNanos,maxCpuNanos;
    public static String failure="";
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("interstice","eitan_camera");
    private static final ResourceLocation BLACK=ResourceLocation.fromNamespaceAndPath("interstice","textures/block/eitan/black.png");
    private static final com.google.gson.JsonArray MONITORS=EitanData.metadata("monitors");
    private static EitanController active,renderedController;private static boolean renderedRear;private static long touched,lastCapture,lastCost;private static boolean frameReady,registered,rear;
    private static TextureTarget target;private static Object vanillaPipeline;public static boolean irisSettingsPreserved;
    public static boolean chassisOnly(){return System.getProperty("interstice.eitanPhase","").equals("chassis");}
    public static void touch(EitanController c){if(CAPTURING)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.player.distanceToSqr(c.world(0,1,0))>144)return;var seated=EitanClient.vehicle();if(seated!=null&&seated!=c)return;if(active!=null&&active!=c&&System.nanoTime()-touched<300_000_000L&&mc.player.distanceToSqr(active.world(0,1,0))<mc.player.distanceToSqr(c.world(0,1,0)))return;active=c;touched=System.nanoTime();}
    public static boolean matches(EitanController c){return renderedController==c&&frameReady&&target!=null;}
    public static boolean rearFrame(){return frameReady&&renderedRear;}
    public static boolean sensorBody(EitanController c){return CAPTURING&&active==c;}
    public static RenderTarget target(){return target;}
    public static Object vanillaPipeline(){if(vanillaPipeline==null)try{
        // The stock constructor changes global material maps, AO and chunk format.
        // Keep its renderer, but restore every setting before any auxiliary draw.
        var settingsType=Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
        var settings=settingsType.getField("INSTANCE").get(null);
        var fields=java.util.Arrays.stream(settingsType.getDeclaredFields()).filter(f->!java.lang.reflect.Modifier.isStatic(f.getModifiers())).toArray(java.lang.reflect.Field[]::new);
        var saved=new Object[fields.length];for(int i=0;i<fields.length;i++){fields[i].setAccessible(true);saved[i]=fields[i].get(settings);}
        try{vanillaPipeline=Class.forName("net.irisshaders.iris.pipeline.VanillaRenderingPipeline").getConstructor().newInstance();}
        finally{for(int i=0;i<fields.length;i++)fields[i].set(settings,saved[i]);}
        for(int i=0;i<fields.length;i++)if(!java.util.Objects.equals(fields[i].get(settings),saved[i]))throw new IllegalStateException("Iris setting changed: "+fields[i].getName());
        irisSettingsPreserved=true;
    }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot isolate Iris camera pipeline",e);}return vanillaPipeline;}
    public static void reset(){active=renderedController=null;benchmarkPaused=false;frameReady=false;rear=false;if(target!=null){target.destroyBuffers();target=null;}lastCapture=0;failure="";}
    public static void frame(RenderFrameEvent.Pre event){var mc=Minecraft.getInstance();if(CAPTURING||benchmarkPaused||chassisOnly()||mc.level==null||mc.player==null||active==null||active.isRemoved()||!active.powered()||System.nanoTime()-touched>700_000_000L||!failure.isEmpty())return;
        long now=System.nanoTime(),interval=Math.max(EitanClient.vehicle()==active?33_333_333L:66_666_667L,Math.min(400_000_000L,lastCost*8));if(now-lastCapture<interval)return;lastCapture=now;
        if(target==null){target=new TextureTarget(1536,384,true,Minecraft.ON_OSX);target.setClearColor(0,0,0,1);target.setFilterMode(9729);frameReady=false;}
        if(!registered){mc.getTextureManager().register(TEXTURE,new AbstractTexture(){@Override public void load(ResourceManager resources){}@Override public int getId(){return target==null?0:target.getColorTextureId();}@Override public void close(){}});registered=true;}
        var lr=(EitanLevelRendererAccess)mc.levelRenderer;var sections=lr.interstice$sections().clone();var translucent=lr.interstice$translucent();var items=lr.interstice$items();var weather=lr.interstice$weather();var particles=lr.interstice$particles();var clouds=lr.interstice$clouds();var transparency=lr.interstice$transparency();
        var access=(EitanMinecraftAccess)mc;var renderer=(EitanGameRendererAccess)mc.gameRenderer;var camera=(EitanCameraAccess)mc.gameRenderer.getMainCamera();
        var oldTarget=mc.getMainRenderTarget();var oldEntity=mc.getCameraEntity();var oldType=mc.options.getCameraType();int oldW=mc.getWindow().getWidth(),oldH=mc.getWindow().getHeight();float eye=camera.interstice$getEye(),oldEye=camera.interstice$getOldEye();boolean hand=renderer.interstice$getHand(),outline=renderer.interstice$getOutline(),panoramic=renderer.interstice$getPanoramic();
        var marker=new Marker(EntityType.MARKER,mc.level);var body=active.body();if(body==null)return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(true);var pose=body instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel client?client.renderPose(partial):body.logicalPose();var o=active.origin();var p=pose.transformPosition(new org.joml.Vector3d(o.getX(),o.getY()+(rear?2.15:1.95),o.getZ()+(rear?4.08:-4.08)));marker.setPos(p.x,p.y,p.z);marker.xOld=marker.xo=p.x;marker.yOld=marker.yo=p.y;marker.zOld=marker.zo=p.z;
        orientation.set(pose.orientation());if(rear)orientation.rotateY((float)Math.PI);var forward=new Vector3f(0,0,-1).rotate(orientation);marker.setYRot((float)Math.toDegrees(Math.atan2(-forward.x,forward.z)));marker.yRotO=marker.getYRot();marker.setXRot((float)-Math.toDegrees(Math.asin(forward.y)));marker.xRotO=marker.getXRot();
        long begin=System.nanoTime();try{mc.renderBuffers().bufferSource().endBatch();CAPTURING=true;lr.interstice$translucent(null);lr.interstice$items(null);lr.interstice$weather(null);lr.interstice$particles(null);lr.interstice$clouds(null);lr.interstice$transparency(null);access.interstice$setTarget(target);access.interstice$setCamera(marker);mc.getWindow().setWidth(target.width);mc.getWindow().setHeight(target.height);mc.options.setCameraType(CameraType.FIRST_PERSON);camera.interstice$setEye(0);camera.interstice$setOldEye(0);mc.gameRenderer.setRenderHand(false);mc.gameRenderer.setRenderBlockOutline(false);mc.gameRenderer.setPanoramicMode(true);target.clear(Minecraft.ON_OSX);target.bindWrite(true);mc.gameRenderer.renderLevel(event.getPartialTick());mc.renderBuffers().bufferSource().endBatch();opaqueSensor();frameReady=true;renderedController=active;renderedRear=rear;captures++;}
        catch(Throwable error){failure=error.toString();error.printStackTrace();frameReady=false;}
        finally{CAPTURING=false;lr.interstice$sections().clear();lr.interstice$sections().addAll(sections);lr.interstice$translucent(translucent);lr.interstice$items(items);lr.interstice$weather(weather);lr.interstice$particles(particles);lr.interstice$clouds(clouds);lr.interstice$transparency(transparency);access.interstice$setCamera(oldEntity);access.interstice$setTarget(oldTarget);mc.getWindow().setWidth(oldW);mc.getWindow().setHeight(oldH);mc.options.setCameraType(oldType);camera.interstice$setEye(eye);camera.interstice$setOldEye(oldEye);mc.gameRenderer.setRenderHand(hand);mc.gameRenderer.setRenderBlockOutline(outline);mc.gameRenderer.setPanoramicMode(panoramic);oldTarget.bindWrite(true);mc.gameRenderer.getMainCamera().setup(mc.level,oldEntity==null?mc.player:oldEntity,!oldType.isFirstPerson(),oldType.isMirrored(),event.getPartialTick().getGameTimeDeltaPartialTick(true));marker.discard();lastCost=System.nanoTime()-begin;totalCpuNanos+=lastCost;maxCpuNanos=Math.max(maxCpuNanos,lastCost);}
    }
    private static void opaqueSensor(){
        // Video is an opaque image. Shader entity passes may discard its sky when
        // the offscreen renderer leaves alpha zero even though RGB is valid.
        target.bindWrite(false);
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
            var colour=stack.mallocFloat(4);var mask=stack.malloc(4);
            org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_COLOR_CLEAR_VALUE,colour);
            org.lwjgl.opengl.GL11.glGetBooleanv(org.lwjgl.opengl.GL11.GL_COLOR_WRITEMASK,mask);
            org.lwjgl.opengl.GL11.glColorMask(false,false,false,true);org.lwjgl.opengl.GL11.glClearColor(0,0,0,1);
            try{org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT);}
            finally{org.lwjgl.opengl.GL11.glClearColor(colour.get(0),colour.get(1),colour.get(2),colour.get(3));
                org.lwjgl.opengl.GL11.glColorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);}
        }
    }
    public static void displays(EitanController c,PoseStack stack,MultiBufferSource buffers){if(CAPTURING)return;touch(c);var mc=Minecraft.getInstance();for(var v:MONITORS){var m=v.getAsJsonObject();var a=EitanData.vector(m.getAsJsonArray("min"));var b=EitanData.vector(m.getAsJsonArray("max"));String name=m.get("name").getAsString();boolean video=name.startsWith("driver display")&&c.powered()&&matches(c)&&!renderedRear;int sector=video?Integer.parseInt(name.substring(name.length()-1)):0;var vertex=buffers.getBuffer(video?RenderType.entitySolid(TEXTURE):RenderType.entityCutoutNoCull(BLACK));float z=(float)b[2]+.0015f;
            if(video){for(int i=0;i<EitanCameraOptics.STRIPS;i++){var lo=EitanCameraOptics.SAMPLES[sector][i];var hi=EitanCameraOptics.SAMPLES[sector][i+1];float x0=(float)(a[0]+(b[0]-a[0])*i/EitanCameraOptics.STRIPS),x1=(float)(a[0]+(b[0]-a[0])*(i+1)/EitanCameraOptics.STRIPS);
                vertex(vertex,stack,x0,(float)a[1],z,lo.u(),lo.bottom());vertex(vertex,stack,x1,(float)a[1],z,hi.u(),hi.bottom());vertex(vertex,stack,x1,(float)b[1],z,hi.u(),hi.top());vertex(vertex,stack,x0,(float)b[1],z,lo.u(),lo.top());
            }}else{vertex(vertex,stack,(float)a[0],(float)a[1],z,0,0);vertex(vertex,stack,(float)b[0],(float)a[1],z,1,0);vertex(vertex,stack,(float)b[0],(float)b[1],z,1,1);vertex(vertex,stack,(float)a[0],(float)b[1],z,0,1);}
            if(!video){if(name.equals("driver instruments")&&c.powered()){instrument(c,a,b,z,stack,buffers);}else{stack.pushPose();stack.translate(a[0]+.006,b[1]-.015,z+.001);float scale=(float)(b[0]-a[0])/120;stack.scale(scale,-scale,scale);String[] rows=!c.powered()?new String[]{"EITAN E01",Component.translatable("interstice.eitan.power_off").getString()}:name.startsWith("driver display")?new String[]{Component.translatable("interstice.eitan.external_camera").getString(),Component.translatable("interstice.eitan.view_"+name.substring(name.length()-1)).getString(),Component.translatable(failure.isEmpty()?"interstice.eitan.camera_pending":"interstice.eitan.camera_unavailable").getString()}:new String[]{"EITAN E01",String.format(java.util.Locale.ROOT,"%.1f km/h",c.speed*3.6),Component.translatable("interstice.eitan.fuel").getString()+" "+c.fuelTicks/20+"s",Integer.bitCount(c.occupiedMask)+" / 12"};for(int i=0;i<rows.length;i++)mc.font.drawInBatch(rows[i],0,i*11,0xffbddad3,false,stack.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET,0,LightTexture.FULL_BRIGHT);stack.popPose();}}else{stack.pushPose();float scale=(float)(b[0]-a[0])/160;stack.translate(a[0],b[1]-.008,z+.001);stack.scale(scale,-scale,scale);String label=Component.translatable("interstice.eitan.view_"+sector).getString();mc.font.drawInBatch(label,(160-mc.font.width(label))/2f,0,0xffe3c889,false,stack.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET,0,LightTexture.FULL_BRIGHT);stack.popPose();}
        }}
    private static void instrument(EitanController c,double[] a,double[] b,float z,PoseStack stack,MultiBufferSource buffers){
        var font=Minecraft.getInstance().font;stack.pushPose();float scale=(float)(b[0]-a[0])/160;stack.translate(a[0]+.004,b[1]-.01,z+.001);stack.scale(scale,-scale,scale);
        text("EITAN E01",3,0,0xffbddad3,stack,buffers);
        stack.pushPose();stack.translate(3,13,0);stack.scale(2.7f,2.7f,1);text(String.format(java.util.Locale.ROOT,"%.1f",c.speed*3.6),0,0,0xffe3c889,stack,buffers);stack.popPose();
        String gear=c.brakeStatus().equals("parked")?"P":c.driveEffort>.05?"D":c.driveEffort<-.05?"R":"N";
        text(Component.translatable("interstice.eitan.kmh").getString()+"   [ "+gear+" ]",3,44,0xffbddad3,stack,buffers);
        text(Component.translatable("interstice.eitan.fuel_reading",c.fuelTicks/20).getString(),3,62,0xffbddad3,stack,buffers);
        text(Component.translatable("interstice.eitan."+c.brakeStatus()).getString(),3,80,c.brake?0xffe3c889:0xffa7c9b5,stack,buffers);stack.popPose();
    }
    private static void text(String value,float x,float y,int colour,PoseStack stack,MultiBufferSource buffers){Minecraft.getInstance().font.drawInBatch(value,x,y,colour,false,stack.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET,0,LightTexture.FULL_BRIGHT);}
    private static void vertex(VertexConsumer v,PoseStack stack,float x,float y,float z,float u,float t){v.addVertex(stack.last(),x,y,z).setColor(255,255,255,255).setUv(u,t).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(stack.last(),0,0,1);}
    public static void front(){rear=false;}
    public static void gui(GuiGraphics g,int x,int y,int width,int height,int monitor,EitanController c){rear=monitor==-2;touch(c);g.fill(x-1,y-1,x+width+1,y+height+1,0xff9b8255);g.fill(x,y,x+width,y+height,0xff050b0d);
        if(!c.powered()||!matches(c)||renderedRear!=rear){g.drawCenteredString(Minecraft.getInstance().font,Component.translatable(!c.powered()?"interstice.eitan.power_off":"interstice.eitan.camera_pending"),x+width/2,y+height/2,0xffbad1cf);return;}
        g.flush();RenderSystem.setShader(GameRenderer::getPositionTexShader);RenderSystem.setShaderTexture(0,TEXTURE);RenderSystem.disableBlend();var bb=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);var matrix=g.pose().last().pose();int first=monitor>=0&&monitor<3?monitor:0,count=monitor>=0&&monitor<3?1:3;
        for(int sector=first;sector<first+count;sector++){float panelWidth=width/(float)count,xx=x+(sector-first)*panelWidth;for(int i=0;i<EitanCameraOptics.STRIPS;i++){var lo=EitanCameraOptics.SAMPLES[sector][i];var hi=EitanCameraOptics.SAMPLES[sector][i+1];float x0=xx+panelWidth*i/EitanCameraOptics.STRIPS,x1=xx+panelWidth*(i+1)/EitanCameraOptics.STRIPS;
            bb.addVertex(matrix,x0,y+height,0).setUv(lo.u(),lo.bottom());bb.addVertex(matrix,x1,y+height,0).setUv(hi.u(),hi.bottom());bb.addVertex(matrix,x1,y,0).setUv(hi.u(),hi.top());bb.addVertex(matrix,x0,y,0).setUv(lo.u(),lo.top());
        }}BufferUploader.drawWithShader(bb.buildOrThrow());RenderSystem.disableBlend();
    }
    private EitanCameras(){}
}
