package pro.erez.interstice.client;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import pro.erez.interstice.ocean.LowerToxinSurface;
/** Bounded 1MiB ring texture. Uploaded only when a cached sea tile changes. */
public final class LowerToxinShoreMask {
    private static int id;
    public static int texture(){if(id==0){id=GL11.glGenTextures();clear();}return id;}
    private static int bind(){int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE3);int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL13.glActiveTexture(active);return old;}
    private static void restore(int old){int a=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE3);GL11.glBindTexture(GL11.GL_TEXTURE_2D,old);GL13.glActiveTexture(a);}
    public static void clear(){if(id==0)return;int a=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE3);int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);var b=MemoryUtil.memAlloc(1024*1024);try(var pixels=GlPixelUpload.clean()){MemoryUtil.memSet(MemoryUtil.memAddress(b),255,b.capacity());GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_R8,1024,1024,0,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,b);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL11.GL_REPEAT);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL11.GL_REPEAT);}finally{MemoryUtil.memFree(b);GL11.glBindTexture(GL11.GL_TEXTURE_2D,old);GL13.glActiveTexture(a);}}
    public static void tile(ClientLevel w,int cx,int cz){texture();int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE3);int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);int alignment=GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);var b=MemoryUtil.memAlloc(256);try(var pixels=GlPixelUpload.clean()){
        int x=Math.floorMod(cx*16,1024),z=Math.floorMod(cz*16,1024);
        for(int zz=0;zz<16;zz++)for(int xx=0;xx<16;xx++)b.put((byte)(LowerToxinSurface.cornerMask(w,cx*16+xx,cz*16+zz)*255));b.flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,x,z,16,16,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,b);
        b.clear();for(int zz=0;zz<16;zz++)b.put((byte)(LowerToxinSurface.cornerMask(w,cx*16+16,cz*16+zz)*255));b.flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,(x+16)%1024,z,1,16,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,b);
        b.clear();for(int xx=0;xx<16;xx++)b.put((byte)(LowerToxinSurface.cornerMask(w,cx*16+xx,cz*16+16)*255));b.flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,x,(z+16)%1024,16,1,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,b);
        b.clear();b.put((byte)(LowerToxinSurface.cornerMask(w,cx*16+16,cz*16+16)*255));b.flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,(x+16)%1024,(z+16)%1024,1,1,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,b);
    }finally{MemoryUtil.memFree(b);GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,alignment);GL11.glBindTexture(GL11.GL_TEXTURE_2D,old);GL13.glActiveTexture(active);}}
    public static void release(){if(id!=0)GL11.glDeleteTextures(id);id=0;}
    private LowerToxinShoreMask(){}
}
