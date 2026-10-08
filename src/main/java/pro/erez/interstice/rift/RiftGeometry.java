package pro.erez.interstice.rift;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import pro.erez.interstice.Interstice;

/** The ten-block, corner-optional 4x5 frame has a 2x3 interior, in either horizontal axis. */
public final class RiftGeometry {
    private RiftGeometry() {}
    public record Frame(BlockPos origin, Direction.Axis axis) {
        public Frame { origin = origin.immutable(); }
        public BlockPos at(int width, int height) { return axis == Direction.Axis.X ? origin.offset(width, height, 0) : origin.offset(0, height, width); }
        public RiftLinks.Endpoint endpoint(ServerLevel level) { return new RiftLinks.Endpoint(level.dimension(), origin, axis); }
    }
    public static boolean load(ServerLevel level, Frame frame) {
        BlockPos low = frame.at(-1, -1), high = frame.at(2, 3);
        if (level.isOutsideBuildHeight(low) || level.isOutsideBuildHeight(high)
                || !level.getWorldBorder().isWithinBounds(low) || !level.getWorldBorder().isWithinBounds(high)) return false;
        for (int cx = Math.min(low.getX(), high.getX()) >> 4; cx <= (Math.max(low.getX(), high.getX()) >> 4); cx++)
            for (int cz = Math.min(low.getZ(), high.getZ()) >> 4; cz <= (Math.max(low.getZ(), high.getZ()) >> 4); cz++) level.getChunk(cx, cz);
        return true;
    }
    public static boolean valid(ServerLevel level, Frame frame, boolean active) {
        if (frame.axis() == Direction.Axis.Y) return false;
        for (int w = -1; w <= 2; w++) for (int y = -1; y <= 3; y++) {
            if ((w == -1 || w == 2) && (y == -1 || y == 3)) continue;
            BlockPos pos = frame.at(w, y);
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (w == -1 || w == 2 || y == -1 || y == 3) {
                if (!state.is(Interstice.RIFT_FRAME.get())) return false;
            } else {
                boolean portal = state.is(Interstice.RIFT_PORTAL.get()) && state.getValue(RiftPortalBlock.AXIS) == frame.axis();
                if (!portal && (active || !state.isAir())) return false;
            }
        }
        return true;
    }
    public static Frame atPortal(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        if (!state.is(Interstice.RIFT_PORTAL.get())) return null;
        Direction.Axis axis = state.getValue(RiftPortalBlock.AXIS);
        Direction width = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        BlockPos origin = cell;
        for (int i = 0; i < 2 && level.getBlockState(origin.below()).is(Interstice.RIFT_PORTAL.get()); i++) origin = origin.below();
        if (level.getBlockState(origin.relative(width.getOpposite())).is(Interstice.RIFT_PORTAL.get())) origin = origin.relative(width.getOpposite());
        Frame frame = new Frame(origin, axis);
        return valid(level, frame, true) ? frame : null;
    }
    public static Frame activate(ServerLevel level, BlockPos clicked) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            Direction width = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
            for (int w = -2; w <= 1; w++) for (int y = -3; y <= 1; y++) {
                Frame frame = new Frame(clicked.relative(width, w).above(y), axis);
                if (!valid(level, frame, false)) continue;
                for (int x = 0; x < 2; x++) for (int h = 0; h < 3; h++) level.setBlock(frame.at(x, h), Interstice.RIFT_PORTAL.get().defaultBlockState().setValue(RiftPortalBlock.AXIS, axis), 3);
                return frame;
            }
        }
        return null;
    }
}
