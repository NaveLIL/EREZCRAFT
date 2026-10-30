package pro.erez.interstice.client;
import java.util.*;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import pro.erez.interstice.ship.SkatLightField;
/** Optional ships publish plain immutable views; the ocean renderer has no Sable/API linkage. */
public final class ShipLightUniforms {
    public record View(double x,double y,double z,double[] inverse,int mask,AABB bounds){
        public double localX(double x,double y,double z){return inverse[0]*(x-this.x)+inverse[3]*(y-this.y)+inverse[6]*(z-this.z);}
        public double localY(double x,double y,double z){return inverse[1]*(x-this.x)+inverse[4]*(y-this.y)+inverse[7]*(z-this.z);}
        public double localZ(double x,double y,double z){return inverse[2]*(x-this.x)+inverse[5]*(y-this.y)+inverse[8]*(z-this.z);}
    }
    public static volatile List<View> views=List.of();private static int texture;
    public static long uploads;
    private static int texture(){
        if(texture!=0)return texture;texture=GL11.glGenTextures();int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        var bytes=MemoryUtil.memAlloc(SkatLightField.ATLAS.length);bytes.put(SkatLightField.ATLAS).flip();
        try(var pixels=GlPixelUpload.clean()){
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,SkatLightField.NX,SkatLightField.NY*SkatLightField.NZ,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        }finally{MemoryUtil.memFree(bytes);GL11.glBindTexture(GL11.GL_TEXTURE_2D,old);}uploads++;return texture;
    }
    public static void bind(ShaderInstance shader,Vec3 camera,int cx,int cz){
        var tile=new AABB(cx*16,-4096,cz*16,cx*16+16,4096,cz*16+16);int count=0;
        for(var view:views){if(view.mask()==0||!view.bounds().intersects(tile))continue;if(count==4)break;var a=view.inverse();
            var matrix=new Matrix4f((float)a[0],(float)a[1],(float)a[2],0,(float)a[3],(float)a[4],(float)a[5],0,(float)a[6],(float)a[7],(float)a[8],0,(float)view.localX(camera.x,camera.y,camera.z),(float)view.localY(camera.x,camera.y,camera.z),(float)view.localZ(camera.x,camera.y,camera.z),1);
            shader.safeGetUniform("ErezShipLightM"+count).set(matrix);int m=view.mask();shader.safeGetUniform("ErezShipLightS"+count).set((m&1)!=0?1f:0f,(m&1)!=0?1f:0f,(m&2)!=0?1f:0f,(m&4)!=0?1f:0f);count++;
        }
        shader.safeGetUniform("ErezShipLightCount").set(count);if(count>0)shader.setSampler("ErezShipLights",texture());
    }
    public static void clear(){views=List.of();if(texture!=0)GL11.glDeleteTextures(texture);texture=0;}
    private ShipLightUniforms(){}
}
