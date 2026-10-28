package pro.erez.interstice.client;

import java.util.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.renderer.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.gear.*;
import pro.erez.interstice.tide.*;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.SeaSurface;

@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class EngineerClient {
    // V and the common letters are already used by voice/JEI/map/pack controls.
    // An inventory button makes settings accessible without changing anyone's bindings.
    public static final KeyMapping ACTION=new KeyMapping("key.interstice.engineer",InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN,"key.categories.interstice");
    private record Seen(UUID uuid,long until){}
    private static final Map<Integer,Seen> PULSE=new HashMap<>();
    private static net.minecraft.resources.ResourceLocation pulseDimension;
    private static EngineerNetworking.Status status;
    private static long statusTick;
    private EngineerClient(){}
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event){event.register(ACTION);}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(EngineerArmor.MENU.get(),EngineerScreen::new);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){PULSE.clear();pulseDimension=null;status=null;}
    @SubscribeEvent public static void inventory(ScreenEvent.Init.Post event){
        if(!(event.getScreen() instanceof AbstractContainerScreen<?> screen)||!(screen instanceof InventoryScreen||screen instanceof CreativeModeInventoryScreen))return;
        var mc=Minecraft.getInstance();if(mc.player==null||!EngineerArmor.worn(mc.player,EquipmentSlot.HEAD)&&!EngineerArmor.worn(mc.player,EquipmentSlot.CHEST))return;
        int y=screen.getGuiTop()+screen.getYSize()+32;if(y+18>screen.height-4)y=Math.max(4,screen.getGuiTop()-(screen instanceof CreativeModeInventoryScreen?48:24));
        int x=screen.getGuiLeft()+(screen.getXSize()-43)/2+47;
        var b=Button.builder(Component.translatable("menu.interstice.engineer.button"),unused->PacketDistributor.sendToServer(new EngineerNetworking.Action(0))).bounds(x,y,43,18).build();
        b.setTooltip(Tooltip.create(Component.translatable("tooltip.interstice.engineer.controls")));event.addListener(b);
    }
    public static void receive(EngineerNetworking.Pulse packet){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(packet.dimension()))return;
        PULSE.clear();pulseDimension=packet.dimension();long until=mc.level.getGameTime()+60;for(var target:packet.targets())PULSE.put(target.entity(),new Seen(target.uuid(),until));
    }
    public static void receive(EngineerNetworking.Status packet){var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.dimension().location().equals(packet.dimension())){status=packet;statusTick=mc.level.getGameTime();}}
    public static boolean active(){var mc=Minecraft.getInstance();return mc.level!=null&&mc.player!=null&&mc.getCameraEntity()==mc.player&&EngineerSystems.active(mc.player);}
    public static boolean night(LivingEntity entity){var mc=Minecraft.getInstance();return entity==mc.player&&active()&&EngineerSystems.night(mc.player.getItemBySlot(EquipmentSlot.HEAD));}
    public static boolean scanned(Entity entity){
        var mc=Minecraft.getInstance();if(!active()||!mc.level.dimension().location().equals(pulseDimension)||!(entity instanceof LivingEntity living)||!living.isAlive()||entity==mc.player||entity.isSpectator())return false;
        var seen=PULSE.get(entity.getId());return seen!=null&&seen.uuid().equals(entity.getUUID())&&seen.until()>mc.level.getGameTime()&&entity.distanceToSqr(mc.player)<=256&&mc.player.hasLineOfSight(entity);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();while(ACTION.consumeClick())if(mc.player!=null&&mc.screen==null&&!mc.isPaused())PacketDistributor.sendToServer(new EngineerNetworking.Action(mc.player.isShiftKeyDown()?0:1));
        if(mc.level==null){PULSE.clear();pulseDimension=null;status=null;return;}if(!mc.level.dimension().location().equals(pulseDimension))PULSE.clear();PULSE.entrySet().removeIf(e->e.getValue().until()<=mc.level.getGameTime());
        if(status!=null&&(!status.dimension().equals(mc.level.dimension().location())||mc.level.getGameTime()-statusTick>40))status=null;
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui||mc.screen!=null||!EngineerArmor.worn(mc.player,EquipmentSlot.HEAD))return;
        var helmet=mc.player.getItemBySlot(EquipmentSlot.HEAD);var chest=mc.player.getItemBySlot(EquipmentSlot.CHEST);var lines=new ArrayList<Component>();
        // Mechanical reserve indication remains readable with no cell, visor disabled or depleted.
        if(EngineerArmor.worn(chest,EquipmentSlot.CHEST))lines.add(Component.translatable("hud.interstice.engineer.air",(EngineerModules.reserve(EngineerModules.get(chest,0))+19)/20,Component.translatable(EngineerBreathing.ready(mc.player)?"hud.interstice.engineer.sealed":"hud.interstice.engineer.unsealed")));
        if(active()){
            var tide=ClientTideState.get();lines.add(Component.translatable("hud.interstice.engineer.tide",Component.translatable("tide.interstice.phase."+tide.phase().name().toLowerCase(Locale.ROOT)),Math.max(0,tide.phaseDurationTicks()-tide.phaseTicksElapsed())/20));
            if(AegisPower.realm(mc.player)){var p=GeometryProfiles.get(mc.level);double lower=mc.player.getY()-p.lowerSeaTop(),upper=SeaSurface.cellMinimum(p,mc.player.getBlockX(),mc.player.getBlockZ(),true)-mc.player.getY();lines.add(Component.translatable("hud.interstice.engineer.seas",Math.round(lower),Math.round(upper)));}
            lines.add(Component.translatable("hud.interstice.engineer.energy",EngineerModules.energy(EngineerModules.get(helmet,0))/EngineerModules.UNIT,1800));
            var held=mc.player.getMainHandItem();if(held.getItem() instanceof RealmWorktools.Worktool)lines.add(Component.translatable("hud.interstice.engineer.tool",RealmWorktools.mode(held),held.getMaxDamage()-held.getDamageValue()));
            else if(held.isDamageableItem())lines.add(Component.translatable("hud.interstice.engineer.tool",held.getHoverName(),held.getMaxDamage()-held.getDamageValue()));
            if(status!=null){if(!status.diagnostic().getString().isEmpty())lines.add(status.diagnostic());if(!status.route().getString().isEmpty())lines.add(status.route());}
        }else lines.add(Component.translatable("hud.interstice.engineer.off"));
        int y=Math.max(136,mc.getWindow().getGuiScaledHeight()/2-32);int maxWidth=Math.min(360,mc.getWindow().getGuiScaledWidth()-16);
        for(var line:lines)for(var part:mc.font.split(line,maxWidth)){if(y>mc.getWindow().getGuiScaledHeight()-46)break;event.getGuiGraphics().drawString(mc.font,part,8,y,0xa9dfc7,true);y+=10;}
    }
    @SubscribeEvent public static void preview(RenderLevelStageEvent event){
        var mc=Minecraft.getInstance();if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||!active()||mc.options.hideGui||!(mc.hitResult instanceof BlockHitResult hit))return;
        var positions=RealmWorktools.preview(mc.player,mc.player.getMainHandItem(),hit.getBlockPos(),hit.getDirection());if(positions.isEmpty())return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();var vertex=buffers.getBuffer(RenderType.lines());
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);for(var p:positions)LevelRenderer.renderLineBox(pose,vertex,new AABB(p).inflate(.002),.35F,.95F,.7F,.8F);pose.popPose();buffers.endBatch(RenderType.lines());
    }
}
