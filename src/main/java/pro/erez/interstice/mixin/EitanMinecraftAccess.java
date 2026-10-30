package pro.erez.interstice.mixin;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Minecraft.class)
public interface EitanMinecraftAccess {
    @Mutable @Accessor("mainRenderTarget") void interstice$setTarget(RenderTarget target);
    @Accessor("cameraEntity") void interstice$setCamera(Entity entity);
}
