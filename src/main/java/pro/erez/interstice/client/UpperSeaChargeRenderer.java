package pro.erez.interstice.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.fauna.UpperSeaCharge;

/** Samples the existing animated upper-ocean atlas tile. No new or copied image. */
public final class UpperSeaChargeRenderer extends EntityRenderer<UpperSeaCharge> {
    private static final ResourceLocation TILE = ResourceLocation.fromNamespaceAndPath(Interstice.ID, "block/light_still_v3");
    public UpperSeaChargeRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(UpperSeaCharge entity) { return TextureAtlas.LOCATION_BLOCKS; }
    @Override public void render(UpperSeaCharge entity, float yaw, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light) {
        poses.pushPose(); poses.translate(0, .27, 0);
        poses.mulPose(new Quaternionf().rotateY((entity.tickCount + partialTick) * .12F));
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(TILE);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        var pose = poses.last(); float s = .25F;
        quad(vertices, pose, sprite, light, new float[]{-s,-s,s, s,-s,s, s,s,s, -s,s,s}, 0,0,1);
        quad(vertices, pose, sprite, light, new float[]{s,-s,-s, -s,-s,-s, -s,s,-s, s,s,-s}, 0,0,-1);
        quad(vertices, pose, sprite, light, new float[]{s,-s,s, s,-s,-s, s,s,-s, s,s,s}, 1,0,0);
        quad(vertices, pose, sprite, light, new float[]{-s,-s,-s, -s,-s,s, -s,s,s, -s,s,-s}, -1,0,0);
        quad(vertices, pose, sprite, light, new float[]{-s,s,s, s,s,s, s,s,-s, -s,s,-s}, 0,1,0);
        quad(vertices, pose, sprite, light, new float[]{-s,-s,-s, s,-s,-s, s,-s,s, -s,-s,s}, 0,-1,0);
        poses.popPose(); super.render(entity, yaw, partialTick, poses, buffers, light);
    }
    private static void quad(VertexConsumer vertices, PoseStack.Pose pose, TextureAtlasSprite sprite,
                             int light, float[] points, float nx, float ny, float nz) {
        for (int i = 0; i < 4; i++) {
            float u = i == 0 || i == 3 ? sprite.getU0() : sprite.getU1();
            float v = i < 2 ? sprite.getV0() : sprite.getV1();
            vertices.addVertex(pose, points[i * 3], points[i * 3 + 1], points[i * 3 + 2])
                    .setColor(255, 255, 255, 235).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light).setNormal(pose, nx, ny, nz);
        }
    }
}
