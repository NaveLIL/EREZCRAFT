package pro.erez.interstice.mixin;
import com.mojang.blaze3d.pipeline.RenderTarget;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(LevelRenderer.class)
public interface EitanLevelRendererAccess {
    @Accessor("visibleSections") ObjectArrayList<SectionRenderDispatcher.RenderSection> interstice$sections();
    @Accessor("translucentTarget") RenderTarget interstice$translucent();
    @Accessor("translucentTarget") void interstice$translucent(RenderTarget value);
    @Accessor("itemEntityTarget") RenderTarget interstice$items();
    @Accessor("itemEntityTarget") void interstice$items(RenderTarget value);
    @Accessor("weatherTarget") RenderTarget interstice$weather();
    @Accessor("weatherTarget") void interstice$weather(RenderTarget value);
    @Accessor("particlesTarget") RenderTarget interstice$particles();
    @Accessor("particlesTarget") void interstice$particles(RenderTarget value);
    @Accessor("cloudsTarget") RenderTarget interstice$clouds();
    @Accessor("cloudsTarget") void interstice$clouds(RenderTarget value);
    @Accessor("transparencyChain") PostChain interstice$transparency();
    @Accessor("transparencyChain") void interstice$transparency(PostChain value);
}
