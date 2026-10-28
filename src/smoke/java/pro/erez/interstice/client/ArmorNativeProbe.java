package pro.erez.interstice.client;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import pro.erez.interstice.gear.*;

/** Real renderer, native client packets, local optics and two-process component persistence. */
public final class ArmorNativeProbe {
    private static int phase,ticks,zombieId,cowId;
    private static CompletableFuture<?> job;
    private static final Map<EquipmentSlot,ItemStack> saved=new EnumMap<>(EquipmentSlot.class);
    private static MobEffectInstance oldNight;
    private ArmorNativeProbe(){}
    private static String kind(EquipmentSlot slot){return switch(slot){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";case FEET->"boots";default->throw new IllegalArgumentException();};}
    private static void equip(ServerPlayer p,String tier){for(var slot:RealmArmor.SLOTS){
        var item=tier.equals("aegis")?AegisArmor.item(tier+"_"+kind(slot)):RealmArmaments.item(tier+"_"+kind(slot));
        var stack=new ItemStack(item);if(tier.equals("aegis"))AegisPower.charge(stack,3210);p.setItemSlot(slot,stack);
    }p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void shot(Minecraft mc,String mode,String name){Screenshot.grab(mc.gameDirectory,"armor-"+mode+"-"+name+".png",mc.getMainRenderTarget(),m->System.out.println("ARMOR_SCREENSHOT "+name));}
    private static void inspect(Minecraft mc,JsonObject report,String tier,int cubes){
        long vertices=0;
        for(var slot:RealmArmor.SLOTS){
            var model=RealmArmorClient.model(tier,slot);require(model.cubeCount()==cubes,"Wrong authored armor revision: "+tier);
            vertices+=model.renderedVertices;
        }
        require(vertices>0,"Native armor renderer never submitted vertices: "+tier);report.addProperty(tier+"_native_vertices",vertices);
    }
    public static boolean tick(Minecraft mc,JsonObject report){
        String mode=System.getProperty("interstice.progressionSmoke");var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
        if(phase==0){
            job=server.submit(()->{
                var p=server.getPlayerList().getPlayer(uuid);for(var slot:RealmArmor.SLOTS)saved.put(slot,p.getItemBySlot(slot).copy());
                oldNight=p.getEffect(MobEffects.NIGHT_VISION);p.removeEffect(MobEffects.NIGHT_VISION);
                if(mode.equals("reload")){
                    var stored=p.getInventory().getItem(25);require(stored.is(AegisArmor.item("aegis_helmet"))&&AegisPower.charge(stored)==3210
                        &&stored.getDamageValue()==31&&stored.getHoverName().getString().equals("Magen cold proof"),"Cold Magen charge, wear or name changed");
                }
                equip(p,"riftsilver");return true;
            });mc.setScreen(new InventoryScreen(mc.player));phase=1;ticks=0;return false;
        }
        if(job!=null&&!job.isDone())return false;if(job!=null)job.join();if(++ticks<40)return false;ticks=0;
        if(phase==1){
            inspect(mc,report,"riftsilver",71);shot(mc,mode,"silver");
            job=server.submit(()->{equip(server.getPlayerList().getPlayer(uuid),"riftsteel");return true;});phase=2;
        }else if(phase==2){
            inspect(mc,report,"riftsteel",81);shot(mc,mode,"steel");
            job=server.submit(()->{
                var p=server.getPlayerList().getPlayer(uuid);equip(p,"aegis");
                if(mode.equals("create")){
                    var stored=p.getItemBySlot(EquipmentSlot.HEAD).copy();stored.setDamageValue(31);stored.set(DataComponents.CUSTOM_NAME,Component.literal("Magen cold proof"));p.getInventory().setItem(25,stored);
                }
                p.inventoryMenu.broadcastChanges();return true;
            });phase=3;
        }else if(phase==3){
            inspect(mc,report,"aegis",124);require(AegisVision.active()&&mc.player.hasEffect(MobEffects.NIGHT_VISION),"Optic night vision must be active without a potion");
            require(mc.player.getActiveEffects().stream().noneMatch(e->e.getEffect().equals(MobEffects.NIGHT_VISION)),"Optics must not overwrite or create a real potion effect");
            for(var entry:AegisArmor.PARTS.entrySet())require(mc.getItemRenderer().getModel(new ItemStack(entry.getValue().get()),mc.level,mc.player,0)!=mc.getModelManager().getMissingModel(),"Missing electronics model: "+entry.getKey());
            shot(mc,mode,"magen");mc.setScreen(null);mc.options.setCameraType(CameraType.FIRST_PERSON);
            job=server.submit(()->{
                var p=server.getPlayerList().getPlayer(uuid);var world=p.serverLevel();double x=p.getX(),y=p.getY(),z=p.getZ();
                for(int xx=-2;xx<=2;xx++)for(int yy=0;yy<3;yy++)world.setBlock(BlockPos.containing(x+xx,y+yy,z+1),Blocks.STONE.defaultBlockState(),3);
                var zombie=new Zombie(world);zombie.setPos(x-1,y,z+2);zombie.setNoAi(true);zombie.setNoGravity(true);world.addFreshEntity(zombie);zombieId=zombie.getId();
                var cow=new Cow(EntityType.COW,world);cow.setPos(x+1,y,z+2);cow.setNoAi(true);cow.setNoGravity(true);world.addFreshEntity(cow);cowId=cow.getId();return true;
            });phase=4;
        }else if(phase==4){
            var zombie=mc.level.getEntity(zombieId);var cow=mc.level.getEntity(cowId);
            require(zombie!=null&&cow!=null,"Native tracked entities must reach the scanner client");
            require(mc.shouldEntityAppearGlowing(zombie)&&mc.shouldEntityAppearGlowing(cow),"Local scanner must outline both targets through the wall");
            require(zombie.getTeamColor()==0xff625f&&cow.getTeamColor()==0x6be0d8,"Threat classification colors");
            require(!((LivingEntity)zombie).hasEffect(MobEffects.GLOWING)&&!((LivingEntity)cow).hasEffect(MobEffects.GLOWING),"Scanner must not apply a shared potion effect");
            shot(mc,mode,"scanner");job=server.submit(()->{server.getPlayerList().getPlayer(uuid).setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);return true;});phase=5;
        }else if(phase==5){
            require(!AegisVision.active()&&!mc.player.hasEffect(MobEffects.NIGHT_VISION),"Removing helmet must disable virtual night vision");
            require(!mc.shouldEntityAppearGlowing(mc.level.getEntity(zombieId)),"Removing helmet must disable local outlines");
            job=server.submit(()->{server.getPlayerList().getPlayer(uuid).addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,1200,0,false,false));return true;});phase=6;
        }else if(phase==6){
            require(!AegisVision.active()&&mc.player.hasEffect(MobEffects.NIGHT_VISION),"Ordinary potion night vision must still work independently");
            job=server.submit(()->{
                var p=server.getPlayerList().getPlayer(uuid);p.removeEffect(MobEffects.NIGHT_VISION);if(oldNight!=null)p.addEffect(new MobEffectInstance(oldNight));
                for(var slot:RealmArmor.SLOTS)p.setItemSlot(slot,saved.get(slot));
                for(int id:new int[]{zombieId,cowId}){var e=p.serverLevel().getEntity(id);if(e!=null)e.discard();}
                p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();return true;
            });phase=7;
        }else if(phase==7){
            report.addProperty("magen_local_outline_and_night_vision",true);report.addProperty("ordinary_potion_preserved",true);
            report.addProperty("three_armor_sets_native_rendered",true);report.addProperty("magen_cold_charge",mode.equals("reload"));return true;
        }
        return false;
    }
}
