package pro.erez.interstice.fauna;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.*;
import pro.erez.interstice.*;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.worldgen.*;
public final class LozhnokorenFauna {
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE,Interstice.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(BuiltInRegistries.ITEM,Interstice.ID);
    public static final DeferredHolder<EntityType<?>,EntityType<Lozhnokoren>> TYPE=ENTITIES.register("lozhnokoren",()->EntityType.Builder.<Lozhnokoren>of(Lozhnokoren::new,MobCategory.MONSTER).sized(2.4f,2.1f).eyeHeight(1.1f).clientTrackingRange(12).updateInterval(1).build("interstice:lozhnokoren"));
    public static final DeferredHolder<Item,Item> EGG=ITEMS.register("lozhnokoren_spawn_egg",()->new net.neoforged.neoforge.common.DeferredSpawnEggItem(TYPE,0x473e30,0x716c41,new Item.Properties()));
    public static void register(IEventBus bus){ENTITIES.register(bus);ITEMS.register(bus);bus.addListener((EntityAttributeCreationEvent e)->e.put(TYPE.get(),Lozhnokoren.attributes().build()));bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.SPAWN_EGGS)e.accept(EGG.get());});NeoForge.EVENT_BUS.addListener(LozhnokorenFauna::tick);}
    public static boolean habitat(ServerLevel level,BlockPos pos){
        if(!StrongRealmFauna.isV6(level)||!StrongRealmFauna.loadedBox(level,new AABB(pos).inflate(7,4,7)))return false;
        var g=GeometryProfiles.get(level);if(pos.getY()<=g.lowerSeaTop()||pos.getY()>g.maxLand())return false;
        if(!level.getBlockState(pos.below()).is(ForestFlora.FLOOR))return false;
        var biome=level.getBiome(pos);if(!biome.is(RealmBiomes.PALE_GARDENS)&&!biome.unwrapKey().map(k->k.location().getPath().equals("crimson_thickets")).orElse(false))return false;
        return true;
    }
    public static boolean trySpawn(ServerLevel level,BlockPos pos){
        if(!StrongRealmFauna.hasMonsterCapacity(level)||!habitat(level,pos)||!level.isNaturalSpawningAllowed(pos)||!level.getWorldBorder().isWithinBounds(pos))return false;
        boolean nearby=false;for(var p:level.players()){double d=p.position().distanceToSqr(Vec3.atBottomCenterOf(pos));if(!p.isSpectator()&&d<32*32)return false;if(!p.isCreative()&&!p.isSpectator()&&d<80*80)nearby=true;}if(!nearby)return false;
        int world=0;for(var e:level.getAllEntities())if(e instanceof Lozhnokoren&&e.isAlive()){if(++world>=6||e.distanceToSqr(Vec3.atBottomCenterOf(pos))<128*128)return false;}
        var mob=TYPE.get().create(level);mob.moveTo(Vec3.atBottomCenterOf(pos));mob.setYRot(level.random.nextFloat()*360);
        if(!mob.canOccupy(mob.position(),mob.getYRot()))return false;
        mob.refreshSections();for(var part:mob.getParts()){var p=new BlockPos(part.blockPosition().getX(),pos.getY(),part.blockPosition().getZ()); // Placement support uses the declared body sections, not only the root box.
            if(!level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP))return false;
        }
        if(!EventHooks.checkSpawnPosition(mob,level,MobSpawnType.NATURAL))return false;
        EventHooks.finalizeMobSpawn(mob,level,level.getCurrentDifficultyAt(pos),MobSpawnType.NATURAL,null);return !mob.isSpawnCancelled()&&level.addFreshEntity(mob);
    }
    private static void tick(ServerTickEvent.Post event){for(var level:event.getServer().getAllLevels()){
        if(level.getGameTime()%400!=0||!StrongRealmFauna.hasMonsterCapacity(level))continue;
        var players=level.players().stream().filter(p->p.isAlive()&&!p.isCreative()&&!p.isSpectator()).toList();if(players.isEmpty())continue;var p=players.get(level.random.nextInt(players.size()));
        for(int i=0;i<6;i++){double a=level.random.nextDouble()*Math.PI*2;int r=36+level.random.nextInt(29),x=(int)Math.floor(p.getX()+Math.cos(a)*r),z=(int)Math.floor(p.getZ()+Math.sin(a)*r);var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);if(chunk==null)continue;var g=GeometryProfiles.get(level);
            for(int y=Math.min(g.maxLand(),(int)p.getY()+20);y>Math.max(g.lowerSeaTop(),(int)p.getY()-24);y--){var at=new BlockPos(x,y,z);if(chunk.getBlockState(at).isAir()&&chunk.getBlockState(at.below()).is(ForestFlora.FLOOR)&&trySpawn(level,at))break;}
        }
    }}
    private LozhnokorenFauna(){}
}
