package pro.erez.interstice;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import pro.erez.interstice.geometry.GeometryProfiles;

public final class FluidContact {
    private FluidContact() {}
    public static double overlap(BlockGetter world, BlockPos pos, FluidState fluid, AABB box) {
        double height = fluid.getHeight(world, pos);
        double bottom = pos.getY() + (fluid.getType() instanceof LightFluid ? 1.0 - height : 0);
        double top = pos.getY() + (fluid.getType() instanceof LightFluid ? 1.0 : height);
        if (box.maxX <= pos.getX() || box.minX >= pos.getX() + 1
                || box.maxZ <= pos.getZ() || box.minZ >= pos.getZ() + 1) return 0;
        if (world.getBlockState(pos).getBlock() instanceof OceanLiquidBlock) {
            bottom=Math.max(pos.getY(),SeaSurface.minimumUnderBox(GeometryProfiles.get(world),pos,box.minX,box.maxX,box.minZ,box.maxZ,world.getBlockState(pos).getValue(OceanLiquidBlock.CHAOTIC)));
            top = pos.getY() + 1;
        }
        return Math.max(0, Math.min(box.maxY, top) - Math.max(box.minY, bottom));
    }
    public static double lightOverlap(BlockGetter world, AABB box) {
        double depth = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(box.minX); x < Mth.ceil(box.maxX); x++) {
            for (int y = Mth.floor(box.minY); y < Mth.ceil(box.maxY); y++) {
                for (int z = Mth.floor(box.minZ); z < Mth.ceil(box.maxZ); z++) {
                    pos.set(x, y, z);
                    FluidState fluid = world.getFluidState(pos);
                    if (fluid.getType() instanceof LightFluid) depth = Math.max(depth, overlap(world, pos, fluid, box));
                }
            }
        }
        return depth;
    }
    public static boolean pointInLight(BlockGetter world, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        FluidState fluid = world.getFluidState(pos);
        if (world.getBlockState(pos).getBlock() instanceof OceanLiquidBlock) return y>=SeaSurface.heightAt(GeometryProfiles.get(world),x,z,world.getBlockState(pos).getValue(OceanLiquidBlock.CHAOTIC));
        return fluid.getType() instanceof LightFluid && y >= pos.getY() + 1.0 - fluid.getHeight(world, pos);
    }
}
