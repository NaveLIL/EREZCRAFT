package pro.erez.interstice.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Items;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.equipment.BackpackHarness;
import pro.erez.interstice.equipment.BackpackStorage;

/** Torso-bound wearable geometry. Every face uses a whole existing sprite, with no fabricated texture atlas. */
public final class BackpackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation CLOTH = texture("gui/backpack_cloth.png");
    private static final ResourceLocation METAL = texture("gui/retort_metal.png");
    private static final ResourceLocation FIELD_MARK = texture("item/expedition/field_backpack.png");
    private static final ResourceLocation EXPEDITION_MARK = texture("item/expedition/expedition_backpack.png");

    public BackpackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(Interstice.ID, "textures/" + path);
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbAmount, float partialTick, float age,
                       float headYaw, float headPitch) {
        if (!player.isAlive() || player.isSpectator() || player.isInvisible()) return;
        var worn = BackpackHarness.get(player);
        if (!BackpackStorage.isPack(worn)) return;
        boolean reinforced = BackpackStorage.capacity(worn) > 54;
        boolean armor = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ArmorItem;
        // Folded vanilla wings occupy the first two model pixels behind the torso.
        float back = player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) ? 4.15F : armor ? 3.1F : 2.15F;
        float front = armor ? -3.1F : -2.15F;
        float halfWidth = reinforced ? 4.3F : 4F;
        float depth = reinforced ? 4.1F : 3.5F;
        float outer = back + depth;
        float red = reinforced ? .55F : .9F;
        float green = reinforced ? .94F : .76F;
        float blue = reinforced ? .87F : .94F;
        poses.pushPose();
        try {
            // The parent's animated torso includes sneaking, swimming, flight and either player skin width.
            getParentModel().body.translateAndRotate(poses);
            poses.scale(1F / 16F, 1F / 16F, 1F / 16F);
            var pose = poses.last();
            var cloth = buffers.getBuffer(RenderType.entityCutoutNoCull(CLOTH));
            cube(cloth, pose, light, -halfWidth, 1.4F, back, halfWidth, 11.4F, outer, red, green, blue);
            // A projecting lid and layered outer pocket give the silhouette physical depth.
            cube(cloth, pose, light, -halfWidth - .2F, .75F, back - .1F, halfWidth + .2F, 2.1F, outer + .3F,
                    red * .85F, green * .85F, blue * .85F);
            cube(cloth, pose, light, -2.65F, 6F, outer - .06F, 2.65F, 10.8F, outer + 1.15F,
                    red * .88F, green * .88F, blue * .88F);
            cube(cloth, pose, light, -2.85F, 5.75F, outer + .05F, 2.85F, 6.7F, outer + 1.32F,
                    red * .72F, green * .72F, blue * .72F);
            if (reinforced) {
                cube(cloth, pose, light, -5.2F, 5F, back + .65F, -4.15F, 10.5F, outer - .45F,
                        red * .75F, green * .75F, blue * .75F);
                cube(cloth, pose, light, 4.15F, 5F, back + .65F, 5.2F, 10.5F, outer - .45F,
                        red * .75F, green * .75F, blue * .75F);
            }
            // Two front straps sit just beyond bare torso or chest armor; neither uses an equipment armor slot.
            for (float center : new float[]{-2.65F, 2.65F}) {
                cube(cloth, pose, light, center - .45F, .25F, front - .12F, center + .45F, 11.2F, front + .06F,
                        red * .52F, green * .52F, blue * .52F);
                cube(cloth, pose, light, center - .45F, .25F, front, center + .45F, 1F, back + .3F,
                        red * .52F, green * .52F, blue * .52F);
                cube(cloth, pose, light, center - .3F, 2.25F, outer + .02F, center + .3F, 5.55F, outer + .2F,
                        red * .48F, green * .48F, blue * .48F);
            }
            var metal = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
            for (float center : new float[]{-2.65F, 2.65F}) {
                cube(metal, pose, light, center - .62F, 7.65F, front - .24F, center + .62F, 8.75F, front - .1F,
                        .9F, .92F, 1F);
                cube(metal, pose, light, center - .48F, 3.8F, outer + .18F, center + .48F, 4.7F, outer + .35F,
                        .9F, .92F, 1F);
            }
            // The already shipped item sprite is a small sewn marker on the outer pocket, not a new bitmap.
            var mark = buffers.getBuffer(RenderType.entityCutoutNoCull(reinforced ? EXPEDITION_MARK : FIELD_MARK));
            quad(mark, pose, light, -1.5F, 7.1F, outer + 1.17F, 1.5F, 7.1F, outer + 1.17F,
                    1.5F, 10.1F, outer + 1.17F, -1.5F, 10.1F, outer + 1.17F, 0, 0, 1, 1, 1, 1);
        } finally {
            poses.popPose();
        }
    }

    private static void cube(VertexConsumer out, PoseStack.Pose pose, int light,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float red, float green, float blue) {
        quad(out, pose, light, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1, 0,0,1, red,green,blue);
        quad(out, pose, light, x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0, 0,0,-1, red,green,blue);
        quad(out, pose, light, x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1, 1,0,0, red,green,blue);
        quad(out, pose, light, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0, -1,0,0, red,green,blue);
        quad(out, pose, light, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, 0,-1,0, red,green,blue);
        quad(out, pose, light, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0, 0,1,0, red,green,blue);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose, int light,
                             float x0,float y0,float z0, float x1,float y1,float z1,
                             float x2,float y2,float z2, float x3,float y3,float z3,
                             float nx,float ny,float nz, float red,float green,float blue) {
        vertex(out,pose,light,x0,y0,z0,0,0,nx,ny,nz,red,green,blue);
        vertex(out,pose,light,x1,y1,z1,1,0,nx,ny,nz,red,green,blue);
        vertex(out,pose,light,x2,y2,z2,1,1,nx,ny,nz,red,green,blue);
        vertex(out,pose,light,x3,y3,z3,0,1,nx,ny,nz,red,green,blue);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, int light,
                               float x,float y,float z, float u,float v,
                               float nx,float ny,float nz, float red,float green,float blue) {
        out.addVertex(pose,x,y,z).setColor(red,green,blue,1F).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,nx,ny,nz);
    }
}
