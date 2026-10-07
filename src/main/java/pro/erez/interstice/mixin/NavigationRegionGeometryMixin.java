package pro.erez.interstice.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.geometry.GeometryView;

@Mixin(PathNavigationRegion.class)
public abstract class NavigationRegionGeometryMixin implements GeometryView {
    @Shadow @Final protected Level level;
    @Override public GeometryProfile intersticeGeometry() { return GeometryProfiles.get(level); }
}
