package pro.erez.interstice.ocean;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import pro.erez.interstice.Interstice;

/** Optional pinned Sable bridge; point impulses run inside its physical substep, never teleport a ship. */
public final class SableOceanBridge {
    public static volatile double lastMass,lastDt,lastWorldY;public static volatile int lastHullPoints;public static final LongAdder CALLS=new LongAdder();
    public static final LongAdder STEPS=new LongAdder(),POINTS=new LongAdder();
    private record Hull(String bounds,long refreshed,List<Vector3d> points,double area){}
    private static final Map<Object,Hull> HULLS=new WeakHashMap<>();
    private static final Map<String,Method> METHODS=new java.util.concurrent.ConcurrentHashMap<>();
    public static Object call(Object o,String name,Object...args)throws ReflectiveOperationException{
        String key=o.getClass().getName()+"#"+name+Arrays.toString(Arrays.stream(args).map(a->a.getClass().getName()).toArray());var m=METHODS.get(key);
        if(m==null){for(var p:o.getClass().getMethods())if(p.getName().equals(name)&&p.getParameterCount()==args.length){boolean matches=true;for(int i=0;i<args.length;i++)if(!p.getParameterTypes()[i].isInstance(args[i])&&!(p.getParameterTypes()[i].isPrimitive()&&(args[i] instanceof Number||args[i] instanceof Boolean)))matches=false;if(matches){m=p;break;}}if(m==null)throw new NoSuchMethodException(key);METHODS.put(key,m);}return m.invoke(o,args);
    }
    private static int i(Object o,String n)throws ReflectiveOperationException{return ((Number)call(o,n)).intValue();}
    public static void step(Object ship,Object system,Object handle,double dt){
        try{
            CALLS.increment();lastDt=dt;
            var level=(ServerLevel)call(ship,"getLevel");if(!LowerToxinSurface.active(level)||!(dt>0&&dt<=.1))return;
            Object plot=call(ship,"getPlot"),bounds=call(plot,"getBoundingBox"),pose=call(ship,"logicalPose");
            String signature=bounds.toString();var hull=HULLS.get(ship);
            if(hull==null||!hull.bounds.equals(signature)||level.getGameTime()-hull.refreshed>=40){
                BlockGetter blocks=level;var points=new ArrayList<Vector3d>();
                int minX=i(bounds,"minX"),minY=i(bounds,"minY"),minZ=i(bounds,"minZ"),maxX=i(bounds,"maxX"),maxY=i(bounds,"maxY"),maxZ=i(bounds,"maxZ");
                for(int x=0;x<4;x++)for(int z=0;z<4;z++){int bx=(int)Math.floor(minX+(maxX-minX+1)*(x+.5)/4),bz=(int)Math.floor(minZ+(maxZ-minZ+1)*(z+.5)/4);for(int y=minY;y<=Math.min(maxY,minY+48);y++){var state=blocks.getBlockState(new BlockPos(bx,y,bz));if(!state.isAir()&&!state.getCollisionShape(blocks,new BlockPos(bx,y,bz)).isEmpty()){points.add(new Vector3d(bx+.5,y+.5,bz+.5));}}}
                hull=new Hull(signature,level.getGameTime(),List.copyOf(points),(maxX-minX+1)*(maxZ-minZ+1)/16.0);HULLS.put(ship,hull);
            }
            lastHullPoints=hull.points.size();if(hull.points.isEmpty())return;double mass=((Number)call(call(ship,"getMassTracker"),"getMass")).doubleValue();lastMass=mass;if(!Double.isFinite(mass)||mass<=0)return;
            var linear=(Vector3dc)call(handle,"getLinearVelocity");var angular=(Vector3dc)call(handle,"getAngularVelocity");var center=(Vector3dc)call(pose,"transformPosition",(Vector3dc)call(call(ship,"getMassTracker"),"getCenterOfMass"),new Vector3d());
            for(var local:hull.points){var world=(Vector3d)call(pose,"transformPosition",(Vector3dc)local,new Vector3d());
                lastWorldY=world.y;var p=BlockPos.containing(world.x,LowerToxinSurface.base(level),world.z);if(!level.hasChunkAt(p)||!level.getBlockState(p).is(Interstice.HEAVY_BLOCK.get()))continue;
                var surface=LowerToxinSurface.sample(level,world.x,world.z,0);double depth=surface.height()-(world.y-.5),flatDepth=Math.ceil(LowerToxinSurface.base(level))-(world.y-.5);if(Math.max(depth,flatDepth)<=0||Math.min(depth,flatDepth)>=1)continue;
                var velocity=new Vector3d(angular).cross(new Vector3d(world).sub(center)).add(linear);
                double unitMass=mass/hull.points.size(),wet=Math.clamp(Math.max(depth,flatDepth),0,1);double lift=volumeDifference(surface.height(),Math.ceil(LowerToxinSurface.base(level)),world.y,hull.area)-unitMass*2*(velocity.y-surface.velocity()*20);
                // Replace only the displaced-volume difference at the waterline. Fully dry/submerged blocks add zero.
                var impulse=new Vector3d(-velocity.x*unitMass*.4*wet*dt,lift*dt,-velocity.z*unitMass*.4*wet*dt);
                var localImpulse=(Vector3dc)call(pose,"transformNormalInverse",(Vector3dc)impulse,new Vector3d());call(handle,"applyImpulseAtPoint",(Vector3dc)local,localImpulse);POINTS.increment();
            }STEPS.increment();
        }catch(InvocationTargetException e){throw new IllegalStateException("Sable ocean physics failed",e.getCause());}catch(ReflectiveOperationException e){throw new IllegalStateException("Pinned Sable2.0.6 ocean API changed",e);}
    }
    public static double volumeDifference(double waveTop,double flatTop,double centerY,double area){return 9.81*area*(Math.clamp(waveTop-centerY+.5,0,1)-Math.clamp(flatTop-centerY+.5,0,1));}
    private SableOceanBridge(){}
}
