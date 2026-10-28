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
        BackpackModels.render(poses,buffers,light,player,getParentModel(),worn);
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
