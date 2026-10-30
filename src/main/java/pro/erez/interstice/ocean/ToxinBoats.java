package pro.erez.interstice.ocean;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import pro.erez.interstice.Interstice;

@EventBusSubscriber(modid=Interstice.ID)
public final class ToxinBoats {
    public static final String EXPOSURE="interstice:toxin_exposure";
    public static final TagKey<EntityType<?>> BOATS=TagKey.create(Registries.ENTITY_TYPE,ResourceLocation.fromNamespaceAndPath(Interstice.ID,"corrodible_boats"));
    public static boolean susceptible(Entity e){return e instanceof Boat||e.getType().is(BOATS);}
    public static double surface(Entity e){
        var w=e.level();var b=e.getBoundingBox();double sum=0;int n=0;
        for(double dx:new double[]{-.45,.45})for(double dz:new double[]{-.35,.35}){
            double x=e.getX()+dx,z=e.getZ()+dz;double h=Double.NaN;
            int sea=pro.erez.interstice.geometry.GeometryProfiles.get(w).lowerSeaTop();
            if(Math.abs(b.minY-sea)<2)h=LowerToxinSurface.fluidTop(w,x,sea,z,0);
            if(!Double.isFinite(h))for(int y=(int)Math.floor(b.minY)-1;y<=(int)Math.floor(b.maxY);y++){double next=LowerToxinSurface.fluidTop(w,x,y,z,0);if(Double.isFinite(next))h=Double.isFinite(h)?Math.max(h,next):next;}
            if(Double.isFinite(h)&&!ToxinHullOcclusion.dry(w,x,Math.min(b.maxY,h)-.001,z)&&b.minY<h+.025&&b.maxY>h-.8){sum+=h;n++;}
        }return n==0?Double.NaN:sum/n;
    }
    public static boolean floatBoat(Boat b){
        double h=surface(b);if(!Double.isFinite(h)||b.getY()<h-.7)return false;
        var v=b.getDeltaMovement();double target=h-.18;double vy=Math.clamp(v.y+(target-b.getY())*.12-v.y*.45,-.20,.20);
        b.setDeltaMovement(v.x*.95,vy,v.z*.95);b.fallDistance=0;return true;
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event){
        var e=event.getEntity();if(e.level().isClientSide||!e.isAlive()||!susceptible(e))return;
        if(e instanceof OceanBoatState state&&e.tickCount%4==0)state.interstice$exposure(exposure(e));
        double h=surface(e);if(!Double.isFinite(h))return;
        int old=exposure(e),ticks=Math.min(200,old+1);e.getPersistentData().putInt(EXPOSURE,ticks);
        var w=(ServerLevel)e.level();
        if(ticks%20==0){w.sendParticles(ParticleTypes.SMOKE,e.getX(),h+.08,e.getZ(),ticks>140?7:3,.55,.03,.4,.005);w.playSound(null,e.blockPosition(),SoundEvents.FIRE_EXTINGUISH,SoundSource.BLOCKS,.18f,.7f+ticks/400f);}
        if(ticks>=200){
            // Empty the container first: ChestBoat.remove must not duplicate its inventory.
            if(e instanceof Container c){Containers.dropContents(w,e,c);c.clearContent();}
            e.ejectPassengers();e.discard();
        }
    }
    public static int exposure(Entity e){return Math.clamp(e.getPersistentData().getInt(EXPOSURE),0,200);}
    public static void saveItem(Entity e,ItemStack stack){if(susceptible(e)&&stack.getItem() instanceof net.minecraft.world.item.BoatItem&&exposure(e)>0)CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putInt(EXPOSURE,exposure(e)));}
    public static void loadItem(Entity e,ItemStack stack){var data=stack.get(DataComponents.CUSTOM_DATA);if(susceptible(e)&&data!=null)e.getPersistentData().putInt(EXPOSURE,Math.clamp(data.copyTag().getInt(EXPOSURE),0,200));}
    private ToxinBoats(){}
}
