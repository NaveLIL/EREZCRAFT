package pro.erez.interstice.test;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import pro.erez.interstice.vehicle.*;
import pro.erez.interstice.ship.*;
/** Executed only by a fresh, explicitly owned native fleet fixture; never shipped. */
public final class VehicleFleetChecks {
    public static final String PIN="731864";
    static void need(boolean b,String s){if(!b)throw new IllegalStateException(s);}
    static ServerPlayer actor(ServerLevel w,String name){var id=UUID.nameUUIDFromBytes(("erez-fleet:"+name).getBytes(StandardCharsets.UTF_8));return FakePlayerFactory.get(w,new GameProfile(id,name));}
    public static JsonObject eitan(EitanController c,ServerPlayer owner){var w=owner.serverLevel();var out=new JsonObject();var old=c.security.save();try{
        var rogue=actor(w,"FleetIntruder");var at=c.world(0,1,0);rogue.setPos(at.x,at.y,at.z);need(!c.security.allowed(rogue),"Intruder unexpectedly trusted");need(c.security.allowed(owner),"Owner rejected");
        int channel=3;float motion=c.target[channel];boolean engine=c.engine;c.action(rogue,0,0);c.mechanism(rogue,channel,true);c.input(rogue,1,1,false,true);need(c.engine==engine&&c.target[channel]==motion,"Forged controls changed the vehicle");out.addProperty("unauthorized_controls_denied",true);
        need(!EitanSeatEntity.mount(c,3,rogue),"Intruder mounted troop seat");out.addProperty("unauthorized_boarding_denied",true);
        var cell=EitanData.CELLS.stream().filter(v->v.groups().isEmpty()&&!v.pos().equals(EitanData.CONTROL)&&EitanData.MOUNTS.stream().noneMatch(m->m.pos().equals(v.pos()))).findFirst().orElseThrow();var p=c.origin().offset(cell.pos());var state=w.getBlockState(p);rogue.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.NETHERITE_PICKAXE));need(state.getDestroyProgress(rogue,w,p)==0,"Intruder can mine the hull");need(!rogue.gameMode.destroyBlock(p)&&w.getBlockState(p)==state,"Native player mining removed protected hull");out.addProperty("native_pickaxe_break_denied",true);
        need(c.security.changePin(owner,PIN),"Owner PIN setup failed");long tick=w.getGameTime();for(int i=0;i<5;i++)need(!c.security.unlock(rogue,"000000",tick+i*11),"Wrong PIN accepted");need(!c.security.unlock(rogue,PIN,tick+60),"PIN lockout bypassed");need(c.security.unlock(rogue,PIN,tick+1300),"Correct PIN after lockout failed");out.addProperty("pin_and_lockout_checked",true);
        var full=c.security.save();need(!full.toString().contains(PIN),"Plaintext PIN persisted");var restored=new VehicleSecurity();restored.load(full);need(restored.allowed(rogue)&&restored.allowed(owner),"Owner/crew NBT round trip lost access");var publicTag=c.getUpdateTag(w.registryAccess()).getCompound("VehicleAccess");need(!publicTag.contains("Salt")&&!publicTag.contains("Hash")&&!publicTag.contains("Crew"),"Private access data leaked into update packet");out.addProperty("private_pin_and_persistent_crew",true);
        byte[] before=c.security.save().getByteArray("Hash");VehicleNetwork.handle(rogue,new VehicleNetwork.Request(c.getBlockPos(),2,"999999"));need(Arrays.equals(before,c.security.save().getByteArray("Hash")),"Crew reset the owner's PIN");need(!c.security.toggleMaintenance(rogue,tick),"Crew enabled structural maintenance");need(c.security.toggleMaintenance(owner,tick),"Owner maintenance rejected");need(state.getDestroyProgress(owner,w,p)>0,"Owner cannot service hull");restored.load(c.security.save());need(!restored.maintenance(owner,tick),"Maintenance survived reload");out.addProperty("owner_only_temporary_maintenance",true);
        need(c.security.changePin(owner,"864137")&&!c.security.allowed(rogue),"PIN rotation did not revoke old crew");out.addProperty("pin_rotation_revokes_crew",true);
    }finally{c.security.load(old);VehicleAccess.changed(c);}out.addProperty("passed",true);return out;}
    public static void verifyUi(EitanController c,ServerPlayer owner){need(c.security.configured(),"Native PIN panel did not configure a PIN");var crew=actor(owner.serverLevel(),"FleetUiCrew");var p=c.world(0,1,0);crew.setPos(p.x,p.y,p.z);need(c.security.unlock(crew,PIN,owner.serverLevel().getGameTime()),"The native GUI configured the wrong PIN");VehicleAccess.changed(c);}
    public static void cold(EitanController c,ServerPlayer owner){need(c.security.owner().equals(owner.getUUID())&&c.security.allowed(owner)&&c.security.configured(),"Cold ownership/PIN lost");need(c.security.allowed(actor(owner.serverLevel(),"FleetUiCrew")),"Cold crew access lost");need(!c.security.maintenance(owner,owner.serverLevel().getGameTime()),"Cold maintenance lease restored");}
    public static JsonObject bunker(ServerLevel w){var out=new JsonObject();var positions=new BlockPos[]{new BlockPos(72,210,32),new BlockPos(74,210,32),new BlockPos(76,210,32)};for(var p:positions){w.getChunkAt(p);need(w.getBlockState(p).isAir(),"Disposable fuel test site occupied");}try{
        var cargo=SkatData.CELLS.stream().filter(c->c.cargo()==0).findFirst().orElseThrow();var engines=SkatData.CELLS.stream().filter(c->c.role().equals("engine")).toList();w.setBlock(positions[0],SkatShips.state(cargo),3);w.setBlock(positions[1],SkatShips.state(engines.get(0)),3);w.setBlock(positions[2],SkatShips.state(engines.get(1)),3);
        var b=(SkatCargoEntity)w.getBlockEntity(positions[0]);var l=(SkatEngineEntity)w.getBlockEntity(positions[1]);var r=(SkatEngineEntity)w.getBlockEntity(positions[2]);b.setItem(0,new ItemStack(Items.DIAMOND,3));b.setItem(1,new ItemStack(Items.COAL,4));b.setItem(2,new ItemStack(Items.LAVA_BUCKET));need(b.feed(l)&&b.feed(r),"Bunker did not feed both engines");need(l.fuelTicks()==1600&&r.fuelTicks()==1600&&b.getItem(1).getCount()==2,"Bunker duplicated or lost coal");need(b.getItem(0).getCount()==3&&b.getItem(2).is(Items.LAVA_BUCKET),"Non-fuel or container item consumed");
        var tag=b.saveWithFullMetadata(w.registryAccess());var clone=new SkatCargoEntity(positions[0],b.getBlockState());clone.loadWithComponents(tag,w.registryAccess());need(clone.inventoryId().equals(b.inventoryId())&&clone.getItem(1).getCount()==2,"Bunker NBT lost contents/UUID");var coal=new ItemStack(Items.COAL,64);need(l.addFuel(coal,false,64)&&l.fuelTicks()==64000,"Engine fuel cap incorrect");int count=coal.getCount();need(!l.addFuel(coal,false,1)&&coal.getCount()==count,"Full engine consumed coal");out.addProperty("both_engines",true);out.addProperty("coal_conserved",true);out.addProperty("foreign_items_preserved",true);out.addProperty("inventory_uuid_and_nbt",true);out.addProperty("fuel_cap_does_not_eat_items",true);out.addProperty("cargo_containers",SkatData.CARGO.size());need(SkatData.CARGO.size()==26,"Deck inventories missing");out.addProperty("passed",true);
    }finally{if(w.getBlockEntity(positions[0]) instanceof SkatCargoEntity b)b.clearContent();for(var p:positions)w.setBlock(p,Blocks.AIR.defaultBlockState(),3);}return out;}
    private VehicleFleetChecks(){}
}
