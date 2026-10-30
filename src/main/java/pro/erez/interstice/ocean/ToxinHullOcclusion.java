package pro.erez.interstice.ocean;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import pro.erez.interstice.Interstice;
/** Cached spill thresholds shared by contact and the surface mask. Authored thin hulls use quarter-block collision voxels. */
@EventBusSubscriber(modid=Interstice.ID)
public final class ToxinHullOcclusion {
    private static final class Hull {int x,y,z,nx,ny,nz,scale=1;byte[] spill;String signature;boolean dirty=true;BitSet cells;AABB world;}
    private static final Map<Level,Map<Object,Hull>> WORLDS=Collections.synchronizedMap(new WeakHashMap<>());
    public static long builds;public static volatile int serverCells,clientCells;
    private static Object call(Object o,String name,Object...a)throws ReflectiveOperationException{return SableOceanBridge.call(o,name,a);}
    private static double d(Object o,String n)throws ReflectiveOperationException{return ((Number)call(o,n)).doubleValue();}
    private static int i(Object o,String n)throws ReflectiveOperationException{return ((Number)call(o,n)).intValue();}
    public static void invalidate(Level w,BlockPos p){var map=WORLDS.get(w);if(map==null)return;for(var h:map.values())if(p.getX()>=h.x&&p.getX()<h.x+h.nx/h.scale&&p.getY()>=h.y&&p.getY()<h.y+h.ny/h.scale&&p.getZ()>=h.z&&p.getZ()<h.z+h.nz/h.scale)h.dirty=true;}
    @SubscribeEvent public static void tick(LevelTickEvent.Pre e){if(!e.getLevel().isClientSide())update(e.getLevel(),!WORLDS.containsKey(e.getLevel()));}
    public static void update(Level w){
        update(w,false);
    }
    private static void update(Level w,boolean force){
        if(!LowerToxinSurface.active(w)||!force&&w.getGameTime()%10!=0||!net.neoforged.fml.ModList.get().isLoaded("sable"))return;
        try {var all=SableHullAccess.subLevels(w);var map=WORLDS.computeIfAbsent(w,k->new WeakHashMap<>());
            for(var it=map.entrySet().iterator();it.hasNext();){var e=it.next();if(!all.contains(e.getKey())||((Boolean)call(e.getKey(),"isRemoved"))){it.remove();}}
            int count=0;for(var ship:all){var worldBounds=call(ship,"boundingBox");var box=new AABB(d(worldBounds,"minX"),d(worldBounds,"minY"),d(worldBounds,"minZ"),d(worldBounds,"maxX"),d(worldBounds,"maxY"),d(worldBounds,"maxZ"));double sea=LowerToxinSurface.base(w);if(box.minY>sea+4||box.maxY<sea-4)continue;
                var b=call(call(ship,"getPlot"),"getBoundingBox");String signature=b.toString();var h=map.computeIfAbsent(ship,k->new Hull());h.world=box.inflate(8);
                if(h.dirty||!signature.equals(h.signature)){
                    h.signature=signature;h.dirty=false;h.x=i(b,"minX");h.y=i(b,"minY");h.z=i(b,"minZ");h.nx=i(b,"maxX")-h.x+1;h.ny=i(b,"maxY")-h.y+1;h.nz=i(b,"maxZ")-h.z+1;h.cells=new BitSet();
                    int bx=h.nx,by=h.ny,bz=h.nz;long baseVolume=(long)bx*by*bz;h.scale=1;h.spill=null;
                    if(bx>0&&by>0&&bz>0&&bx<=128&&by<=32&&bz<=128&&baseVolume<=65536){
                        var states=new net.minecraft.world.level.block.state.BlockState[(int)baseVolume];var pos=new BlockPos.MutableBlockPos();boolean authored=false;
                        for(int y=0;y<by;y++)for(int z=0;z<bz;z++)for(int x=0;x<bx;x++){pos.set(h.x+x,h.y+y,h.z+z);var state=w.getBlockState(pos);states[(y*bz+z)*bx+x]=state;if(net.neoforged.fml.ModList.get().isLoaded("aeronautics")&&state.getBlock() instanceof pro.erez.interstice.ship.SkatBlock)authored=true;}
                        if(authored&&bx*4<=128&&bz*4<=128){h.scale=4;by=Math.min(by,7);}
                        h.nx=bx*h.scale;h.ny=by*h.scale;h.nz=bz*h.scale;boolean[] solid=new boolean[h.nx*h.ny*h.nz];
                        for(int y=0;y<by;y++)for(int z=0;z<bz;z++)for(int x=0;x<bx;x++){pos.set(h.x+x,h.y+y,h.z+z);var state=states[(y*bz+z)*bx+x];
                            if(h.scale==1)solid[(y*bz+z)*bx+x]=state.isCollisionShapeFullBlock(w,pos);
                            else for(var shape:state.getCollisionShape(w,pos).toAabbs())HullRaster.fill(solid,h.nx,h.ny,h.nz,x,y,z,h.scale,shape);
                        }
                        h.spill=HullSeal.spillLevels(solid,h.nx,h.ny,h.nz);h.cells=HullSeal.enclosed(h.spill,h.nx,h.ny,h.nz);

                    }builds++;
                }count+=h.cells.cardinality();
            }if(w.isClientSide())clientCells=count;else serverCells=count;
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Pinned Sable hull occlusion API changed",e);}
    }
    public record View(Object ship,int x,int y,int z,int nx,int ny,int nz,int scale,BitSet cells,AABB world){}
    public static List<View> views(Level w){var map=WORLDS.get(w);if(map==null)return List.of();var out=new ArrayList<View>();for(var e:map.entrySet()){var h=e.getValue();if(h.cells!=null&&!h.cells.isEmpty())out.add(new View(e.getKey(),h.x,h.y,h.z,h.nx,h.ny,h.nz,h.scale,h.cells,h.world));}return out;}
    public static boolean dry(Level w,double x,double y,double z){
        var map=WORLDS.get(w);if(map==null||map.isEmpty())return false;
        try{for(var e:map.entrySet()){var h=e.getValue();if(h.cells==null||h.cells.isEmpty()||h.spill==null||h.world==null||!h.world.contains(x,y,z))continue;
            var pose=call(e.getKey(),"logicalPose");var p=(Vector3d)call(pose,"transformPositionInverse",(Vector3dc)new Vector3d(x,y,z),new Vector3d());
            int xx=(int)Math.floor((p.x-h.x)*h.scale),yy=(int)Math.floor((p.y-h.y)*h.scale),zz=(int)Math.floor((p.z-h.z)*h.scale);
            if(xx<0||yy<0||zz<0||xx>=h.nx||yy>=h.ny||zz>=h.nz)continue;int index=(yy*h.nz+zz)*h.nx+xx;
            if(!h.cells.get(index)&&h.scale>1&&yy+1<h.ny&&h.cells.get(index+h.nx*h.nz)){
                var block=BlockPos.containing(p.x,p.y,p.z);boolean inSolid=false;for(var box:w.getBlockState(block).getCollisionShape(w,block).toAabbs())if(box.contains(p.x-block.getX(),p.y-block.getY()+.02,p.z-block.getZ())){inSolid=true;break;}
                if(!inSolid)index+=h.nx*h.nz;
            }
            if(!h.cells.get(index))continue;
            var water=(Vector3d)call(pose,"transformPositionInverse",(Vector3dc)new Vector3d(x,LowerToxinSurface.sample(w,x,z,0).height(),z),new Vector3d());
            if((water.y-h.y)*h.scale<h.spill[index]-.001)return true;
        }return false;}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    private ToxinHullOcclusion(){}
}
