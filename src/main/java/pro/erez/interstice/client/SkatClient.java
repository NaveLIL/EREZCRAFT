package pro.erez.interstice.client;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import pro.erez.interstice.ship.*;

public final class SkatClient {
    private static SkatNetwork.Status state;private static long seen;
    private static float groundSpeed=Float.NaN;private static long speedSeen;
    public static float displayedSpeed(){var w=Minecraft.getInstance().level;return w!=null&&state!=null&&state.active()&&w.getGameTime()-speedSeen<=40?groundSpeed:Float.NaN;}
    public static void lightingPanel(SkatNetwork.LightingPanel p){var mc=Minecraft.getInstance();if(p.open())mc.setScreen(new SkatLightingScreen(p.helm(),p.lighting()));else if(mc.screen instanceof SkatLightingScreen s&&s.helm.equals(p.helm()))s.update(p.lighting());}
    public static void status(SkatNetwork.Status packet){if(state==null||!packet.active()||!packet.helm().equals(state.helm()))groundSpeed=Float.NaN;state=packet;var w=Minecraft.getInstance().level;seen=w==null?0:w.getGameTime();}
    public static void speed(SkatNetwork.Speed packet){var w=Minecraft.getInstance().level;if(w==null||state==null||!state.active()||!state.helm().equals(packet.helm())||!Float.isFinite(packet.metersPerSecond())||packet.metersPerSecond()<0||packet.metersPerSecond()>64)return;groundSpeed=packet.metersPerSecond();speedSeen=w.getGameTime();}
    public static void register(IEventBus bus){
        bus.addListener((RegisterMenuScreensEvent event)->event.register(SkatShips.CARGO_MENU.get(),SkatCargoScreen::new));
        bus.addListener(SkatModels::geometry);bus.addListener(SkatModels::additional);
        bus.addListener((EntityRenderersEvent.RegisterRenderers e)->{
            e.registerEntityRenderer(SkatShips.SEAT_ACTOR.get(),net.minecraft.client.renderer.entity.NoopRenderer::new);
            e.registerBlockEntityRenderer(SkatShips.PROPELLER_ENTITY.get(),Propeller::new);
            e.registerBlockEntityRenderer(SkatShips.HELM_ENTITY.get(),Bridge::new);
        });
        NeoForge.EVENT_BUS.addListener(SkatClient::tick);NeoForge.EVENT_BUS.addListener(SkatClient::hud);
        if(net.neoforged.fml.ModList.get().isLoaded("lambdynlights")||net.neoforged.fml.ModList.get().isLoaded("lambdynlights_runtime"))NeoForge.EVENT_BUS.addListener(SkatLightsClient::tick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e)->{state=null;groundSpeed=Float.NaN;});
        if(System.getProperty("interstice.studioSmoke","").equals("skat"))try{Class.forName("pro.erez.interstice.client.SkatNative").getMethod("register").invoke(null);}catch(ReflectiveOperationException e){throw new IllegalStateException("Skat dev harness absent",e);}
    }
    private static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();
        if(state==null||!state.active()||mc.player==null||mc.level==null)return;
        if(mc.level.getGameTime()-seen>40||!mc.player.isPassenger()){state=null;return;}
        if(mc.level.getGameTime()%4==0){int f=mc.screen==null?(mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0):0;
            int t=mc.screen==null?(mc.options.keyRight.isDown()?1:0)-(mc.options.keyLeft.isDown()?1:0):0;
            PacketDistributor.sendToServer(new SkatNetwork.Input(state.helm(),f,t));}
    }
    private static void hud(RenderGuiEvent.Post event){var mc=Minecraft.getInstance();if(state==null||!state.active()||mc.options.hideGui||mc.screen!=null)return;
        var g=event.getGuiGraphics();int x=10,y=g.guiHeight()-94;g.fill(x,y,x+222,y+70,0xe51c2a30);g.fill(x,y,x+222,y+1,0xff75c6c2);
        g.drawString(mc.font,net.minecraft.network.chat.Component.translatable("hud.interstice.skat.title"),x+8,y+7,0xffb8d9d6,false);
        String meters="—",kmh="—";if(Float.isFinite(groundSpeed)&&mc.level!=null&&mc.level.getGameTime()-speedSeen<=40){meters=String.format(java.util.Locale.ROOT,"%.1f",groundSpeed);kmh=String.format(java.util.Locale.ROOT,"%.1f",groundSpeed*3.6F);}
        g.drawString(mc.font,net.minecraft.network.chat.Component.translatable("hud.interstice.skat.speed",meters,kmh),x+8,y+21,0xffe0e4dd,false);
        g.drawString(mc.font,net.minecraft.network.chat.Component.translatable("hud.interstice.skat.controls"),x+8,y+35,0xffe0e4dd,false);
        g.drawString(mc.font,net.minecraft.network.chat.Component.translatable("hud.interstice.skat.fuel",state.leftFuel()/20,state.rightFuel()/20),x+8,y+49,0xffc8b58b,false);
    }
    private static void draw(String model,PoseStack stack,MultiBufferSource buffers,int light,int overlay){
        var mc=Minecraft.getInstance();var baked=mc.getModelManager().getModel(SkatModels.id(model));
        mc.getBlockRenderer().getModelRenderer().renderModel(stack.last(),buffers.getBuffer(RenderType.solid()),null,baked,1,1,1,light,overlay,net.neoforged.neoforge.client.model.data.ModelData.EMPTY,RenderType.solid());
        if(SkatModels.hasGlass(baked))mc.getBlockRenderer().getModelRenderer().renderModel(stack.last(),buffers.getBuffer(RenderType.translucent()),null,baked,1,1,1,light,overlay,net.neoforged.neoforge.client.model.data.ModelData.EMPTY,RenderType.translucent());
    }
    private static final class Propeller implements BlockEntityRenderer<SkatPropellerEntity>{
        Propeller(BlockEntityRendererProvider.Context context){}
        @Override public void render(SkatPropellerEntity entity,float dt,PoseStack stack,MultiBufferSource buffers,int light,int overlay){
            stack.pushPose();stack.translate(.5,.5,.95);float angle=net.minecraft.util.Mth.lerp(dt,entity.getPreviousAngle(),entity.getAngle());
            stack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));draw("rotor",stack,buffers,light,overlay);stack.popPose();
        }
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(SkatPropellerEntity entity){return new net.minecraft.world.phys.AABB(entity.getBlockPos()).inflate(3);}
    }
    private static final class Bridge implements BlockEntityRenderer<SkatHelmEntity>{
        Bridge(BlockEntityRendererProvider.Context context){}
        @Override public void render(SkatHelmEntity entity,float dt,PoseStack stack,MultiBufferSource buffers,int light,int overlay){
            var frustum=Minecraft.getInstance().levelRenderer.getFrustum();var local=getRenderBoundingBox(entity);var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(entity);net.minecraft.world.phys.AABB bounds=null;for(double x:new double[]{local.minX,local.maxX})for(double y:new double[]{local.minY,local.maxY})for(double z:new double[]{local.minZ,local.maxZ}){var p=new org.joml.Vector3d(x,y,z);if(body instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel client)client.renderPose(dt).transformPosition(p);var point=new net.minecraft.world.phys.AABB(p.x,p.y,p.z,p.x,p.y,p.z);bounds=bounds==null?point:bounds.minmax(point);}if(frustum!=null&&!frustum.isVisible(bounds))return;
            stack.pushPose();stack.translate(0,-4,6);
            for(int side:new int[]{-1,1}){stack.pushPose();double hinge=.5+side*.94;stack.translate(hinge,4.01,-2.32);stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(side*90*entity.door(dt)));stack.translate(-hinge,-4.01,2.32);draw(side<0?"door_port":"door_starboard",stack,buffers,light,overlay);stack.popPose();}
            stack.translate(.5,4.1,9.3);
            stack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(100*entity.hatch(dt)));
            stack.translate(-.5,-4.1,-9.3);draw("hatch",stack,buffers,light,overlay);stack.popPose();
        }
        @Override public boolean shouldRenderOffScreen(SkatHelmEntity entity){return true;}
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(SkatHelmEntity entity){var o=entity.origin();return new net.minecraft.world.phys.AABB(o.getX()-6,o.getY()+3,o.getZ()-7,o.getX()+6,o.getY()+10,o.getZ()+14);}
    }
    private SkatClient(){}
}
