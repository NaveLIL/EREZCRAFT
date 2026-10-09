package pro.erez.interstice.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import pro.erez.interstice.entity.ToxinSpitEntity;

public final class ToxinSpitRenderer extends EntityRenderer<ToxinSpitEntity> {
    public ToxinSpitRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ToxinSpitEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(ToxinSpitEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0, 0.1, 0.0);
        float scale = 0.22F;
        poseStack.scale(scale, scale, scale);

        float angle = (entity.tickCount + partialTick) * 20.0F;
        poseStack.mulPose(new Quaternionf().rotateY(Mth.DEG_TO_RAD * angle));
        poseStack.mulPose(new Quaternionf().rotateX(Mth.DEG_TO_RAD * angle * 0.7F));

        VertexConsumer consumer = buffer.getBuffer(RenderType.translucent());
        PoseStack.Pose pose = poseStack.last();

        float r = 0.20F, g = 0.95F, b = 0.78F, a = 0.85F;
        float h = 0.5F;

        quad(consumer, pose, packedLight, -h, -h, h,  h, -h, h,  h, h, h,  -h, h, h,  0, 0, 1, r, g, b, a);
        quad(consumer, pose, packedLight,  h, -h, -h, -h, -h, -h, -h, h, -h,  h, h, -h,  0, 0, -1, r, g, b, a);
        quad(consumer, pose, packedLight,  h, -h, h,  h, -h, -h,  h, h, -h,  h, h, h,  1, 0, 0, r, g, b, a);
        quad(consumer, pose, packedLight, -h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h, -1, 0, 0, r, g, b, a);
        quad(consumer, pose, packedLight, -h,  h, h,  h,  h, h,  h, h, -h, -h,  h, -h,  0, 1, 0, r, g, b, a);
        quad(consumer, pose, packedLight, -h, -h, -h,  h, -h, -h,  h, -h, h, -h, -h, h,  0, -1, 0, r, g, b, a);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose, int light,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz, float r, float g, float b, float a) {
        vertex(out, pose, light, x0, y0, z0, 0, 0, nx, ny, nz, r, g, b, a);
        vertex(out, pose, light, x1, y1, z1, 1, 0, nx, ny, nz, r, g, b, a);
        vertex(out, pose, light, x2, y2, z2, 1, 1, nx, ny, nz, r, g, b, a);
        vertex(out, pose, light, x3, y3, z3, 0, 1, nx, ny, nz, r, g, b, a);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, int light,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, float r, float g, float b, float a) {
        out.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
