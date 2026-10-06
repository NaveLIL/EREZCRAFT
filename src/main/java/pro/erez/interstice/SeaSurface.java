package pro.erez.interstice;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shared geometry for generated liquid, rendering, contact and eye immersion. */
public final class SeaSurface {
    public static final int REFERENCE = 94;
    public static final int MINIMUM = 84;
    public static final int MAXIMUM = 96;
    public static final int CEILING = 97;
    private SeaSurface() {}

    public static double vertexHeight(double x, double z) {
        return vertexHeight(x,z,true);
    }
    public static double vertexHeight(double x,double z,boolean chaotic) {
        if(!chaotic) return 90.0-6.0*Math.cos(x*Math.PI/16.0)*Math.cos(z*Math.PI/20.0);
        double wx=x+4*noise(x/24,z/24,0x71);
        double wz=z+4*noise(x/24,z/24,0x93);
        double broad=Math.cos(wx*Math.PI/16.0)*Math.cos(wz*Math.PI/20.0);
        double detail=noise(wx/11,wz/11,0x37);
        double small=noise(wx/4,wz/4,0xB5);
        // Convex weights preserve the original [84,96] range without hard-cut plateaus.
        return 90.0-6.0*(0.70*broad+0.24*detail+0.06*small);
    }
    private static double random(int x,int z,long salt) {
        long value=x*0x9E3779B97F4A7C15L+z*0xC2B2AE3D27D4EB4FL+salt;
        value=(value^(value>>>30))*0xBF58476D1CE4E5B9L;
        value=(value^(value>>>27))*0x94D049BB133111EBL;
        value^=value>>>31;
        return (value>>>11)*0x1.0p-53*2-1;
    }
    private static double smooth(double t) { return t*t*t*(t*(t*6-15)+10); }
    private static double noise(double x,double z,long salt) {
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
        double fx=smooth(x-bx),fz=smooth(z-bz);
        double a=random(bx,bz,salt),b=random(bx+1,bz,salt);
        double c=random(bx,bz+1,salt),d=random(bx+1,bz+1,salt);
        return (a+(b-a)*fx)*(1-fz)+(c+(d-c)*fx)*fz;
    }

    /** The same diagonal and triangles as the visible mesh, not a separate approximation. */
    public static double heightAt(double x, double z) {
        return heightAt(x,z,true);
    }
    public static double heightAt(double x,double z,boolean chaotic) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        double fx = x - bx, fz = z - bz;
        double h00 = vertexHeight(bx,bz,chaotic),h10=vertexHeight(bx+1,bz,chaotic);
        double h11 = vertexHeight(bx+1,bz+1,chaotic),h01=vertexHeight(bx,bz+1,chaotic);
        return fx >= fz ? h00 + fx * (h10 - h00) + fz * (h11 - h10)
                : h00 + fz * (h01 - h00) + fx * (h11 - h01);
    }

    public static double cellMinimum(int x, int z) {
        return cellMinimum(x,z,true);
    }
    public static double cellMinimum(int x,int z,boolean chaotic) {
        return Math.min(Math.min(vertexHeight(x,z,chaotic),vertexHeight(x+1,z,chaotic)),
                Math.min(vertexHeight(x+1,z+1,chaotic),vertexHeight(x,z+1,chaotic)));
    }

    /** Exact minimum over the intersection of an entity footprint with the two mesh triangles. */
    public static double minimumUnderBox(BlockPos pos, double minX, double maxX, double minZ, double maxZ) {
        return minimumUnderBox(pos,minX,maxX,minZ,maxZ,true);
    }
    public static double minimumUnderBox(BlockPos pos,double minX,double maxX,double minZ,double maxZ,boolean chaotic) {
        double x0 = Math.max(pos.getX(), minX), x1 = Math.min(pos.getX() + 1.0, maxX);
        double z0 = Math.max(pos.getZ(), minZ), z1 = Math.min(pos.getZ() + 1.0, maxZ);
        double result=Math.min(Math.min(heightAt(x0,z0,chaotic),heightAt(x1,z0,chaotic)),
                Math.min(heightAt(x1,z1,chaotic),heightAt(x0,z1,chaotic)));
        double a = Math.max(x0 - pos.getX(), z0 - pos.getZ());
        double b = Math.min(x1 - pos.getX(), z1 - pos.getZ());
        if (a <= b) {
            result=Math.min(result,heightAt(pos.getX()+a,pos.getZ()+a,chaotic));
            result=Math.min(result,heightAt(pos.getX()+b,pos.getZ()+b,chaotic));
        }
        return result;
    }

    public static float thickness(BlockPos pos,boolean chaotic) {
        if(pos.getY()>=MAXIMUM) return 1;
        if(pos.getY()+1<=MINIMUM) return 0;
        return (float)Math.max(0,Math.min(1,pos.getY()+1-cellMinimum(pos.getX(),pos.getZ(),chaotic)));
    }

    public static void fillColumn(Level world, int x, int z) {
        for (int y = (int) Math.floor(cellMinimum(x, z)); y < CEILING; y++) {
            world.setBlock(new BlockPos(x,y,z),Interstice.LIGHT_SEA.get().defaultBlockState().setValue(OceanLiquidBlock.CHAOTIC,true),2);
        }
    }

    /** Bucket ray picking uses a small voxel approximation; immersion and toxin contact use exact triangles. */
    public static VoxelShape pickingShape(BlockPos pos,boolean chaotic) {
        if(pos.getY()>=Math.ceil(Math.max(Math.max(vertexHeight(pos.getX(),pos.getZ(),chaotic),
                vertexHeight(pos.getX()+1,pos.getZ(),chaotic)),Math.max(vertexHeight(pos.getX()+1,pos.getZ()+1,chaotic),
                vertexHeight(pos.getX(),pos.getZ()+1,chaotic))))) return Shapes.block();
        VoxelShape shape = Shapes.empty();
        for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) {
            double bottom=Math.max(0,heightAt(pos.getX()+(x+0.5)/4,pos.getZ()+(z+0.5)/4,chaotic)-pos.getY());
            if (bottom < 1) shape = Shapes.or(shape, Shapes.box(x / 4.0, bottom, z / 4.0, (x + 1) / 4.0, 1, (z + 1) / 4.0));
        }
        return shape;
    }
}
