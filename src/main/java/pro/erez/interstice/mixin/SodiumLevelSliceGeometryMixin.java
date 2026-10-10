package pro.erez.interstice.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.geometry.GeometryView;

/** Bind worker render slices to their own dimension, just like vanilla render regions. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class SodiumLevelSliceGeometryMixin implements GeometryView {
    @Shadow @Final private ClientLevel level;

    @Override
    public GeometryProfile intersticeGeometry() {
        return GeometryProfiles.get(level);
    }
}
