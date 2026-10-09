package pro.erez.interstice.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import pro.erez.interstice.entity.EchoRiftEntity;

public final class EchoRiftRenderer extends EntityRenderer<EchoRiftEntity> {

    public EchoRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(EchoRiftEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(EchoRiftEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float time = entity.tickCount + partialTick;
        float stability = entity.getStability();

        poseStack.pushPose();
        poseStack.translate(0.0, 0.8, 0.0);

        // Core singularity scale pulsation
        float pulse = 0.35F + 0.05F * Mth.sin(time * 0.12F);
        pulse *= (0.5F + 0.5F * stability);

        VertexConsumer consumer = buffer.getBuffer(RenderType.debugQuads());

        // 1. Draw glowing inner core octahedron
        poseStack.pushPose();
        poseStack.scale(pulse, pulse, pulse);
        poseStack.mulPose(new Quaternionf().rotateY(Mth.DEG_TO_RAD * time * 4.0F));
        poseStack.mulPose(new Quaternionf().rotateX(Mth.DEG_TO_RAD * time * 2.5F));
        renderOctahedron(consumer, poseStack.last(), 0.85F, 0.60F, 1.0F, 0.85F * stability);
        poseStack.popPose();

        // 2. Draw outer rotating temporal ring 1
        poseStack.pushPose();
        float ring1Scale = 0.75F * (0.8F + 0.2F * stability);
        poseStack.scale(ring1Scale, ring1Scale, ring1Scale);
        poseStack.mulPose(new Quaternionf().rotateZ(Mth.DEG_TO_RAD * 35.0F));
        poseStack.mulPose(new Quaternionf().rotateY(Mth.DEG_TO_RAD * time * 3.0F));
        renderRing(consumer, poseStack.last(), 1.4F, 0.08F, 0.35F, 0.85F, 0.95F, 0.65F * stability);
        poseStack.popPose();

        // 3. Draw outer rotating temporal ring 2 (counter-rotating)
        poseStack.pushPose();
        float ring2Scale = 0.95F * (0.8F + 0.2F * stability);
        poseStack.scale(ring2Scale, ring2Scale, ring2Scale);
        poseStack.mulPose(new Quaternionf().rotateX(Mth.DEG_TO_RAD * 45.0F));
        poseStack.mulPose(new Quaternionf().rotateY(Mth.DEG_TO_RAD * -time * 2.2F));
        renderRing(consumer, poseStack.last(), 1.6F, 0.06F, 0.70F, 0.25F, 0.95F, 0.55F * stability);
        poseStack.popPose();

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void renderOctahedron(VertexConsumer consumer, PoseStack.Pose pose, float r, float g, float b, float a) {
        float s = 1.0F;
        // Top pyramid
        triangle(consumer, pose, 0, s, 0,  -s, 0, -s,   s, 0, -s, r, g, b, a);
        triangle(consumer, pose, 0, s, 0,   s, 0, -s,   s, 0,  s, r, g, b, a);
        triangle(consumer, pose, 0, s, 0,   s, 0,  s,  -s, 0,  s, r, g, b, a);
        triangle(consumer, pose, 0, s, 0,  -s, 0,  s,  -s, 0, -s, r, g, b, a);
        // Bottom pyramid
        triangle(consumer, pose, 0, -s, 0,   s, 0, -s,  -s, 0, -s, r, g, b, a);
        triangle(consumer, pose, 0, -s, 0,   s, 0,  s,   s, 0, -s, r, g, b, a);
        triangle(consumer, pose, 0, -s, 0,  -s, 0,  s,   s, 0,  s, r, g, b, a);
        triangle(consumer, pose, 0, -s, 0,  -s, 0, -s,  -s, 0,  s, r, g, b, a);
    }

    private static void renderRing(VertexConsumer consumer, PoseStack.Pose pose, float radius, float thickness, float r, float g, float b, float a) {
        int segments = 16;
        float angleStep = (2 * (float) Math.PI) / segments;

        for (int i = 0; i < segments; i++) {
            float a1 = i * angleStep;
            float a2 = (i + 1) * angleStep;

            float x1 = Mth.cos(a1) * radius;
            float z1 = Mth.sin(a1) * radius;
            float x2 = Mth.cos(a2) * radius;
            float z2 = Mth.sin(a2) * radius;

            quad(consumer, pose, x1, -thickness, z1, x2, -thickness, z2, x2, thickness, z2, x1, thickness, z1, r, g, b, a);
        }
    }

    private static void triangle(VertexConsumer out, PoseStack.Pose pose,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float r, float g, float b, float a) {
        vertex(out, pose, x1, y1, z1, r, g, b, a);
        vertex(out, pose, x2, y2, z2, r, g, b, a);
        vertex(out, pose, x3, y3, z3, r, g, b, a);
        vertex(out, pose, x3, y3, z3, r, g, b, a); // Duplicate last vertex to form quad-compatible primitive
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        vertex(out, pose, x0, y0, z0, r, g, b, a);
        vertex(out, pose, x1, y1, z1, r, g, b, a);
        vertex(out, pose, x2, y2, z2, r, g, b, a);
        vertex(out, pose, x3, y3, z3, r, g, b, a);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, float r, float g, float b, float a) {
        out.addVertex(pose, x, y, z)
                .setColor(r, g, b, a); // POSITION_COLOR: genuinely untextured, fullbright colored mesh.
    }
}
