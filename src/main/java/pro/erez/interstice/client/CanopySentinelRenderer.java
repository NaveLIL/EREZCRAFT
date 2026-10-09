package pro.erez.interstice.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.fauna.CanopySentinel;

/** Reuses accepted crown bark, gloomcrown bark, and crown foliage tiles directly. */
@OnlyIn(Dist.CLIENT)
public final class CanopySentinelRenderer extends MobRenderer<CanopySentinel, CanopySentinelModel> {
    private static final ResourceLocation BARK = texture("block/garden/crown_log.png");
    private static final ResourceLocation DARK_BARK = texture("block/gloomcrown_log.png");
    private static final ResourceLocation LEAVES = texture("block/garden/crown_leaves.png");

    public CanopySentinelRenderer(EntityRendererProvider.Context context) {
        super(context, new CanopySentinelModel(context.bakeLayer(CanopySentinelModel.LAYER_LOCATION)), 0.3F);
        addLayer(new LeafTailLayer(this));
        addLayer(new BarkEyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(CanopySentinel entity) {
        return BARK;
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(Interstice.ID, "textures/" + path);
    }

    /** Cutout keeps the holes in the existing foliage; warning does not add emitted light. */
    private static final class LeafTailLayer extends RenderLayer<CanopySentinel, CanopySentinelModel> {
        private LeafTailLayer(CanopySentinelRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poses, MultiBufferSource buffers, int light, CanopySentinel entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (entity.isInvisible()) return;
            getParentModel().renderLeaves(poses, buffers.getBuffer(RenderType.entityCutoutNoCull(LEAVES)),
                    light, LivingEntityRenderer.getOverlayCoords(entity, 0.0F), -1);
        }
    }

    /** Tiny cubical eyes use the accepted dark bark tile with a dark vertex tint. */
    private static final class BarkEyesLayer extends RenderLayer<CanopySentinel, CanopySentinelModel> {
        private BarkEyesLayer(CanopySentinelRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poses, MultiBufferSource buffers, int light, CanopySentinel entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (entity.isInvisible()) return;
            getParentModel().renderEyes(poses, buffers.getBuffer(RenderType.entityCutoutNoCull(DARK_BARK)),
                    light, LivingEntityRenderer.getOverlayCoords(entity, 0.0F), 0xFF42484B);
        }
    }
}
