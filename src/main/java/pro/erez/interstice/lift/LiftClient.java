package pro.erez.interstice.lift;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.GardenMaterials;

@EventBusSubscriber(modid = Interstice.ID, value = Dist.CLIENT)
public final class LiftClient {
    private LiftClient() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerEntityRenderer(RealmLift.LIFT.get(), Renderer::new); }
    private static final class Renderer extends EntityRenderer<FieldLiftEntity> {
        private Renderer(EntityRendererProvider.Context context) { super(context); shadowRadius = .8F; }
        @Override public ResourceLocation getTextureLocation(FieldLiftEntity entity) { return TextureAtlas.LOCATION_BLOCKS; }
        private void part(PoseStack pose, MultiBufferSource buffers, int light, BlockState material, double x, double y, double z, float sx, float sy, float sz) {
            pose.pushPose(); pose.translate(x, y, z); pose.scale(sx, sy, sz);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(material, pose, buffers, light, OverlayTexture.NO_OVERLAY); pose.popPose();
        }
        @Override public void render(FieldLiftEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            part(pose, buffers, light, GardenMaterials.PALEHEART_PLANKS.get().defaultBlockState(), -.75, 0, -.75, 1.5F, .35F, 1.5F);
            for (double x : new double[]{-.68, .56}) for (double z : new double[]{-.68, .56})
                part(pose, buffers, light, GardenMaterials.PALESTONE_BRICKS.get().defaultBlockState(), x, .35, z, .12F, .45F, .12F);
            part(pose, buffers, light, GardenMaterials.PALESTONE_BRICKS.get().defaultBlockState(), -.68, .7, -.68, 1.36F, .1F, .12F);
            double cable = entity.anchorId() == null ? 0 : Math.clamp(entity.getY() - entity.anchorPos().getY() - 1, 0, RealmLift.MAX_TRAVEL);
            // One-block segments retain the existing texture's density along the whole short field guide.
            for (double length = 0; length < cable; length++)
                part(pose, buffers, light, GardenMaterials.PALESTONE_BRICKS.get().defaultBlockState(), .62, -Math.min(length + 1, cable), .62,
                        .035F, (float)Math.min(1, cable - length), .035F);
            super.render(entity, yaw, partialTick, pose, buffers, light);
        }
    }
}
