package pro.erez.interstice.client;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import org.joml.Vector3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import pro.erez.interstice.ship.*;

/** Real keyboard movement and client menu packets in an already verified cold ship. */
final class SkatSystemsNative {
    private static ServerLevel world;private static ServerSubLevel ship;private static BlockPos origin;
    private static int step,ticks;private static CompletableFuture<?> work;private static double startZ;private static long motionAt;
    private static final JsonObject REPORT=new JsonObject();
    private static final Map<BlockPos,BlockState> PIER=new LinkedHashMap<>();private static int boardSide=1;private static boolean climbed;
    static JsonObject report(){var r=REPORT.deepCopy();if(step==16||REPORT.has("lighting"))r.add("lighting",SkatLightingNative.report());r.addProperty("step",step);r.addProperty("ticks",ticks);return r;}
    static boolean menuExpected(){return step==8||step==9||step==16||step>=17&&step<=23;}
    static void serverTick(){if(step==16)SkatLightingNative.serverTick();}
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static ServerPlayer player(){return world.getServer().getPlayerList().getPlayers().getFirst();}
    private static Vector3d global(double x,double y,double z){return ship.logicalPose().transformPosition(new Vector3d(origin.getX()+x,origin.getY()+y,origin.getZ()+z),new Vector3d());}
    private static Vector3d local(){var p=player().position();return ship.logicalPose().transformPositionInverse(new Vector3d(p.x,p.y,p.z),new Vector3d()).sub(origin.getX(),origin.getY(),origin.getZ());}
    private static void submit(Runnable action){work=world.getServer().submit(()->{try{action.run();}catch(Throwable failure){REPORT.addProperty("failure_health",player().getHealth());REPORT.addProperty("failure_local",local().toString());player().setGameMode(net.minecraft.world.level.GameType.CREATIVE);throw failure;}});}
    private static void place(double x,double y,double z){var p=global(x,y,z);player().teleportTo(world,p.x,p.y,p.z,Set.of(),180,0);player().setDeltaMovement(Vec3.ZERO);}
    private static void boardStart(int side){
        boardSide=side;climbed=false;int y=(int)Math.ceil(pro.erez.interstice.ocean.LowerToxinSurface.base(world)+.65)-1;
        var a=global(side*6.8+.5,1,.75-1.1);var b=global(side*8.0+.5,1,.75+1.1);
        for(int x=(int)Math.floor(Math.min(a.x,b.x))-1;x<=Math.ceil(Math.max(a.x,b.x))+1;x++)for(int z=(int)Math.floor(Math.min(a.z,b.z))-1;z<=Math.ceil(Math.max(a.z,b.z))+1;z++){
            var local=ship.logicalPose().transformPositionInverse(new Vector3d(x+.5,y+1,z+.5),new Vector3d()).sub(origin.getX(),origin.getY(),origin.getZ());
            if(side*(local.x-.5)<6.5||side*(local.x-.5)>8.7||Math.abs(local.z-.75)>1.6)continue;
            var p=new BlockPos(x,y,z);require(world.getBlockEntity(p)==null,"Pier fixture would replace a block entity");PIER.put(p,world.getBlockState(p));world.setBlock(p,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        }
        var start=global(side*7.3+.5,1,.75);player().teleportTo(world,start.x,y+1.02,start.z,Set.of(),0,0);player().setDeltaMovement(Vec3.ZERO);player().fallDistance=0;motionAt=world.getGameTime();REPORT.addProperty("boarding_pier_blocks_"+side,PIER.size());
    }
    private static void boardClean(){for(var entry:PIER.entrySet())world.setBlock(entry.getKey(),entry.getValue(),3);PIER.clear();}
    private static void camera(){player().setGameMode(net.minecraft.world.level.GameType.CREATIVE);player().getAbilities().flying=true;player().onUpdateAbilities();var p=global(-18,10,-17);var aim=global(.5,4.5,0);double dx=aim.x-p.x,dy=aim.y-(p.y+1.62),dz=aim.z-p.z;player().teleportTo(world,p.x,p.y,p.z,Set.of(),(float)Math.toDegrees(Math.atan2(-dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));}
    private static void walk(Minecraft mc,double x,double z,boolean down){
        var target=global(x,1.65,z);var direction=new Vector3d(target.x-mc.player.getX(),0,target.z-mc.player.getZ());
        var rotation=dev.ryanhcode.sable.api.entity.EntitySubLevelUtil.getCustomEntityOrientation(mc.player,1);
        if(rotation!=null)rotation.transformInverse(direction);
        mc.player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));mc.options.keyUp.setDown(down);
    }
    static void begin(ServerLevel w,ServerSubLevel s,BlockPos o){world=w;ship=s;origin=o;step=0;ticks=0;work=null;
        for(var cell:SkatData.CELLS)if(!cell.opening().isEmpty()){var pos=origin.offset(cell.pos());var state=world.getBlockState(pos);if(state.hasProperty(SkatOpeningBlock.OPEN)&&state.getValue(SkatOpeningBlock.OPEN))world.setBlock(pos,state.setValue(SkatOpeningBlock.OPEN,false),3);}
        REPORT.addProperty("opening_fixture_reset",true);
        var p=player();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setHealth(p.getMaxHealth());p.getAbilities().flying=false;p.onUpdateAbilities();p.getInventory().clearContent();p.getFoodData().setFoodLevel(20);place(.5,1.68,4.8);startZ=4.8;motionAt=world.getGameTime();
    }
    static void survivalSave(){require(player().getHealth()>19.9,"Player damaged before survival save");player().setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player().getAbilities().flying=false;player().onUpdateAbilities();place(.5,1.68,2.8);REPORT.addProperty("saved_survival_inside_hold",true);}
    private static void click(Minecraft mc,BlockPos relative,Vector3d hit){mc.player.getInventory().selected=8;
        var p=origin.offset(relative);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(origin.getX()+hit.x,origin.getY()+hit.y,origin.getZ()+hit.z),Direction.UP,p,false));
    }
    static boolean tick(){var mc=Minecraft.getInstance();if(work!=null&&!work.isDone())return false;if(work!=null){work.join();work=null;}ticks++;
        if(step==0){long elapsed=world.getGameTime()-motionAt;walk(mc,.5,-5.5,elapsed<=110);if(ticks%20==0)submit(()->{var p=player();var sample=new JsonObject();sample.addProperty("local",local().toString());sample.addProperty("health",p.getHealth());sample.addProperty("dry",pro.erez.interstice.ocean.ToxinHullOcclusion.dry(world,p.getX(),p.getY()+.01,p.getZ()));sample.addProperty("fluid",p.getFluidTypeHeight(pro.erez.interstice.Interstice.HEAVY_TYPE.get()));REPORT.add("walk_"+ticks,sample);});if(elapsed<120)return false;
            submit(()->{var p=local();require(p.z< -5,"Actual forehold walk obstructed: "+p);require(p.x>-.5&&p.x<1.5,"Walk left the central aisle: "+p);require(player().getHealth()>19.9,"Toxin or collision damaged a player inside the hold: "+player().getHealth());REPORT.addProperty("real_hold_walk_blocks",startZ-p.z);REPORT.addProperty("forehold_reached",true);REPORT.addProperty("hold_health_preserved",true);place(.5,4.05,-1.1);});step=1;ticks=0;
        }else if(step==1){if(ticks<25)return false;click(mc,new BlockPos(0,4,-3),new Vector3d(.5,5,-2.32));step=2;ticks=0;
        }else if(step==2){if(ticks<25)return false;submit(()->{require(SkatOpeningBlock.isOpen(world,origin,"doors"),"Real door interaction failed");REPORT.addProperty("door_client_interaction",true);});step=3;ticks=0;
        }else if(step==3){walk(mc,.5,-4.5,ticks<=28);if(ticks<35)return false;submit(()->{
            require(local().z< -3,"Player could not walk through cabin doors: "+local());require(SkatShelter.sheltered(world,player()),"Moving cabin roof did not shelter player: "+SkatShelter.lastFailure);
            pro.erez.interstice.tide.BuoyancyController.applyEntityBuoyancy(player(),1);require(!player().getAttribute(Attributes.GRAVITY).hasModifier(pro.erez.interstice.tide.BuoyancyController.BUOYANCY_ID),"Cabin did not protect during surge");
            REPORT.addProperty("real_cabin_entry",true);REPORT.addProperty("cabin_surge_shelter",true);
            SkatLightingNative.begin(world,ship,origin);
        });step=16;ticks=0;
        }else if(step==16){if(!SkatLightingNative.tick())return false;REPORT.add("lighting",SkatLightingNative.report());submit(()->place(2,4.06,7.7));step=4;ticks=0;
        }else if(step==4){if(ticks<25)return false;click(mc,new BlockPos(0,4,7),new Vector3d(.5,4.18,7.75));step=5;ticks=0;
        }else if(step==5){if(ticks<25)return false;submit(()->{require(SkatOpeningBlock.isOpen(world,origin,"hatch"),"Real hatch interaction failed");REPORT.addProperty("hatch_client_interaction",true);place(.5,3.4,7.75);});step=14;ticks=0;
        }else if(step==14){if(ticks<10)return false;click(mc,new BlockPos(0,4,7),new Vector3d(.5,4.18,7.75));step=15;ticks=0;
        }else if(step==15){if(ticks<20)return false;submit(()->{require(SkatOpeningBlock.isOpen(world,origin,"hatch"),"Hatch closed through a player on a moving ship");REPORT.addProperty("hatch_occupant_veto",true);place(.5,4.10,9.1);motionAt=world.getGameTime();});step=6;ticks=0;
        }else if(step==6){long elapsed=world.getGameTime()-motionAt;walk(mc,.5,4.8,elapsed<=100);if(ticks%20==0)submit(()->REPORT.addProperty("stair_descent_"+ticks,local().toString()));if(elapsed<120)return false;submit(()->{var p=local();require(p.z<5.1&&p.y>1.4&&p.y<1.9,"Actual standing stair descent failed: "+p);REPORT.addProperty("real_stair_descent",true);REPORT.addProperty("stair_crouch_required",false);motionAt=world.getGameTime();});step=12;ticks=0;
        }else if(step==12){long elapsed=world.getGameTime()-motionAt;walk(mc,.5,9.65,elapsed<=100);if(ticks%20==0)submit(()->REPORT.addProperty("stair_ascent_"+ticks,local().toString()));if(elapsed<120)return false;submit(()->{var p=local();require(p.z>9.3&&p.y>3.7,"Actual standing stair ascent failed: "+p);REPORT.addProperty("real_stair_ascent",true);place(.5,1.68,-1.45);});step=7;ticks=0;
        }else if(step==7){if(ticks<25)return false;click(mc,new BlockPos(-1,2,-2),new Vector3d(-.89,2.08,-1.45));step=8;ticks=0;
        }else if(step==8){if(ticks<25||ticks<120&&(mc.player.containerMenu.slots.size()!=90||!mc.player.containerMenu.getSlot(0).getItem().is(Items.DIAMOND)))return false;require(mc.player.containerMenu.slots.size()==90,"Cargo menu not opened through real interaction: "+mc.player.containerMenu.slots.size());require(mc.player.containerMenu.getSlot(0).getItem().is(Items.DIAMOND),"Cargo menu slot sync failed");
            Screenshot.grab(mc.gameDirectory,"skat-cargo-menu.png",mc.getMainRenderTarget(),m->{});mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,0,0,ClickType.QUICK_MOVE,mc.player);step=9;ticks=0;
        }else if(step==9){if(ticks<25)return false;submit(()->{
            var cargo=(SkatCargoEntity)world.getBlockEntity(origin.offset(SkatData.CARGO.getFirst().anchor()));require(cargo.getItem(0).isEmpty()&&player().getInventory().countItem(Items.DIAMOND)==1,"Real cargo quick-move failed");
            for(int i=0;i<player().getInventory().getContainerSize();i++)if(player().getInventory().getItem(i).is(Items.DIAMOND)){player().getInventory().removeItem(i,1);break;}
            cargo.setItem(0,new ItemStack(Items.DIAMOND,1));REPORT.addProperty("cargo_real_client_quick_move",true);
            var capability=world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,cargo.getBlockPos(),Direction.UP);require(capability!=null&&capability.getSlots()==54,"Cargo automation capability missing");
            require(capability.insertItem(52,new ItemStack(Items.IRON_INGOT,65),false).getCount()==1,"Automation overflow lost items");require(capability.extractItem(52,64,false).getCount()==64&&cargo.getItem(52).isEmpty(),"Automation extraction failed");REPORT.addProperty("cargo_automation",true);
            var engine=(SkatEngineEntity)world.getBlockEntity(origin.offset(-3,6,9));int before=engine.fuelTicks(),units=Math.min(64,(64000-before)/1600);var fuel=new ItemStack(Items.COAL,64);require(engine.addFuel(fuel,false,64)==(units>0),"Bulk refuel acceptance mismatch");require(fuel.getCount()==64-units&&engine.fuelTicks()==before+units*1600&&engine.fuelTicks()<=64000,"Bulk refuel lost fuel or overflowed");REPORT.addProperty("bulk_refuel",true);REPORT.addProperty("bulk_coal_accepted",units);
            boardStart(1);
        });mc.player.closeContainer();step=13;ticks=0;
        }else if(step==13){long elapsed=world.getGameTime()-motionAt;var cp=ship.logicalPose().transformPositionInverse(new Vector3d(mc.player.getX(),mc.player.getY(),mc.player.getZ()),new Vector3d()).sub(origin.getX(),origin.getY(),origin.getZ());climbed|=mc.player.onClimbable();mc.options.keyJump.setDown(elapsed<160&&cp.y<4.10);walk(mc,boardSide*4.7+.5,.75,elapsed<160);if(elapsed<180)return false;
            boolean second=boardSide<0;submit(()->{var p=local();REPORT.addProperty("boarding_climbed_"+boardSide,climbed);REPORT.addProperty("boarding_local_"+boardSide,p.toString());require(climbed&&p.y>3.7&&boardSide*(p.x-.5)<5.3,"Real shore boarding failed: "+p+" climbed="+climbed);require(player().getHealth()>19.9,"Shore boarding exposed player to toxin");REPORT.addProperty("boarding_passed_"+boardSide,true);boardClean();if(!second)boardStart(-1);else place(4.1,4.05,8.55);});if(second){step=17;}ticks=0;
        }else if(step==17||step==19){var c=SkatData.CARGO.get(step==17?24:25);mc.options.hideGui=false;var aim=global(c.x(),c.y(),c.z()).sub(mc.player.getX(),mc.player.getEyeY(),mc.player.getZ());var rotation=dev.ryanhcode.sable.api.entity.EntitySubLevelUtil.getCustomEntityOrientation(mc.player,1);if(rotation!=null)rotation.transformInverse(aim);mc.player.setYRot((float)Math.toDegrees(Math.atan2(-aim.x,aim.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(aim.y,Math.hypot(aim.x,aim.z))));if(ticks<30)return false;click(mc,c.anchor(),new Vector3d(c.x(),c.y(),c.z()));step++;ticks=0;
        }else if(step==18||step==20){if(ticks<25||ticks<120&&mc.player.containerMenu.slots.size()!=90)return false;require(mc.player.containerMenu.slots.size()==90,"Roof cargo GUI missing");int index=step==18?24:25;require(mc.player.containerMenu.getSlot(0).getItem().is(Items.DIAMOND)&&mc.player.containerMenu.getSlot(0).getItem().getCount()==index+1,"Wrong roof inventory");REPORT.addProperty("deck_crate_native_gui_"+index,true);mc.player.closeContainer();if(step==18){step=19;}else{step=21;}ticks=0;
        }else if(step==21){if(ticks<20)return false;net.neoforged.neoforge.network.PacketDistributor.sendToServer(new pro.erez.interstice.vehicle.VehicleNetwork.Request(origin.offset(0,4,-6),0,""));step=22;ticks=0;
        }else if(step==22){if(ticks<20)return false;require(mc.screen instanceof VehicleAccessScreen,"Vehicle access GUI did not open");var field=mc.screen.children().stream().filter(x->x instanceof net.minecraft.client.gui.components.EditBox).map(x->(net.minecraft.client.gui.components.EditBox)x).findFirst().orElseThrow();field.setValue("604827");mc.screen.children().stream().filter(x->x instanceof net.minecraft.client.gui.components.Button).map(x->(net.minecraft.client.gui.components.Button)x).findFirst().orElseThrow().onPress();step=23;ticks=0;
        }else if(step==23){if(ticks<25)return false;submit(()->{require(((SkatHelmEntity)world.getBlockEntity(origin.offset(0,4,-6))).security.configured(),"PIN GUI did not configure the server");REPORT.addProperty("skat_pin_actual_gui",true);camera();});mc.setScreen(null);step=10;ticks=0;
        }else if(step==10){mc.options.hideGui=true;if(ticks<40)return false;Screenshot.grab(mc.gameDirectory,"skat-systems-full.png",mc.getMainRenderTarget(),m->{});WaveRenderTelemetry.begin();step=11;ticks=0;
        }else if(step==11){if(WaveRenderTelemetry.frames()<300)return false;REPORT.add("full_frame_one_ship",WaveRenderTelemetry.end());REPORT.addProperty("passed",true);return true;}
        return false;
    }
    private SkatSystemsNative(){}
}
