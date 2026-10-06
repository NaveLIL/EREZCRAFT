package pro.erez.interstice.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import org.joml.Vector3f;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.LightFluid;
import pro.erez.interstice.OceanLiquidBlock;
import pro.erez.interstice.SeaSurface;
import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class FluidClient {
    @SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new Appearance(true), Interstice.LIGHT_TYPE.get());
        event.registerFluidType(new Appearance(false), Interstice.HEAVY_TYPE.get());
    }
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(Interstice.LIGHT.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(Interstice.LIGHT_FLOW.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(Interstice.HEAVY.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(Interstice.HEAVY_FLOW.get(), RenderType.translucent());
        });
    }
    private static final class Appearance implements IClientFluidTypeExtensions {
        private final boolean light;
        Appearance(boolean light) { this.light = light; }
        @Override public ResourceLocation getStillTexture() { return ResourceLocation.fromNamespaceAndPath(Interstice.ID, light ? "block/light_still_v3" : "block/heavy_still_v2"); }
        @Override public ResourceLocation getFlowingTexture() { return ResourceLocation.fromNamespaceAndPath(Interstice.ID, light ? "block/light_flow_v3" : "block/heavy_flow_v2"); }
        @Override public int getTintColor() { return light ? 0xD8FFFFFF : 0xFFFFFFFF; }
        @Override public Vector3f modifyFogColor(Camera camera, float partial, ClientLevel world, int distance, float darkness, Vector3f color) {
            return light ? new Vector3f(0.70F, 0.69F, 0.47F) : new Vector3f(0.23F, 0.02F, 0.055F);
        }
        @Override public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float distance, float partial, float near, float far, FogShape shape) {
            RenderSystem.setShaderFogStart(0);
            RenderSystem.setShaderFogEnd(light ? 5 : 3);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
        }
        @Override public boolean renderFluid(FluidState state, BlockAndTintGetter world, BlockPos pos, VertexConsumer consumer, BlockState block) {
            if (!light) return false;
            if (block.getBlock() instanceof OceanLiquidBlock) renderOcean(world, pos, consumer, this);
            else renderCeilingFluid(state, world, pos, consumer, this);
            return true;
        }
    }
    /** Shared corner heights and mirrored UVs keep adjoining liquid voxels visually continuous. */
    private static void renderCeilingFluid(FluidState state, BlockAndTintGetter world, BlockPos pos, VertexConsumer out, Appearance appearance) {
        float x = pos.getX() & 15, y = pos.getY() & 15, z = pos.getZ() & 15;
        float b00 = 1 - cornerThickness(world, pos, 0, 0);
        float b10 = 1 - cornerThickness(world, pos, 1, 0);
        float b11 = 1 - cornerThickness(world, pos, 1, 1);
        float b01 = 1 - cornerThickness(world, pos, 0, 1);
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite still = atlas.apply(appearance.getStillTexture());
        TextureAtlasSprite flow = atlas.apply(appearance.getFlowingTexture());
        int light = LightTexture.FULL_BRIGHT;
        int color = appearance.getTintColor();
        if (visible(world, pos.above())) {
            quad(out, still, color, light, pos, 0, 1, 0,
                    x,y+1,z, x,y+1,z+1, x+1,y+1,z+1, x+1,y+1,z);
        }
        if (visible(world, pos.below()) || Math.max(Math.max(b00,b10),Math.max(b11,b01)) > 0) {
            quad(out, still, color, light, pos, 0, -1, 0,
                    x,y+b00,z, x+1,y+b10,z, x+1,y+b11,z+1, x,y+b01,z+1);
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(side);
            // Shared smooth underside closes the volume. Never draw walls inside the same liquid.
            if (world.getFluidState(neighbor).getType() instanceof LightFluid
                    || world.getBlockState(neighbor).isSolidRender(world, neighbor)) continue;
            switch (side) {
                case NORTH -> quad(out, flow, color, light, pos, 0,0,-1, x,y+b00,z, x,y+1,z, x+1,y+1,z, x+1,y+b10,z);
                case SOUTH -> quad(out, flow, color, light, pos, 0,0,1, x+1,y+b11,z+1, x+1,y+1,z+1, x,y+1,z+1, x,y+b01,z+1);
                case WEST -> quad(out, flow, color, light, pos, -1,0,0, x,y+b01,z+1, x,y+1,z+1, x,y+1,z, x,y+b00,z);
                case EAST -> quad(out, flow, color, light, pos, 1,0,0, x+1,y+b10,z, x+1,y+1,z, x+1,y+1,z+1, x+1,y+b11,z+1);
                default -> {}
            }
        }
    }
    private static float cornerThickness(BlockAndTintGetter world, BlockPos pos, int cornerX, int cornerZ) {
        float sum = 0, weight = 0;
        for (int dx=cornerX-1;dx<=cornerX;dx++) for (int dz=cornerZ-1;dz<=cornerZ;dz++) {
            BlockPos sample=pos.offset(dx,0,dz);
            FluidState fluid=world.getFluidState(sample);
            if (fluid.getType() instanceof LightFluid) {
                float thickness=fluid.getHeight(world,sample);
                if (thickness>=1) return 1;
                float w=thickness>=0.8F ? 10 : 1;
                sum+=thickness*w;weight+=w;
            } else if (!world.getBlockState(sample).isSolidRender(world,sample)) {
                weight+=1;
            }
        }
        return weight==0 ? 0 : sum/weight;
    }
    private static boolean visible(BlockAndTintGetter world, BlockPos pos) {
        return !(world.getFluidState(pos).getType() instanceof LightFluid) && !world.getBlockState(pos).isSolidRender(world, pos);
    }
    private record Point(double x, double y, double z) {
        Point mix(Point other, double t) { return new Point(x+(other.x-x)*t,y+(other.y-y)*t,z+(other.z-z)*t); }
    }
    private static void renderOcean(BlockAndTintGetter world, BlockPos pos, VertexConsumer out, Appearance appearance) {
        if(pos.getY()>=SeaSurface.MAXIMUM && !visible(world,pos.above())) {
            boolean exposed=false;
            for(Direction side:Direction.Plane.HORIZONTAL) if(visible(world,pos.relative(side))) {exposed=true;break;}
            if(!exposed) return;
        }
        var atlas=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        var still=atlas.apply(appearance.getStillTexture());
        var flow=atlas.apply(appearance.getFlowingTexture());
        int light=LightTexture.FULL_BRIGHT, color=appearance.getTintColor();
        int x=pos.getX(),z=pos.getZ();
        boolean chaotic=world.getBlockState(pos).getValue(OceanLiquidBlock.CHAOTIC);
        Point a=new Point(x,SeaSurface.vertexHeight(x,z,chaotic),z);
        Point b=new Point(x+1,SeaSurface.vertexHeight(x+1,z,chaotic),z);
        Point c=new Point(x+1,SeaSurface.vertexHeight(x+1,z+1,chaotic),z+1);
        Point d=new Point(x,SeaSurface.vertexHeight(x,z+1,chaotic),z+1);
        // Clip each real surface triangle into the occupied voxel. No oversized shader-only hills.
        double min=Math.min(Math.min(a.y,b.y),Math.min(c.y,d.y));
        double max=Math.max(Math.max(a.y,b.y),Math.max(c.y,d.y));
        if(pos.getY()<=max && pos.getY()+1>=min) {
            oceanPolygon(out,still,color,light,pos,List.of(a,b,c),0,-1,0,true,chaotic);
            oceanPolygon(out,still,color,light,pos,List.of(a,c,d),0,-1,0,true,chaotic);
        }
        if(visible(world,pos.above())) {
            double top=pos.getY()+1;
            oceanPolygon(out,still,color,light,pos,List.of(new Point(x,top,z),new Point(x,top,z+1),new Point(x+1,top,z+1),new Point(x+1,top,z)),0,1,0,false,chaotic);
        }
        for(Direction side:Direction.Plane.HORIZONTAL) {
            if(!visible(world,pos.relative(side))) continue;
            Point left,right;
            switch(side) {
                case NORTH -> {left=a;right=b;}
                case SOUTH -> {left=c;right=d;}
                case WEST -> {left=d;right=a;}
                case EAST -> {left=b;right=c;}
                default -> throw new IllegalStateException();
            }
            double top=pos.getY()+1;
            oceanPolygon(out,flow,color,light,pos,List.of(left,new Point(left.x,top,left.z),new Point(right.x,top,right.z),right),side.getStepX(),0,side.getStepZ(),false,chaotic);
        }
    }
    private static List<Point> clipY(List<Point> polygon,double plane,boolean above) {
        List<Point> clipped=new ArrayList<>();
        if(polygon.isEmpty()) return clipped;
        Point previous=polygon.getLast();
        boolean previousIn=above ? previous.y>=plane : previous.y<=plane;
        for(Point current:polygon) {
            boolean currentIn=above ? current.y>=plane : current.y<=plane;
            if(currentIn!=previousIn) clipped.add(previous.mix(current,(plane-previous.y)/(current.y-previous.y)));
            if(currentIn) clipped.add(current);
            previous=current;previousIn=currentIn;
        }
        return clipped;
    }
    private static void oceanPolygon(VertexConsumer out,TextureAtlasSprite sprite,int color,int light,BlockPos pos,List<Point> polygon,float nx,float ny,float nz,boolean surface,boolean chaotic) {
        polygon=clipY(clipY(polygon,pos.getY(),true),pos.getY()+1,false);
        if(polygon.size()<3) return;
        for(int i=1;i+1<polygon.size();i++) {
            Point a=polygon.getFirst(),b=polygon.get(i),c=polygon.get(i+1);
            double ux=b.x-a.x,uy=b.y-a.y,uz=b.z-a.z,vx=c.x-a.x,vy=c.y-a.y,vz=c.z-a.z;
            double cx=uy*vz-uz*vy,cy=uz*vx-ux*vz,cz=ux*vy-uy*vx;
            if(cx*cx+cy*cy+cz*cz<1e-12) continue;
            float[] xyz=new float[12];
            Point[] points={a,b,c,c}; // Degenerate quad carries one triangle in the chunk's QUADS format.
            for(int j=0;j<4;j++) {
                xyz[j*3]=(float)(points[j].x-pos.getX()+(pos.getX()&15));
                xyz[j*3+1]=(float)(points[j].y-pos.getY()+(pos.getY()&15));
                xyz[j*3+2]=(float)(points[j].z-pos.getZ()+(pos.getZ()&15));
            }
            emitQuad(out,sprite,color,light,pos,nx,ny,nz,xyz,surface,chaotic);
        }
    }
    private static float mirrored(int cell,float fraction,int period) {
        float coordinate=(Math.floorMod(cell,period)+fraction)/period;
        return (Math.floorDiv(cell,period)&1)==0 ? coordinate : 1-coordinate;
    }
    private static void quad(VertexConsumer out, TextureAtlasSprite sprite, int color, int light, BlockPos pos, float nx, float ny, float nz, float... xyz) {
        emitQuad(out,sprite,color,light,pos,nx,ny,nz,xyz,false,false);
    }
    private static void emitQuad(VertexConsumer out,TextureAtlasSprite sprite,int color,int light,BlockPos pos,float nx,float ny,float nz,float[] xyz,boolean oceanSurface,boolean chaotic) {
        // 64px across four blocks, or 128px across eight: still 16 texels per block.
        int period=ny==0 ? 8 : 4;
        float insetU=0.5F/sprite.contents().width();
        float insetV=0.5F/sprite.contents().height();
        float lx=pos.getX()&15, ly=pos.getY()&15, lz=pos.getZ()&15;
        float[] u=new float[4],v=new float[4];
        for(int i=0;i<4;i++) {
            float fx=xyz[i*3]-lx, fy=xyz[i*3+1]-ly, fz=xyz[i*3+2]-lz;
            float tu=ny!=0 || nx==0 ? mirrored(pos.getX(),fx,period) : mirrored(pos.getZ(),fz,period);
            float tv=ny!=0 ? mirrored(pos.getZ(),fz,period) : mirrored(pos.getY(),fy,period);
            // Exact matching edge texels, with half-texel inset to prevent atlas bleeding.
            u[i]=sprite.getU(insetU+tu*(1-2*insetU));
            v[i]=sprite.getV(insetV+tv*(1-2*insetV));
        }
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 4; i++) {
                int p = side == 0 ? i : 3 - i;
                float shade=1;
                if(oceanSurface) {
                    double wx=pos.getX()+xyz[p*3]-lx,wz=pos.getZ()+xyz[p*3+2]-lz;
                    double dx=(SeaSurface.vertexHeight(wx+0.15,wz,chaotic)-SeaSurface.vertexHeight(wx-0.15,wz,chaotic))/0.3;
                    double dz=(SeaSurface.vertexHeight(wx,wz+0.15,chaotic)-SeaSurface.vertexHeight(wx,wz-0.15,chaotic))/0.3;
                    double lighting=(0.32*dx+0.85+0.42*dz)/Math.sqrt(dx*dx+1+dz*dz);
                    shade=(float)(0.75+0.25*Math.max(0,Math.min(1,lighting)));
                }
                out.addVertex(xyz[p*3], xyz[p*3+1], xyz[p*3+2])
                        .setColor((int)(((color >> 16) & 255)*shade),(int)(((color >> 8) & 255)*shade),(int)((color & 255)*shade),(color >>> 24) & 255)
                        .setUv(u[p], v[p]).setLight(light).setNormal(side == 0 ? nx : -nx, side == 0 ? ny : -ny, side == 0 ? nz : -nz);
            }
        }
    }
}
