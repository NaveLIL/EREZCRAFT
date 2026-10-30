package pro.erez.interstice.client;
import java.util.*;
import dev.lambdaurora.lambdynlights.api.*;
import dev.lambdaurora.lambdynlights.api.behavior.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pro.erez.interstice.ship.*;
import org.joml.*;
/** One cached light behavior per nearby hull, capped at four; five updates/second, no world writes. */
public final class SkatLightsClient {
    private static final Map<UUID,Source> sources=new LinkedHashMap<>();private static Object world;private static DynamicLightBehaviorManager manager;private static int ticks;
    private static final Map<UUID,Long> retryAfter=new HashMap<>();
    public static int sourceCount(){return sources.size();}
    public static boolean dynamicApi(){return manager!=null;}
    public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(world!=mc.level){clear();world=mc.level;}if(mc.level==null||mc.player==null||++ticks%4!=0)return;
        if(manager==null&&(net.neoforged.fml.ModList.get().isLoaded("lambdynlights")||net.neoforged.fml.ModList.get().isLoaded("lambdynlights_runtime")))try{
            var context=(DynamicLightsContext)Class.forName("dev.lambdaurora.lambdynlights.LambDynLights").getMethod("get").invoke(null);manager=context.dynamicLightBehaviorManager();
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Pinned LambDynamicLights API changed",e);}
        var container=SubLevelContainer.getContainer(mc.level);if(container==null)return;
        var candidates=new ArrayList<SubLevel>();for(var s:container.getAllSubLevels()){if(s.isRemoved())continue;var b=s.boundingBox();double dx=(b.minX()+b.maxX())*.5-mc.player.getX(),dz=(b.minZ()+b.maxZ())*.5-mc.player.getZ();if(dx*dx+dz*dz<=64*64)candidates.add(s);}
        candidates.sort(Comparator.comparingDouble(s->{var p=s.logicalPose().position();double dx=p.x()-mc.player.getX(),dz=p.z()-mc.player.getZ();return dx*dx+dz*dz;}));
        var retained=new HashSet<UUID>();var views=new ArrayList<ShipLightUniforms.View>();
        for(var ship:candidates){if(views.size()==4)break;var id=ship.getUniqueId();var source=sources.get(id);
            if(source!=null&&source.helm.isRemoved()){source.removed=true;if(manager!=null)manager.remove(source);sources.remove(id);source=null;}
            if(source==null){if(retryAfter.getOrDefault(id,0L)>mc.level.getGameTime())continue;SkatHelmEntity helm=null;
                for(var chunk:ship.getPlot().getLoadedChunks())for(var be:chunk.getChunk().getBlockEntities().values())if(be instanceof SkatHelmEntity h&&!h.isRemoved())helm=h;
                if(helm==null){if(retryAfter.size()>128)retryAfter.clear();retryAfter.put(id,mc.level.getGameTime()+20);continue;}source=new Source(helm);source.update(ship);sources.put(id,source);if(manager!=null)manager.add(source);
            }
            source.update(ship);retained.add(id);views.add(source.view);
        }
        var iterator=sources.entrySet().iterator();while(iterator.hasNext()){var e=iterator.next();if(!retained.contains(e.getKey())){e.getValue().removed=true;if(manager!=null)manager.remove(e.getValue());iterator.remove();}}
        ShipLightUniforms.views=List.copyOf(views);
    }
    private static final class Source implements DynamicLightBehavior {
        final SkatHelmEntity helm;volatile ShipLightUniforms.View view;volatile boolean removed;private volatile long revision;private long seen=-1;
        Source(SkatHelmEntity helm){this.helm=helm;}
        void update(SubLevel ship){
            if(view!=null&&view.mask()==0&&helm.lighting()==0)return;
            var origin=helm.origin();var p=ship.logicalPose().transformPosition(new Vector3d(origin.getX(),origin.getY(),origin.getZ()),new Vector3d());var q=new Quaterniond(ship.logicalPose().orientation()).conjugate();var m=q.get(new Matrix3d());double[] inverse={m.m00,m.m01,m.m02,m.m10,m.m11,m.m12,m.m20,m.m21,m.m22};
            var old=view;boolean changed=old==null||old.mask()!=helm.lighting()||new Vector3d(old.x(),old.y(),old.z()).distanceSquared(p)>.04;
            if(!changed)for(int i=0;i<9;i++)if(java.lang.Math.abs(old.inverse()[i]-inverse[i])>.015){changed=true;break;}
            if(!changed)return;
            AABB box=null;for(double x:new double[]{SkatLightField.X0,SkatLightField.X0+SkatLightField.NX-1})for(double y:new double[]{SkatLightField.Y0,SkatLightField.Y0+SkatLightField.NY-1})for(double z:new double[]{SkatLightField.Z0,SkatLightField.Z0+SkatLightField.NZ-1}){var c=ship.logicalPose().transformPosition(new Vector3d(origin.getX()+x,origin.getY()+y,origin.getZ()+z),new Vector3d());var a=new AABB(c.x,c.y,c.z,c.x,c.y,c.z);box=box==null?a:box.minmax(a);}
            view=new ShipLightUniforms.View(p.x,p.y,p.z,inverse,helm.lighting(),box);revision++;
        }
        @Override public double lightAtPos(BlockPos p,double previous){var v=view;if(v==null||removed)return previous;return java.lang.Math.max(previous,SkatLightField.sample(v.localX(p.getX()+.5,p.getY()+.5,p.getZ()+.5),v.localY(p.getX()+.5,p.getY()+.5,p.getZ()+.5),v.localZ(p.getX()+.5,p.getY()+.5,p.getZ()+.5),v.mask()));}
        @Override public BoundingBox getBoundingBox(){var b=view.bounds();return new BoundingBox((int)java.lang.Math.floor(b.minX),(int)java.lang.Math.floor(b.minY),(int)java.lang.Math.floor(b.minZ),(int)java.lang.Math.ceil(b.maxX),(int)java.lang.Math.ceil(b.maxY),(int)java.lang.Math.ceil(b.maxZ));}
        @Override public boolean hasChanged(){if(seen==revision)return false;seen=revision;return true;}
        @Override public boolean isRemoved(){return removed||helm.isRemoved();}
    }
    public static void clear(){for(var source:sources.values()){source.removed=true;if(manager!=null)manager.remove(source);}sources.clear();retryAfter.clear();ShipLightUniforms.views=List.of();}
    private SkatLightsClient(){}
}
