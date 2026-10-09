package pro.erez.interstice.entity.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import pro.erez.interstice.entity.EchoRiftEntity;

public final class EchoRiftRenderer extends EntityRenderer<EchoRiftEntity> {

    // Custom translucent RenderTypes with COLOR_WRITE (depthMask = false).
    // This allows fluids (toxic seas, water) and translucent terrain behind the anomaly
    // to render completely without being culled or creating an X-Ray hole into the sea bed!
    public static final RenderType RIFT_GLOW_QUADS = createGlowType("interstice_rift_glow_quads", VertexFormat.Mode.QUADS);
    public static final RenderType RIFT_GLOW_TRIANGLES = createGlowType("interstice_rift_glow_triangles", VertexFormat.Mode.TRIANGLES);

    private static RenderType createGlowType(String name, VertexFormat.Mode mode) {
        return RenderType.create(
                name,
                DefaultVertexFormat.POSITION_COLOR,
                mode,
                1536,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false)
        );
    }

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

        // 1. Cosmic Void Singularity (End Gateway parallax starfield in core)
        poseStack.pushPose();
        float corePulse = 0.45F + 0.06F * Mth.sin(time * 0.15F);
        corePulse *= (0.5F + 0.5F * stability);
        poseStack.scale(corePulse, corePulse, corePulse);
        poseStack.mulPose(new Quaternionf().rotateY(Mth.DEG_TO_RAD * time * 2.0F));
        poseStack.mulPose(new Quaternionf().rotateX(Mth.DEG_TO_RAD * time * 1.5F));

        VertexConsumer gatewayConsumer = buffer.getBuffer(RenderType.endGateway());
        renderSolidCube(gatewayConsumer, poseStack.last(), 0.52F);
        poseStack.popPose();

        // 2. Translucent Inner Octahedral Corona (using depthMask=false glow type)
        VertexConsumer triConsumer = buffer.getBuffer(RIFT_GLOW_TRIANGLES);
        VertexConsumer quadConsumer = buffer.getBuffer(RIFT_GLOW_QUADS);

        poseStack.pushPose();
        float auraPulse = corePulse * 1.35F;
        poseStack.scale(auraPulse, auraPulse, auraPulse);
        poseStack.mulPose(new Quaternionf().rotateY(Mth.DEG_TO_RAD * -time * 3.5F));
        poseStack.mulPose(new Quaternionf().rotateZ(Mth.DEG_TO_RAD * time * 2.0F));
        renderOctahedron(triConsumer, poseStack.last(), 0.70F, 0.25F, 0.95F, 0.50F * stability);
        poseStack.popPose();

        // 3. Four Orbiting 3D Chrono-Crystal Shards (Bipyramids)
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

            renderCrystalShard(triConsumer, quadConsumer, poseStack.last(), 0.15F, 0.40F, 0.25F, 0.90F, 0.95F, 0.85F * stability);
            poseStack.popPose();
        }

        // 4. Volumetric 3D Gyroscope Rings (forming a spherical containment field)
        // Ring 1: Equatorial tilted (Cyan)
        poseStack.pushPose();
        float ring1Radius = 1.65F * (0.85F + 0.15F * stability);
        poseStack.mulPose(new Quaternionf().rotateZ(Mth.DEG_TO_RAD * 40.0F));
        poseStack.mulPose(new Quaternionf().rotateY(Mth.DEG_TO_RAD * time * 2.5F));
        renderVolumetricRing(quadConsumer, poseStack.last(), ring1Radius, 0.08F, 0.06F, 0.20F, 0.85F, 0.95F, 0.75F * stability);
        poseStack.popPose();

        // Ring 2: Counter-tilted (Magenta)
        poseStack.pushPose();
        float ring2Radius = 1.95F * (0.85F + 0.15F * stability);
        poseStack.mulPose(new Quaternionf().rotateX(Mth.DEG_TO_RAD * -50.0F));
        poseStack.mulPose(new Quaternionf().rotateY(Mth.DEG_TO_RAD * -time * 1.8F));
        renderVolumetricRing(quadConsumer, poseStack.last(), ring2Radius, 0.08F, 0.06F, 0.85F, 0.25F, 0.95F, 0.65F * stability);
        poseStack.popPose();

        // Ring 3: Polar/Transverse (Deep Violet)
        poseStack.pushPose();
        float ring3Radius = 2.25F * (0.85F + 0.15F * stability);
        poseStack.mulPose(new Quaternionf().rotateY(Mth.DEG_TO_RAD * (time * 1.2F + 45.0F)));
        poseStack.mulPose(new Quaternionf().rotateX(Mth.DEG_TO_RAD * 75.0F));
        renderVolumetricRing(quadConsumer, poseStack.last(), ring3Radius, 0.08F, 0.06F, 0.55F, 0.20F, 0.90F, 0.55F * stability);
        poseStack.popPose();

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void renderSolidCube(VertexConsumer consumer, PoseStack.Pose pose, float s) {
        quadGateway(consumer, pose, -s, -s,  s,   s, -s,  s,   s,  s,  s,  -s,  s,  s);
        quadGateway(consumer, pose,  s, -s, -s,  -s, -s, -s,  -s,  s, -s,   s,  s, -s);
        quadGateway(consumer, pose,  s, -s,  s,   s, -s, -s,   s,  s, -s,   s,  s,  s);
        quadGateway(consumer, pose, -s, -s, -s,  -s, -s,  s,  -s,  s,  s,  -s,  s, -s);
        quadGateway(consumer, pose, -s,  s,  s,   s,  s,  s,   s,  s, -s,  -s,  s, -s);
        quadGateway(consumer, pose, -s, -s, -s,   s, -s, -s,   s, -s,  s,  -s, -s,  s);
    }

    private static void quadGateway(VertexConsumer consumer, PoseStack.Pose pose,
                                    float x0, float y0, float z0,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    float x3, float y3, float z3) {
        consumer.addVertex(pose, x0, y0, z0);
        consumer.addVertex(pose, x1, y1, z1);
        consumer.addVertex(pose, x2, y2, z2);
        consumer.addVertex(pose, x3, y3, z3);
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

    private static void renderCrystalShard(VertexConsumer triConsumer, VertexConsumer quadConsumer, PoseStack.Pose pose, float w, float h, float r, float g, float b, float a) {
        float mid = h * 0.3F;
        // Top cap (triangles)
        triangle(triConsumer, pose, 0, h, 0, -w, mid, 0, 0, mid, -w, r, g, b, a);
        triangle(triConsumer, pose, 0, h, 0, 0, mid, -w, w, mid, 0, r, g, b, a);
        triangle(triConsumer, pose, 0, h, 0, w, mid, 0, 0, mid, w, r, g, b, a);
        triangle(triConsumer, pose, 0, h, 0, 0, mid, w, -w, mid, 0, r, g, b, a);
        // Center belt (quads)
        quad(quadConsumer, pose, -w, mid, 0, 0, mid, -w, 0, -mid, -w, -w, -mid, 0, r * 0.9F, g * 0.9F, b * 0.9F, a);
        quad(quadConsumer, pose, 0, mid, -w, w, mid, 0, w, -mid, 0, 0, -mid, -w, r * 0.9F, g * 0.9F, b * 0.9F, a);
        quad(quadConsumer, pose, w, mid, 0, 0, mid, w, 0, -mid, w, w, -mid, 0, r * 0.9F, g * 0.9F, b * 0.9F, a);
        quad(quadConsumer, pose, 0, mid, w, -w, mid, 0, -w, -mid, 0, 0, -mid, w, r * 0.9F, g * 0.9F, b * 0.9F, a);
        // Bottom cap (triangles)
        triangle(triConsumer, pose, 0, -h, 0, 0, -mid, -w, -w, -mid, 0, r * 0.8F, g * 0.8F, b * 0.8F, a);
        triangle(triConsumer, pose, 0, -h, 0, w, -mid, 0, 0, -mid, -w, r * 0.8F, g * 0.8F, b * 0.8F, a);
        triangle(triConsumer, pose, 0, -h, 0, 0, -mid, w, w, -mid, 0, r * 0.8F, g * 0.8F, b * 0.8F, a);
        triangle(triConsumer, pose, 0, -h, 0, -w, -mid, 0, 0, -mid, w, r * 0.8F, g * 0.8F, b * 0.8F, a);
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

    private static void triangle(VertexConsumer out, PoseStack.Pose pose,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float r, float g, float b, float a) {
        vertex(out, pose, x1, y1, z1, r, g, b, a);
        vertex(out, pose, x2, y2, z2, r, g, b, a);
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
                .setColor(Math.clamp(r, 0.0F, 1.0F), Math.clamp(g, 0.0F, 1.0F), Math.clamp(b, 0.0F, 1.0F), Math.clamp(a, 0.0F, 1.0F));
    }
}
