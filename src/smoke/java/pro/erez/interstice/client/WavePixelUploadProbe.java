package pro.erez.interstice.client;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
/** Real GL regression: both uploads must restore deliberately non-default unpack state. */
final class WavePixelUploadProbe {
    static void run(){var mc=Minecraft.getInstance();try(var old=GlPixelUpload.clean()){int buffer=GL15.glGenBuffers();try{GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,buffer);GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER,16,GL15.GL_STREAM_DRAW);GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,37);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,3);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,5);GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,8);while(GL11.glGetError()!=GL11.GL_NO_ERROR){}
        LowerToxinShoreMask.release();LowerToxinShoreMask.tile(mc.level,mc.player.blockPosition().getX()>>4,mc.player.blockPosition().getZ()>>4);LowerToxinHullMask.clear();LowerToxinHullMask.bind(LowerToxinRenderer.shader,mc.gameRenderer.getMainCamera().getPosition());
        if(GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH)!=37||GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS)!=3||GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS)!=5||GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT)!=8||GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING)!=buffer)throw new IllegalStateException("Ocean upload leaked pixel-store state");int error=GL11.glGetError();if(error!=GL11.GL_NO_ERROR)throw new IllegalStateException("Ocean upload GL error: "+error);
        }finally{GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);GL15.glDeleteBuffers(buffer);}}}
    private WavePixelUploadProbe(){}
}
