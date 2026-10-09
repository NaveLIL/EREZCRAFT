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
    private static final int FULL_BRIGHT = 0xF000F0;

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

        // 1. Cosmic Void Singularity (End Gateway parallax starfield)
        poseStack.pushPose();
        float corePulse = 0.45F + 0.06F * Mth.sin(time * 0.15F);
        corePulse *= (0.6F + 0.4F * stability);
        poseStack.scale(corePulse, corePulse, corePulse);
        poseStack.mulPose(new Quaternionf().rotateY(Mth.DEG_TO_RAD * time * 2.0F));
        poseStack.mulPose(new Quaternionf().rotateX(Mth.DEG_TO_RAD * time * 1.5F));

        VertexConsumer gatewayConsumer = buffer.getBuffer(RenderType.endGateway());
        renderSolidCube(gatewayConsumer, poseStack.last(), 0.55F);
        poseStack.popPose();

        // 2. Translucent Faceted Crystalline Aura over the core
        VertexConsumer transConsumer = buffer.getBuffer(RenderType.translucent());

        poseStack.pushPose();
        float auraPulse = corePulse * 1.35F;
        poseStack.scale(auraPulse, auraPulse, auraPulse);
        poseStack.mulPose(new Quaternionf().rotateY(Mth.DEG_TO_RAD * -time * 3.5F));
        poseStack.mulPose(new Quaternionf().rotateZ(Mth.DEG_TO_RAD * time * 2.0F));
        renderOctahedron(transConsumer, poseStack.last(), 0.65F, 0.25F, 0.95F, 0.60F * stability);
        poseStack.popPose();

        // 3. Four Orbiting 3D Chrono-Crystal Shards
        float orbitRadius = 1.35F * (0.85F + 0.15F * stability);
        for (int i = 0; i < 4; i++) {
            float baseAngle = (i * (float) Math.PI / 2.0F) + (time * 0.04F);
            float bob = Mth.sin(time * 0.1F + i * 1.57F) * 0.22F;

            float sx = Mth.cos(baseAngle) * orbitRadius;
            float sz = Mth.sin(baseAngle) * orbitRadius;
            float sy = bob;

            poseStack.pushPose();
            poseStack.translate(sx, sy, sz);

            // Shard self-rotation
            poseStack.mulPose(new Quaternionf().rotateY(Mth.DEG_TO_RAD * (time * 5.0F + i * 90.0F)));
            poseStack.mulPose(new Quaternionf().rotateX(Mth.DEG_TO_RAD * 25.0F));

            renderCrystalShard(transConsumer, poseStack.last(), 0.14F, 0.38F, 0.30F, 0.90F, 0.95F, 0.85F * stability);
            poseStack.popPose();
        }

        // 4. Volumetric 3D Orbital Gyroscope Ring 1 (tilted, thick 3D bands)
        poseStack.pushPose();
        float ring1Radius = 1.65F * (0.85F + 0.15F * stability);
        poseStack.mulPose(new Quaternionf().rotateZ(Mth.DEG_TO_RAD * 40.0F));
        poseStack.mulPose(new Quaternionf().rotateY(Mth.DEG_TO_RAD * time * 2.5F));
        renderVolumetricRing(transConsumer, poseStack.last(), ring1Radius, 0.08F, 0.06F, 0.25F, 0.85F, 0.95F, 0.70F * stability);
        poseStack.popPose();

        // 5. Volumetric 3D Orbital Gyroscope Ring 2 (counter-tilted)
        poseStack.pushPose();
        float ring2Radius = 1.95F * (0.85F + 0.15F * stability);
        poseStack.mulPose(new Quaternionf().rotateX(Mth.DEG_TO_RAD * -50.0F));
        poseStack.mulPose(new Quaternionf().rotateY(Mth.DEG_TO_RAD * -time * 1.8F));
        renderVolumetricRing(transConsumer, poseStack.last(), ring2Radius, 0.08F, 0.06F, 0.75F, 0.30F, 0.95F, 0.60F * stability);
        poseStack.popPose();

        // 6. Planar Accretion Vortex (horizontal swirling disc)
        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf().rotateY(Mth.DEG_TO_RAD * time * 4.0F));
        renderAccretionDisk(transConsumer, poseStack.last(), 2.3F * stability, 0.5F, 0.2F, 0.8F, 0.28F * stability);
        poseStack.popPose();

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void renderSolidCube(VertexConsumer consumer, PoseStack.Pose pose, float s) {
        quad(consumer, pose, -s, -s,  s,   s, -s,  s,   s,  s,  s,  -s,  s,  s,   1, 1, 1, 1);
        quad(consumer, pose,  s, -s, -s,  -s, -s, -s,  -s,  s, -s,   s,  s, -s,   1, 1, 1, 1);
        quad(consumer, pose,  s, -s,  s,   s, -s, -s,   s,  s, -s,   s,  s,  s,   1, 1, 1, 1);
        quad(consumer, pose, -s, -s, -s,  -s, -s,  s,  -s,  s,  s,  -s,  s, -s,   1, 1, 1, 1);
        quad(consumer, pose, -s,  s,  s,   s,  s,  s,   s,  s, -s,  -s,  s, -s,   1, 1, 1, 1);
        quad(consumer, pose, -s, -s, -s,   s, -s, -s,   s, -s,  s,  -s, -s,  s,   1, 1, 1, 1);
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

    private static void renderCrystalShard(VertexConsumer consumer, PoseStack.Pose pose, float w, float h, float r, float g, float b, float a) {
        // Double-pointed hexagonal/rhombic crystal shard
        // Top point (0, h, 0), Mid belt (+-w, 0, +-w), Bottom point (0, -h, 0)
        float mid = h * 0.3F;
        // Top cap
        triangle(consumer, pose, 0, h, 0, -w, mid, 0, 0, mid, -w, r, g, b, a);
        triangle(consumer, pose, 0, h, 0, 0, mid, -w, w, mid, 0, r, g, b, a);
        triangle(consumer, pose, 0, h, 0, w, mid, 0, 0, mid, w, r, g, b, a);
        triangle(consumer, pose, 0, h, 0, 0, mid, w, -w, mid, 0, r, g, b, a);
        // Center belt
        quad(consumer, pose, -w, mid, 0, 0, mid, -w, 0, -mid, -w, -w, -mid, 0, r * 0.9F, g * 0.9F, b * 0.9F, a);
        quad(consumer, pose, 0, mid, -w, w, mid, 0, w, -mid, 0, 0, -mid, -w, r * 0.9F, g * 0.9F, b * 0.9F, a);
        quad(consumer, pose, w, mid, 0, 0, mid, w, 0, -mid, w, w, -mid, 0, r * 0.9F, g * 0.9F, b * 0.9F, a);
        quad(consumer, pose, 0, mid, w, -w, mid, 0, -w, -mid, 0, 0, -mid, w, r * 0.9F, g * 0.9F, b * 0.9F, a);
        // Bottom cap
        triangle(consumer, pose, 0, -h, 0, 0, -mid, -w, -w, -mid, 0, r * 0.8F, g * 0.8F, b * 0.8F, a);
        triangle(consumer, pose, 0, -h, 0, w, -mid, 0, 0, -mid, -w, r * 0.8F, g * 0.8F, b * 0.8F, a);
        triangle(consumer, pose, 0, -h, 0, 0, -mid, w, w, -mid, 0, r * 0.8F, g * 0.8F, b * 0.8F, a);
        triangle(consumer, pose, 0, -h, 0, -w, -mid, 0, 0, -mid, w, r * 0.8F, g * 0.8F, b * 0.8F, a);
    }

    private static void renderVolumetricRing(VertexConsumer consumer, PoseStack.Pose pose, float radius, float width, float height, float r, float g, float b, float a) {
        int segments = 24;
        float angleStep = (2 * (float) Math.PI) / segments;

        for (int i = 0; i < segments; i++) {
            float a1 = i * angleStep;
            float a2 = (i + 1) * angleStep;

            float cos1 = Mth.cos(a1), sin1 = Mth.sin(a1);
            float cos2 = Mth.cos(a2), sin2 = Mth.sin(a2);

            float rInner = radius - width * 0.5F;
            float rOuter = radius + width * 0.5F;
            float hHalf = height * 0.5F;

            // Outer face
            quad(consumer, pose,
                    cos1 * rOuter, -hHalf, sin1 * rOuter,
                    cos2 * rOuter, -hHalf, sin2 * rOuter,
                    cos2 * rOuter,  hHalf, sin2 * rOuter,
                    cos1 * rOuter,  hHalf, sin1 * rOuter,
                    r, g, b, a);

            // Inner face
            quad(consumer, pose,
                    cos2 * rInner, -hHalf, sin2 * rInner,
                    cos1 * rInner, -hHalf, sin1 * rInner,
                    cos1 * rInner,  hHalf, sin1 * rInner,
                    cos2 * rInner,  hHalf, sin2 * rInner,
                    r * 0.85F, g * 0.85F, b * 0.85F, a);

            // Top face
            quad(consumer, pose,
                    cos1 * rInner, hHalf, sin1 * rInner,
                    cos2 * rInner, hHalf, sin2 * rInner,
                    cos2 * rOuter, hHalf, sin2 * rOuter,
                    cos1 * rOuter, hHalf, sin1 * rOuter,
                    r * 1.1F, g * 1.1F, b * 1.1F, a);

            // Bottom face
            quad(consumer, pose,
                    cos1 * rOuter, -hHalf, sin1 * rOuter,
                    cos2 * rOuter, -hHalf, sin2 * rOuter,
                    cos2 * rInner, -hHalf, sin2 * rInner,
                    cos1 * rInner, -hHalf, sin1 * rInner,
                    r * 0.75F, g * 0.75F, b * 0.75F, a);
        }
    }

    private static void renderAccretionDisk(VertexConsumer consumer, PoseStack.Pose pose, float radius, float r, float g, float b, float a) {
        int segments = 24;
        float angleStep = (2 * (float) Math.PI) / segments;

        for (int i = 0; i < segments; i++) {
            float a1 = i * angleStep;
            float a2 = (i + 1) * angleStep;

            float x1 = Mth.cos(a1) * radius;
            float z1 = Mth.sin(a1) * radius;
            float x2 = Mth.cos(a2) * radius;
            float z2 = Mth.sin(a2) * radius;

            // Two concentric rings fading outward
            triangle(consumer, pose, 0, 0, 0, x1, 0, z1, x2, 0, z2, r, g, b, a);
            triangle(consumer, pose, 0, 0, 0, x2, 0, z2, x1, 0, z1, r, g, b, a);
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
        vertex(out, pose, x3, y3, z3, r, g, b, a);
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
                .setColor(Math.min(1.0F, r), Math.min(1.0F, g), Math.min(1.0F, b), a)
                .setUv(0, 0)
                .setOverlay(0)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
