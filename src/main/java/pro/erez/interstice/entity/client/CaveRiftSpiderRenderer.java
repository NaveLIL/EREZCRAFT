package pro.erez.interstice.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.CaveRiftSpiderEntity;

public final class CaveRiftSpiderRenderer extends MobRenderer<CaveRiftSpiderEntity, CaveRiftSpiderModel> {
    private static final ResourceLocation SKIRMISHER =
            ResourceLocation.fromNamespaceAndPath(Interstice.ID, "textures/entity/spider/cave_rift_spider_skirmisher.png");
    private static final ResourceLocation LURKER =
            ResourceLocation.fromNamespaceAndPath(Interstice.ID, "textures/entity/spider/cave_rift_spider_lurker.png");
    private static final ResourceLocation SPITTER =
            ResourceLocation.fromNamespaceAndPath(Interstice.ID, "textures/entity/spider/cave_rift_spider_spitter.png");

    public CaveRiftSpiderRenderer(EntityRendererProvider.Context context) {
        super(context, new CaveRiftSpiderModel(context.bakeLayer(CaveRiftSpiderModel.LAYER_LOCATION)), 0.7F);
    }

    @Override
    public ResourceLocation getTextureLocation(CaveRiftSpiderEntity entity) {
        return switch (entity.getVariant()) {
            case LURKER -> LURKER;
            case SPITTER -> SPITTER;
            case SKIRMISHER -> SKIRMISHER;
        };
    }

    @Override
    protected void scale(CaveRiftSpiderEntity entity, PoseStack poseStack, float partialTickTime) {
        float s = entity.getVariant().scale;
        poseStack.scale(s, s, s);

        if (entity.isClimbingCeiling()) {
            // Flip upside-down and align feet against ceiling
            poseStack.translate(0.0F, entity.getBbHeight() / s + 0.15F, 0.0F);
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0F));
        }

        // The original bbmodel faces West (-X). Rotate -90 degrees around Y so the spider faces North (-Z, forward).
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90.0F));
    }
}
