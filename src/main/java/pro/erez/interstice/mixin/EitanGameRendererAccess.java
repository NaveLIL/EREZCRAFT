package pro.erez.interstice.mixin;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(GameRenderer.class)
public interface EitanGameRendererAccess {
    @Accessor("renderHand") boolean interstice$getHand();
    @Accessor("renderBlockOutline") boolean interstice$getOutline();
    @Accessor("panoramicMode") boolean interstice$getPanoramic();
}
