package pro.erez.interstice.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import pro.erez.interstice.geometry.GeometryProfile;

/** UV-only OC01 adapter. The same world point has the same material phase on every face. */
public final class LowerOceanUv {
    public static final int PERIOD=64,TILE=640,FRAME=1280;
    public static final LongAdder SODIUM_QUADS=new LongAdder(),VANILLA_QUADS=new LongAdder(),GEOMETRY_ERRORS=new LongAdder();
    public record Context(BlockPos pos,GeometryProfile profile,TextureAtlasSprite sprite,boolean ocean){public Context(BlockPos p,GeometryProfile g,TextureAtlasSprite s){this(p,g,s,false);}}
    public static boolean ocean(net.minecraft.world.level.BlockAndTintGetter w,BlockPos p,GeometryProfile g){return LowerToxinRenderer.active()&&p.getY()==g.lowerSeaTop()&&w.getFluidState(p).isSource()&&w.getBlockState(p.below()).is(pro.erez.interstice.Interstice.HEAVY_BLOCK.get())&&w.getBlockState(p.above()).isAir()&&w.getBlockState(p.above(2)).isAir();}
    public static boolean top(Object quad){try{var a=access(quad);float y=(float)a.y.invoke(quad,0);return y>.7f&&Math.abs(y-(float)a.y.invoke(quad,1))<.0001&&Math.abs(y-(float)a.y.invoke(quad,2))<.0001&&Math.abs(y-(float)a.y.invoke(quad,3))<.0001;}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static volatile Access access;
    private record Access(Method x,Method y,Method z,Method u,Method v,Method color,Method light,Method normal){}
    public static float[] coordinates(BlockPos block,float[] x,float[] y,float[] z,GeometryProfile profile,TextureAtlasSprite sprite){
        if(sprite.contents().width()!=FRAME||sprite.contents().height()!=FRAME)throw new IllegalStateException("OC01 atlas frame missing: "+sprite.contents().name()+" / "+sprite.contents().width()+"x"+sprite.contents().height());
        double[] u=new double[4],v=new double[4];double minU=Double.POSITIVE_INFINITY,minV=Double.POSITIVE_INFINITY;
        for(int i=0;i<4;i++){
            double height=block.getY()+y[i]-(profile.lowerSeaTop()+8.0/9.0);
            u[i]=block.getX()+x[i]-.5*height;v[i]=block.getZ()+z[i]-height;
            minU=Math.min(minU,u[i]);minV=Math.min(minV,v[i]);
        }
        double baseU=Math.floor(minU/PERIOD)*PERIOD,baseV=Math.floor(minV/PERIOD)*PERIOD;float[] out=new float[8];
        for(int i=0;i<4;i++){
            // Repeated source tiles provide a wide gutter. Never shrink each block's UVs.
            double su=(.5+(u[i]-baseU)/PERIOD)*TILE+.5;
            double sv=(1.5-(v[i]-baseV)/PERIOD)*TILE-.5;
            if(su<0||su>=FRAME||sv<0||sv>=FRAME)throw new IllegalStateException("OC01 quad exceeds repeated tile gutter");
            out[i*2]=sprite.getU((float)(su/FRAME));out[i*2+1]=sprite.getV((float)(sv/FRAME));
        }return out;
    }
    private static Access access(Object quad)throws ReflectiveOperationException{
        var a=access;if(a!=null)return a;
        Class<?> type=Class.forName("net.caffeinemc.mods.sodium.client.model.quad.ModelQuadViewMutable",true,quad.getClass().getClassLoader());
        a=new Access(type.getMethod("getX",int.class),type.getMethod("getY",int.class),type.getMethod("getZ",int.class),type.getMethod("setTexU",int.class,float.class),type.getMethod("setTexV",int.class,float.class),type.getMethod("getColor",int.class),type.getMethod("getLight",int.class),type.getMethod("getVertexNormal",int.class));access=a;return a;
    }
    public static void sodium(Object quad,BlockPos pos,GeometryProfile profile,TextureAtlasSprite sprite){
        try{
            var a=access(quad);float[] x=new float[4],y=new float[4],z=new float[4];int[] colors=new int[4],lights=new int[4],normals=new int[4];
            for(int i=0;i<4;i++){x[i]=(float)a.x.invoke(quad,i);y[i]=(float)a.y.invoke(quad,i);z[i]=(float)a.z.invoke(quad,i);colors[i]=(int)a.color.invoke(quad,i);lights[i]=(int)a.light.invoke(quad,i);normals[i]=(int)a.normal.invoke(quad,i);}
            var uv=coordinates(pos,x,y,z,profile,sprite);
            for(int i=0;i<4;i++){a.u.invoke(quad,i,uv[i*2]);a.v.invoke(quad,i,uv[i*2+1]);}
            for(int i=0;i<4;i++)if((float)a.x.invoke(quad,i)!=x[i]||(float)a.y.invoke(quad,i)!=y[i]||(float)a.z.invoke(quad,i)!=z[i]||(int)a.color.invoke(quad,i)!=colors[i]||(int)a.light.invoke(quad,i)!=lights[i]||(int)a.normal.invoke(quad,i)!=normals[i]){GEOMETRY_ERRORS.increment();throw new IllegalStateException("OC01 UV update changed non-UV attributes");}
            SODIUM_QUADS.increment();
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Pinned Sodium OC01 quad API changed",error);}
    }
    public static final class Consumer implements VertexConsumer {
        private final VertexConsumer out;private final BlockPos pos;private final GeometryProfile profile;private final TextureAtlasSprite sprite;
        private final float[] x=new float[4],y=new float[4],z=new float[4],nx=new float[4],ny=new float[4],nz=new float[4];
        private final int[] color=new int[4],overlay=new int[4],light=new int[4];private int count;private final boolean ocean;
        public Consumer(VertexConsumer out,BlockPos pos,GeometryProfile profile,TextureAtlasSprite sprite){this(out,pos,profile,sprite,false);}
        public Consumer(VertexConsumer out,BlockPos pos,GeometryProfile profile,TextureAtlasSprite sprite,boolean ocean){this.ocean=ocean;this.out=out;this.pos=pos.immutable();this.profile=profile;this.sprite=sprite;}
        public VertexConsumer addVertex(float px,float py,float pz){if(count>=4)throw new IllegalStateException("Incomplete fluid vertex attributes");x[count]=px-(pos.getX()&15);y[count]=py-(pos.getY()&15);z[count]=pz-(pos.getZ()&15);return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){color[count]=(a<<24)|(r<<16)|(g<<8)|b;return this;}
        public VertexConsumer setUv(float u,float v){return this;}
        public VertexConsumer setUv1(int u,int v){overlay[count]=(v<<16)|u;return this;}
        public VertexConsumer setUv2(int u,int v){light[count]=(v<<16)|u;return this;}
        public VertexConsumer setNormal(float vx,float vy,float vz){nx[count]=vx;ny[count]=vy;nz[count]=vz;if(++count==4)emit();return this;}
        private void emit(){
            if(ocean&&y[0]>.7f&&Math.abs(y[0]-y[1])<.0001&&Math.abs(y[0]-y[2])<.0001&&Math.abs(y[0]-y[3])<.0001){count=0;return;}
            float[] uv=coordinates(pos,x,y,z,profile,sprite);
            for(int i=0;i<4;i++)out.addVertex(x[i]+(pos.getX()&15),y[i]+(pos.getY()&15),z[i]+(pos.getZ()&15)).setColor(color[i]).setUv(uv[i*2],uv[i*2+1]).setOverlay(overlay[i]).setLight(light[i]).setNormal(nx[i],ny[i],nz[i]);
            count=0;VANILLA_QUADS.increment();
        }
        public void finish(){if(count!=0)throw new IllegalStateException("Incomplete OC01 fluid quad");}
    }
    private LowerOceanUv(){}
}
