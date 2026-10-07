package pro.erez.interstice.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.geometry.GeometryView;

@Mixin(ClientLevel.class)
public abstract class ClientLevelGeometryMixin implements GeometryView {
    @Shadow @Final private ClientPacketListener connection;
    @Unique private GeometryProfile interstice$geometry = GeometryProfile.LEGACY;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void interstice$bindGeometry(CallbackInfo callback) {
        ClientLevel level = (ClientLevel) (Object) this;
        interstice$geometry = GeometryProfiles.forClientLevel(connection.getConnection(), level.dimension(), level);
    }
    @Override public GeometryProfile intersticeGeometry() { return interstice$geometry; }
}
