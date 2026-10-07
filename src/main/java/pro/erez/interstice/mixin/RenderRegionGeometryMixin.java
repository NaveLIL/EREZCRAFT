package pro.erez.interstice.mixin;

import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.geometry.GeometryView;

@Mixin(RenderChunkRegion.class)
public abstract class RenderRegionGeometryMixin implements GeometryView {
    @Shadow @Final protected Level level;
    @Override public GeometryProfile intersticeGeometry() { return GeometryProfiles.get(level); }
}
