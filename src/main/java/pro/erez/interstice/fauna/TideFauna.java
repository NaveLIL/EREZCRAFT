package pro.erez.interstice.fauna;

import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.entity.TidePhantomEntity;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.tide.TideManager;
import pro.erez.interstice.tide.ShelterDetector;

@EventBusSubscriber(modid=Interstice.ID)
public final class TideFauna {
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE,Interstice.ID);
    public static final DeferredHolder<EntityType<?>,EntityType<TidePhantomEntity>> TIDE_PHANTOM=ENTITIES.register("tide_phantom",()->
        EntityType.Builder.<TidePhantomEntity>of(TidePhantomEntity::new,MobCategory.MONSTER).sized(.9F,.5F).eyeHeight(.25F)
            .clientTrackingRange(10).updateInterval(1).build("tide_phantom"));
    private static final java.util.Map<ServerLevel,Integer> CURSORS=new WeakHashMap<>();
    private TideFauna(){}
    public static void register(IEventBus bus){ENTITIES.register(bus);bus.addListener((EntityAttributeCreationEvent event)->event.put(TIDE_PHANTOM.get(),TidePhantomEntity.attributes().build()));}
    public static boolean spawningWindow(ServerLevel level){return StrongRealmFauna.isV6(level)
        &&TidePhantomEntity.spawnPhase(TideManager.getState(level.getServer()).phase());}
    public static boolean airborneHabitat(ServerLevel level,TidePhantomEntity entity){
        return StrongRealmFauna.isV6(level)&&StrongRealmFauna.loadedBox(level,entity.getBoundingBox().inflate(1))
            &&level.getWorldBorder().isWithinBounds(entity.getBoundingBox())&&level.noCollision(entity)
            &&!level.containsAnyLiquid(entity.getBoundingBox())&&!ShelterDetector.isSheltered(level,entity);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        for(var level:event.getServer().getAllLevels()){
            if(level.getGameTime()%80!=0||!spawningWindow(level)||!StrongRealmFauna.hasMonsterCapacity(level))continue;
            var players=level.players().stream().filter(p->p.isAlive()&&!p.isCreative()&&!p.isSpectator()).toList();if(players.isEmpty())continue;
            int cursor=CURSORS.getOrDefault(level,0);CURSORS.put(level,cursor==Integer.MAX_VALUE?0:cursor+1);
            ServerPlayer player=players.get(Math.floorMod(cursor,players.size()));
            int worldCount=0;for(var entity:level.getAllEntities())if(entity instanceof TidePhantomEntity&&entity.isAlive())worldCount++;
            if(worldCount>=8||level.getEntitiesOfClass(TidePhantomEntity.class,player.getBoundingBox().inflate(64),e->e.isAlive()).size()>=2)continue;
            var profile=GeometryProfiles.get(level);
            for(int attempt=0;attempt<6;attempt++){
                double angle=level.random.nextDouble()*Math.PI*2, radius=26+level.random.nextInt(15);
                Vec3 candidate=player.position().add(Math.cos(angle)*radius,8+level.random.nextInt(9),Math.sin(angle)*radius);
                BlockPos pos=BlockPos.containing(candidate);
                if(candidate.y+2>=SeaSurface.cellMinimum(profile,pos.getX(),pos.getZ(),true))continue;
                boolean near=false,inRange=false;for(var p:level.players()){if(p.isSpectator()||!p.isAlive())continue;
                    double distance=p.position().distanceToSqr(candidate);if(distance<24*24)near=true;if(!p.isCreative()&&distance<64*64)inRange=true;}
                if(near||!inRange||!StrongRealmFauna.loadedBox(level,new AABB(pos).inflate(2))
                    ||!level.getWorldBorder().isWithinBounds(pos)||level.getSharedSpawnPos().closerToCenterThan(candidate,24)
                    ||!level.isNaturalSpawningAllowed(pos))continue;
                var entity=TIDE_PHANTOM.get().create(level);if(entity==null)break;entity.moveTo(candidate);
                if(!airborneHabitat(level,entity)
                    ||!EventHooks.checkSpawnPosition(entity,level,MobSpawnType.NATURAL))continue;
                EventHooks.finalizeMobSpawn(entity,level,level.getCurrentDifficultyAt(pos),MobSpawnType.NATURAL,null);
                if(!entity.isSpawnCancelled()&&level.addFreshEntity(entity))break;
            }
        }
    }
}
