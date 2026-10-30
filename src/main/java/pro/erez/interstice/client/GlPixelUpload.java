package pro.erez.interstice.client;
import org.lwjgl.opengl.*;
/** CPU buffers must not inherit NativeImage row/skip settings or a pixel-unpack buffer binding. */
record GlPixelUpload(int alignment,int rowLength,int skipRows,int skipPixels,int buffer) implements AutoCloseable {
    static GlPixelUpload clean(){var state=new GlPixelUpload(GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT),GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH),GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS),GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS),GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING));GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,0);return state;}
    @Override public void close(){GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,alignment);GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,rowLength);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,skipRows);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,skipPixels);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,buffer);}
}
