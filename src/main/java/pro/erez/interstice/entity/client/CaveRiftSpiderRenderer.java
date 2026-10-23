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
    protected void setupRotations(CaveRiftSpiderEntity entity, PoseStack poseStack, float age,
                                  float bodyYaw, float partialTick, float scale) {
        var rotation=pro.erez.interstice.entity.SpiderSurfaceFrame.rotation(entity.previousSurface(),entity.getSurface(),entity.surfaceTransition(partialTick));
        if(entity.getSurface()!=net.minecraft.core.Direction.DOWN||entity.previousSurface()!=net.minecraft.core.Direction.DOWN){
            // Ground yaw is changed by look/attack controls even during a purely
            // vertical climb. Use the server-synced world tangent instead.
            var forward=entity.getOldCrawlForward().lerp(entity.getCrawlForward(),partialTick);
            bodyYaw=pro.erez.interstice.entity.SpiderSurfaceFrame.bodyYaw(rotation,forward,bodyYaw);
        }
        float center = entity.getBbHeight() / (2*scale);
        poseStack.translate(0, center, 0);
        poseStack.mulPose(rotation);
        poseStack.translate(0, -center, 0);
        super.setupRotations(entity, poseStack, age, bodyYaw, partialTick, scale);
    }

    @Override
    protected void scale(CaveRiftSpiderEntity entity, PoseStack poseStack, float partialTickTime) {
        float s = entity.getVariant().scale;
        poseStack.scale(s, s, s);

        // The original bbmodel faces West (-X). Rotate -90 degrees around Y so the spider faces North (-Z, forward).
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90.0F));
    }
}
