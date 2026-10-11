package pro.erez.interstice.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.fauna.UpperSeaGhast;

/** An asymmetric rocky float bladder, lower jaw, and five toxin sacs. All imagery is accepted tiles. */
public final class UpperSeaGhastModel extends HierarchicalModel<UpperSeaGhast> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(Interstice.ID, "upper_sea_ghast"), "main");
    private final ModelPart root, shell, jaw, sacs, eyes;
    private final ModelPart[] tendrils = new ModelPart[5];
    public UpperSeaGhastModel(ModelPart root) {
        this.root = root;
        shell = root.getChild("shell"); jaw = shell.getChild("jaw");
        sacs = root.getChild("sacs"); eyes = root.getChild("eyes");
        for (int i = 0; i < tendrils.length; i++) tendrils[i] = shell.getChild("tendril_" + i);
    }
    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        var shell = root.addOrReplaceChild("shell", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-12, -10, -10, 24, 20, 20)
                .texOffs(0, 0).addBox(-15, -6, -8, 4, 12, 16)
                .texOffs(0, 0).addBox(9, -4, -6, 6, 9, 12), PartPose.offset(0, 8, 0));
        shell.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-8, 0, -6, 16, 3, 12), PartPose.offset(0, 9, -5));
        var sacs = root.addOrReplaceChild("sacs", CubeListBuilder.create(), PartPose.offset(0, 8, 0));
        int[][] positions = {{-9, -4}, {-5, 6}, {0, 0}, {6, -5}, {10, 5}};
        for (int i = 0; i < positions.length; i++) {
            int x = positions[i][0], z = positions[i][1], length = 10 + i % 3 * 3;
            shell.addOrReplaceChild("tendril_" + i, CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-1, 0, -1, 2, length, 2), PartPose.offset(x, 9, z));
            sacs.addOrReplaceChild("sac_" + i, CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-2.5F, -2, -2.5F, 5, 5, 5), PartPose.offset(x, 9 + length, z));
        }
        root.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-8, 1, -10.3F, 3, 2, .5F)
                .addBox(4, -2, -10.3F, 5, 2, .5F), PartPose.offset(0, 8, 0));
        return LayerDefinition.create(mesh, 16, 16);
    }
    @Override public ModelPart root() { return root; }
    @Override public void setupAnim(UpperSeaGhast entity, float limbSwing, float limbSwingAmount,
                                    float age, float netHeadYaw, float headPitch) {
        float bob = Mth.sin(age * .08F) * .65F;
        shell.y = 8 + bob; sacs.y = 8 + bob; eyes.y = 8 + bob;
        jaw.xRot = entity.isCharging() ? .65F : .05F;
        for (int i = 0; i < tendrils.length; i++) {
            tendrils[i].xRot = Mth.sin(age * .12F + i * .7F) * .10F;
            var sac = sacs.getChild("sac_" + i);
            sac.zRot = Mth.sin(age * .09F + i) * .07F;
            float swell = entity.isCharging() ? 1.15F : 1;
            sac.xScale = swell; sac.yScale = swell; sac.zScale = swell;
        }
    }
    @Override public void renderToBuffer(PoseStack poses, VertexConsumer vertices, int light, int overlay, int color) {
        shell.render(poses, vertices, light, overlay, color);
    }
    void renderSacs(PoseStack poses, VertexConsumer vertices, int light, int overlay, int color) {
        sacs.render(poses, vertices, light, overlay, color);
    }
    void renderEyes(PoseStack poses, VertexConsumer vertices, int light, int overlay, int color) {
        eyes.render(poses, vertices, light, overlay, color);
    }
}
