package pro.erez.interstice.gear;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.RetortBlockEntity;
import pro.erez.interstice.tide.*;

/** Server accounting is per actual tick: no offline, reconnect or dimension catch-up grants. */
@EventBusSubscriber(modid=Interstice.ID)
public final class EngineerSystems {
    public static final String VISOR="interstice_engineer_visor",NIGHT="interstice_engineer_night",PULSE="interstice_engineer_next_pulse",MARKER="interstice_engineer_marker";
    public static final ResourceKey<Enchantment> ATTUNEMENT=ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,ResourceLocation.fromNamespaceAndPath(Interstice.ID,"engineer_attunement"));
    private static final Map<Player,Long> TICKS=new WeakHashMap<>();
    private EngineerSystems(){}
    public static boolean visor(ItemStack helmet){var tag=EngineerModules.data(helmet);return !tag.contains(VISOR)||tag.getBoolean(VISOR);}
    public static boolean night(ItemStack helmet){return EngineerModules.data(helmet).getBoolean(NIGHT);}
    public static void toggle(ItemStack helmet,boolean night){var tag=EngineerModules.data(helmet);String key=night?NIGHT:VISOR;tag.putBoolean(key,night?!night(helmet):!visor(helmet));EngineerModules.data(helmet,tag);}
    public static boolean active(LivingEntity wearer){return wearer.isAlive()&&!wearer.isSpectator()&&EngineerArmor.worn(wearer,EquipmentSlot.HEAD)&&visor(wearer.getItemBySlot(EquipmentSlot.HEAD))&&EngineerModules.energy(EngineerModules.get(wearer.getItemBySlot(EquipmentSlot.HEAD),0))>=cost(night(wearer.getItemBySlot(EquipmentSlot.HEAD))?2:1,attunement(wearer))/20;}
    public static int attunement(LivingEntity wearer){var reg=wearer.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);var holder=reg.getHolder(ATTUNEMENT);return holder.isEmpty()?0:Math.clamp(EnchantmentHelper.getItemEnchantmentLevel(holder.get(),wearer.getItemBySlot(EquipmentSlot.HEAD)),0,3);}
    public static int cost(int units,int level){return Math.max(EngineerModules.UNIT,(int)Math.round(units*EngineerModules.UNIT*(1-.1*Math.clamp(level,0,3))));}
    public static int harvest(LivingEntity wearer,TideState tide){
        return !AegisPower.realm(wearer)?0:25+(AegisPower.exposedSurge(wearer,tide)?Math.round(125*Math.clamp(tide.buoyancyIntensity(),0,1)):0);
    }
    public static long cooldown(ServerPlayer player){return Math.clamp(player.getPersistentData().getLong(PULSE)-player.server.overworld().getGameTime(),0,240);}
    public static boolean loadedRay(ServerPlayer player,Vec3 from,Vec3 to){
        var delta=to.subtract(from);int count=Math.max(1,(int)Math.ceil(delta.length()*2));if(count>128)return false;
        for(int i=0;i<=count;i++){var p=BlockPos.containing(from.add(delta.scale((double)i/count)));if(player.serverLevel().getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)==null)return false;}return true;
    }
    public static boolean visible(ServerPlayer player,LivingEntity target){return target!=player&&target.isAlive()&&!target.isSpectator()&&!(target instanceof ArmorStand)&&target.level()==player.level()&&target.distanceToSqr(player)<=256&&loadedRay(player,player.getEyePosition(),target.getEyePosition())&&player.hasLineOfSight(target);}
    public static boolean pulse(ServerPlayer player){
        if(!active(player)||player.isSpectator()||cooldown(player)>0)return false;
        var helmet=player.getItemBySlot(EquipmentSlot.HEAD);var cell=EngineerModules.get(helmet,0);int cost=cost(40,attunement(player));if(EngineerModules.energy(cell)<cost)return false;
        var targets=player.serverLevel().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(16),e->visible(player,e));
        EngineerModules.energy(cell,EngineerModules.energy(cell)-cost);EngineerModules.set(helmet,0,cell);player.getInventory().setChanged();
        player.getPersistentData().putLong(PULSE,player.server.overworld().getGameTime()+240);
        EngineerNetworking.pulse(player,targets);return true;
    }
    private static void rememberMarker(ServerPlayer player,ItemStack helmet){
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            var stack=player.getInventory().getItem(i);var marker=RouteMarkers.marker(stack);if(marker==null)continue;
            var tag=EngineerModules.data(helmet);var saved=EngineerModules.data(stack).getCompound(RouteMarkers.KEY);
            if(!tag.getCompound(MARKER).equals(saved)){tag.put(MARKER,saved.copy());EngineerModules.data(helmet,tag);player.getInventory().setChanged();}break;
        }
    }
    public static Component route(ServerPlayer player){
        var marker=RouteMarkers.parse(EngineerModules.data(player.getItemBySlot(EquipmentSlot.HEAD)).getCompound(MARKER));return marker==null?Component.empty():RouteMarkers.describe(player.level(),player,marker);
    }
    public static Component diagnostic(ServerPlayer player){
        double reach=Math.min(8,player.blockInteractionRange());var from=player.getEyePosition();var end=from.add(player.getLookAngle().scale(reach));if(!loadedRay(player,from,end))return Component.empty();
        var hit=player.level().clip(new ClipContext(from,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));if(hit.getType()!=HitResult.Type.BLOCK)return Component.empty();
        var be=player.level().getBlockEntity(hit.getBlockPos());
        if(be instanceof RetortBlockEntity retort)return Component.translatable("hud.interstice.engineer.retort",Component.translatable("menu.interstice.retort.status."+retort.data.get(3)),retort.data.get(2),retort.data.get(0),retort.data.get(1));
        if(be instanceof RouteBeaconEntity beacon)return Component.translatable("hud.interstice.engineer.beacon",beacon.fuelTicks()/20);
        if(be instanceof pro.erez.interstice.tether.WinchBlockEntity winch)return Component.translatable("hud.interstice.engineer.winch",winch.hooks().size());
        if(be instanceof pro.erez.interstice.lift.FieldAnchorEntity anchor)return Component.translatable("hud.interstice.engineer.lift",Component.translatable("status.interstice.lift."+anchor.status().name().toLowerCase(Locale.ROOT)),anchor.fuel(),Math.round(anchor.target()));
        return Component.empty();
    }
    public static void tick(ServerPlayer player){
        long now=player.server.overworld().getGameTime();if(Objects.equals(TICKS.put(player,now),now))return;
        if(!player.isAlive()||player.isSpectator())return;
        EngineerBreathing.tick(player);
        if(!EngineerArmor.worn(player,EquipmentSlot.HEAD))return;
        var helmet=player.getItemBySlot(EquipmentSlot.HEAD);var cell=EngineerModules.get(helmet,0);
        if(EngineerModules.cell(cell)){
            int available=Math.min(EngineerModules.CAPACITY,EngineerModules.energy(cell)+harvest(player,TideManager.getState(player.server)));
            int drain=visor(helmet)?cost(night(helmet)?2:1,attunement(player))/20:0;
            int next=Math.max(0,available-drain);
            if(next!=EngineerModules.energy(cell)){EngineerModules.energy(cell,next);EngineerModules.set(helmet,0,cell);player.getInventory().setChanged();}
        }
        if(now%10==0){
            // Other menus do not contain armor slots. Keep the owner's inventory synchronized too.
            if(player.containerMenu!=player.inventoryMenu)syncWorn(player);
            rememberMarker(player,helmet);EngineerNetworking.status(player,active(player)?diagnostic(player):Component.empty(),active(player)?route(player):Component.empty(),EngineerBreathing.ready(player));}
    }
    /** Container id -2 is the vanilla direct player-inventory update, valid while another menu is open. */
    public static void syncWorn(ServerPlayer player){
        for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST))player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE+slot.getIndex(),player.getItemBySlot(slot).copy()));
    }
    public static boolean open(ServerPlayer player){
        if(!player.isAlive()||player.isSpectator()||!player.containerMenu.getCarried().isEmpty()||player.containerMenu!=player.inventoryMenu&&!(player.containerMenu instanceof EngineerMenu)
                ||!EngineerArmor.worn(player,EquipmentSlot.HEAD)&&!EngineerArmor.worn(player,EquipmentSlot.CHEST))return false;
        player.openMenu(new SimpleMenuProvider((id,inv,owner)->new EngineerMenu(id,inv),Component.translatable("menu.interstice.engineer.title")));return true;
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){if(event.getEntity() instanceof ServerPlayer player)tick(player);}
}
