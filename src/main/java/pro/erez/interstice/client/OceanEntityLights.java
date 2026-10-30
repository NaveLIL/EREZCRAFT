package pro.erez.interstice.client;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;
/** Small current light snapshot, independent of static ocean meshes and optional ship APIs. */
public final class OceanEntityLights {
    private record Point(Vec3 position,int luminance){}
    private static List<Point> points=List.of();
    private static java.lang.reflect.Method luminance,positionX,positionY,positionZ,enabled;
    private static Class<?> lightSource;
    private static boolean checked;
    public static int count(){return points.size();}
    public static void update(){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null){points=List.of();return;}
        if(!checked){checked=true;if(net.neoforged.fml.ModList.get().isLoaded("lambdynlights")||net.neoforged.fml.ModList.get().isLoaded("lambdynlights_runtime"))try{lightSource=Class.forName("dev.lambdaurora.lambdynlights.engine.source.EntityDynamicLightSource");luminance=lightSource.getMethod("getLuminance");positionX=lightSource.getMethod("getDynamicLightX");positionY=lightSource.getMethod("getDynamicLightY");positionZ=lightSource.getMethod("getDynamicLightZ");enabled=lightSource.getMethod("isDynamicLightEnabled");}catch(ReflectiveOperationException e){throw new IllegalStateException("Pinned dynamic light API changed",e);}}
        if(luminance==null)return;var camera=mc.gameRenderer.getMainCamera().getPosition();var candidates=new ArrayList<Point>();
        var nearby=new ArrayList<Entity>(mc.level.getEntities((Entity)null,new AABB(camera,camera).inflate(24)));if(!nearby.contains(mc.player))nearby.add(mc.player);
        for(var entity:nearby){
            if(entity.isRemoved()||!lightSource.isInstance(entity))continue;try{int value=((Number)luminance.invoke(entity)).intValue();if(value>0&&Boolean.TRUE.equals(enabled.invoke(entity)))candidates.add(new Point(new Vec3(((Number)positionX.invoke(entity)).doubleValue(),((Number)positionY.invoke(entity)).doubleValue(),((Number)positionZ.invoke(entity)).doubleValue()),Math.min(15,value)));}catch(ReflectiveOperationException e){throw new IllegalStateException("Dynamic luminance sampling failed",e);}
        }
        candidates.sort(Comparator.comparingDouble(p->p.position().distanceToSqr(camera)));points=List.copyOf(candidates.subList(0,Math.min(8,candidates.size())));
    }
    public static void bind(ShaderInstance shader,Vec3 camera,int cx,int cz){
        int count=0;for(var p:points){var v=p.position();if(v.x+7.75<cx*16||v.x-7.75>(cx+1)*16||v.z+7.75<cz*16||v.z-7.75>(cz+1)*16)continue;
            shader.safeGetUniform("ErezEntityLight"+count++).set((float)(v.x-camera.x),(float)(v.y-camera.y),(float)(v.z-camera.z),(float)p.luminance());
        }shader.safeGetUniform("ErezEntityLightCount").set(count);
    }
    public static void clear(){points=List.of();}
    private OceanEntityLights(){}
}
