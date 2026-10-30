package pro.erez.interstice.client;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import pro.erez.interstice.ocean.*;
/** A bounded 8MiB voxel atlas: sixteen nearby hulls. No extra scene pass or per-frame volume upload. */
public final class LowerToxinHullMask {
    private static final Object[] ships=new Object[16];private static final BitSet[] cells=new BitSet[16];private static int texture;
    public static long uploads;
    public static void clear(){if(texture!=0)GL11.glDeleteTextures(texture);texture=0;Arrays.fill(ships,null);Arrays.fill(cells,null);}
    private static int texture(){if(texture!=0)return texture;texture=GL11.glGenTextures();int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);var data=MemoryUtil.memCalloc(2048*4096);try(var pixels=GlPixelUpload.clean()){GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_R8,2048,4096,0,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,data);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);}finally{MemoryUtil.memFree(data);GL11.glBindTexture(GL11.GL_TEXTURE_2D,old);}return texture;}
    private static void upload(int slot,ToxinHullOcclusion.View h){int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),align=GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture());GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);var data=MemoryUtil.memCalloc(h.nx()*32*h.nz());try(var pixels=GlPixelUpload.clean()){for(int p=h.cells().nextSetBit(0);p>=0;p=h.cells().nextSetBit(p+1)){int x=p%h.nx(),z=(p/h.nx())%h.nz(),y=p/(h.nx()*h.nz());data.put((z*32+y)*h.nx()+x,(byte)255);}GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,slot*128,0,h.nx(),32*h.nz(),GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,data);}finally{MemoryUtil.memFree(data);GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,align);GL11.glBindTexture(GL11.GL_TEXTURE_2D,old);}uploads++;ships[slot]=h.ship();cells[slot]=h.cells();}
    private static Vector3d inverse(Object pose,String method,Vector3dc p)throws ReflectiveOperationException{return (Vector3d)SableOceanBridge.call(pose,method,p,new Vector3d());}
    public static void bind(ShaderInstance shader,Vec3 camera){var all=new ArrayList<>(ToxinHullOcclusion.views(Minecraft.getInstance().level));all.removeIf(h->h.world()==null||h.world().getCenter().distanceToSqr(camera)>256*256);all.sort(Comparator.comparingDouble(h->h.world().getCenter().distanceToSqr(camera)));int count=java.lang.Math.min(16,all.size());shader.safeGetUniform("ErezHullCount").set(count);if(count==0)return;texture();shader.setSampler("ErezHullMask",texture);
        try{for(int i=0;i<count;i++){var h=all.get(i);if(ships[i]!=h.ship()||cells[i]!=h.cells())upload(i,h);Object pose=SableOceanBridge.call(h.ship(),"logicalPose");var c=inverse(pose,"transformPositionInverse",new Vector3d(camera.x,camera.y,camera.z)).sub(h.x(),h.y(),h.z()).mul(h.scale());var x=inverse(pose,"transformNormalInverse",new Vector3d(1,0,0)).mul(h.scale());var y=inverse(pose,"transformNormalInverse",new Vector3d(0,1,0)).mul(h.scale());var z=inverse(pose,"transformNormalInverse",new Vector3d(0,0,1)).mul(h.scale());var m=new Matrix4f((float)x.x,(float)x.y,(float)x.z,0,(float)y.x,(float)y.y,(float)y.z,0,(float)z.x,(float)z.y,(float)z.z,0,(float)c.x,(float)c.y,(float)c.z,1);shader.safeGetUniform("ErezHullM"+i).set(m);shader.safeGetUniform("ErezHullS"+i).set((float)h.nx(),(float)h.ny(),(float)h.nz(),0f);}}catch(ReflectiveOperationException e){throw new IllegalStateException("Pinned Sable hull pose changed",e);}
    }
    private LowerToxinHullMask(){}
}
