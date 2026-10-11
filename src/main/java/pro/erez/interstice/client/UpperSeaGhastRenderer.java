package pro.erez.interstice.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.fauna.UpperSeaGhast;

public final class UpperSeaGhastRenderer extends MobRenderer<UpperSeaGhast, UpperSeaGhastModel> {
    private static final ResourceLocation SHELL = texture("block/minerals/rift_shale.png");
    private static final ResourceLocation SACS = texture("block/minerals/vitriolite.png");
    public UpperSeaGhastRenderer(EntityRendererProvider.Context context) {
        super(context, new UpperSeaGhastModel(context.bakeLayer(UpperSeaGhastModel.LAYER)), 1.0F);
        addLayer(new Details(this));
    }
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath(Interstice.ID, "textures/" + path); }
    @Override public ResourceLocation getTextureLocation(UpperSeaGhast entity) { return SHELL; }
    @Override protected void scale(UpperSeaGhast entity, PoseStack poses, float partialTick) { poses.scale(1.65F, 1.65F, 1.65F); }
    private static final class Details extends RenderLayer<UpperSeaGhast, UpperSeaGhastModel> {
        Details(UpperSeaGhastRenderer renderer) { super(renderer); }
        @Override public void render(PoseStack poses, MultiBufferSource buffers, int light, UpperSeaGhast entity,
                                     float limbSwing, float limbSwingAmount, float partialTick, float age,
                                     float yaw, float pitch) {
            if (entity.isInvisible()) return;
            int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);
            getParentModel().renderSacs(poses, buffers.getBuffer(RenderType.entityCutoutNoCull(SACS)), light, overlay, 0xFFC5D985);
            getParentModel().renderEyes(poses, buffers.getBuffer(RenderType.entityCutoutNoCull(SHELL)), light, overlay,
                    entity.isCharging() ? 0xFFF0E7A0 : 0xFF233334);
        }
    }
}
