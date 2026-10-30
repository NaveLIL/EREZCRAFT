package pro.erez.interstice.client;
import java.lang.reflect.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import org.joml.*;
/** Creates a real Sable body only inside the disposable native world. */
final class WaveShipFixture {
    static Object call(Object o,String name,Object...args)throws ReflectiveOperationException{
        for(var m:o.getClass().getMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){boolean match=true;for(int i=0;i<args.length;i++){Class<?> type=m.getParameterTypes()[i];if(!type.isInstance(args[i])&&!(type.isPrimitive()&&(args[i] instanceof Number||args[i] instanceof Boolean)))match=false;}if(match)return m.invoke(o,args);}throw new NoSuchMethodException(name);
    }
    static Object create(ServerLevel level)throws ReflectiveOperationException{
        var c=Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");var container=c.getMethod("getContainer",ServerLevel.class).invoke(null,level);
        for(var existing:(java.util.List<?>)call(container,"getAllSubLevels"))if("EREZ toxin wave vessel".equals(call(existing,"getName"))&&!((Boolean)call(existing,"isRemoved"))&&((Number)call(call(existing,"getMassTracker"),"getMass")).doubleValue()>0)return existing;
        var positions=new java.util.ArrayList<BlockPos>();for(int x=41;x<46;x++)for(int z=11;z<18;z++){var p=new BlockPos(x,36,z);if(!level.getBlockState(p).isAir())throw new IllegalStateException("Assembly must use actual air above sea");level.setBlock(p,Blocks.OAK_PLANKS.defaultBlockState(),3);positions.add(p);if(x==41||x==45||z==11||z==17){p=p.above();level.setBlock(p,Blocks.OAK_PLANKS.defaultBlockState(),3);positions.add(p);}}
        var boundsType=Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3i");var bounds=boundsType.getConstructor(int.class,int.class,int.class,int.class,int.class,int.class).newInstance(41,36,11,45,37,17);
        var assembly=Class.forName("dev.ryanhcode.sable.api.SubLevelAssemblyHelper");var boundInterface=Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3ic");var ship=assembly.getMethod("assembleBlocks",ServerLevel.class,BlockPos.class,Iterable.class,boundInterface).invoke(null,level,new BlockPos(43,36,14),positions,bounds);
        if(ship==null)throw new IllegalStateException("Sable assembly failed");call(ship,"setName","EREZ toxin wave vessel");var ticket=Class.forName("dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType").getField("COMMAND_FORCED").get(null);call(container,"addForceLoadTicket",ship,ticket,net.minecraft.util.Unit.INSTANCE);return ship;
    }
    static String describe(ServerLevel level,Object ship)throws ReflectiveOperationException{var c=Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem");var system=c.getMethod("get",net.minecraft.world.level.Level.class).invoke(null,level);return "paused="+call(system,"getPaused")+" removed="+call(ship,"isRemoved")+" mass="+call(call(ship,"getMassTracker"),"getMass")+" bounds="+call(call(ship,"getPlot"),"getBoundingBox");}
    static Vector3d position(Object ship)throws ReflectiveOperationException{return new Vector3d((Vector3dc)call(call(ship,"logicalPose"),"position"));}
    static Quaterniond orientation(Object ship)throws ReflectiveOperationException{return new Quaterniond((Quaterniondc)call(call(ship,"logicalPose"),"orientation"));}
    static void breach(ServerLevel level,Object ship,boolean open)throws ReflectiveOperationException{var b=call(call(ship,"getPlot"),"getBoundingBox");int x=(((Number)call(b,"minX")).intValue()+((Number)call(b,"maxX")).intValue())/2,z=(((Number)call(b,"minZ")).intValue()+((Number)call(b,"maxZ")).intValue())/2,y=((Number)call(b,"minY")).intValue();level.setBlock(new BlockPos(x,y,z),(open?Blocks.AIR:Blocks.OAK_PLANKS).defaultBlockState(),3);}
    static Object container(ServerLevel level)throws ReflectiveOperationException{return Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer").getMethod("getContainer",ServerLevel.class).invoke(null,level);}
    static void flush(ServerLevel level)throws ReflectiveOperationException{var map=call(container(level),"getHoldingChunkMap");call(map,"saveAll");call(call(map,"getStorage"),"flush");}
    static boolean storage(ServerLevel level,net.minecraft.world.level.ChunkPos pos)throws ReflectiveOperationException{return (Boolean)call(container(level),"inBounds",pos);}
    static void pause(ServerLevel level)throws ReflectiveOperationException{var c=Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem");var system=c.getMethod("get",net.minecraft.world.level.Level.class).invoke(null,level);call(system,"setPaused",true);}
}
