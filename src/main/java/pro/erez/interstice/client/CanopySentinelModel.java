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
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.fauna.CanopySentinel;

/**
 * A small squirrel-like crown guardian, built from fourteen ordinary Minecraft cubes.
 * Its low bark body and broad, segmented leaf tail carry the silhouette; no high-poly
 * mesh, new sprite, or texture atlas is needed. Existing 16-pixel tiles repeat on the cubes.
 */
@OnlyIn(Dist.CLIENT)
public final class CanopySentinelModel extends HierarchicalModel<CanopySentinel> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(Interstice.ID, "canopy_sentinel"), "main");

    private final ModelPart root;
    private final ModelPart bark;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart leftEar;
    private final ModelPart rightEar;
    private final ModelPart leftFrontLeg;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftHindLeg;
    private final ModelPart rightHindLeg;
    private final ModelPart eyes;
    private final ModelPart tail;
    private final ModelPart tailMiddle;
    private final ModelPart tailTip;
    private float partialTick;

    public CanopySentinelModel(ModelPart root) {
        this.root = root;
        this.bark = root.getChild("bark");
        this.body = bark.getChild("body");
        this.head = bark.getChild("head");
        this.leftEar = head.getChild("left_ear");
        this.rightEar = head.getChild("right_ear");
        this.leftFrontLeg = bark.getChild("left_front_leg");
        this.rightFrontLeg = bark.getChild("right_front_leg");
        this.leftHindLeg = bark.getChild("left_hind_leg");
        this.rightHindLeg = bark.getChild("right_hind_leg");
        this.eyes = root.getChild("eyes");
        this.tail = root.getChild("tail");
        this.tailMiddle = tail.getChild("tail_middle");
        this.tailTip = tailMiddle.getChild("tail_tip");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition bark = root.addOrReplaceChild("bark", CubeListBuilder.create(), PartPose.ZERO);
        bark.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.0F, -2.5F, -4.5F, 6.0F, 5.0F, 9.0F), PartPose.offset(0.0F, 18.5F, 0.0F));
        PartDefinition head = bark.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offset(0.0F, 17.0F, -6.0F));
        head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.0F, 0.25F, -1.0F, 2.0F, 1.5F, 1.0F), PartPose.offset(0.0F, 0.0F, -2.0F));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offset(1.3F, -2.0F, 0.0F));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offset(-1.3F, -2.0F, 0.0F));
        addLeg(bark, "left_front_leg", 2.1F, -3.0F);
        addLeg(bark, "right_front_leg", -2.1F, -3.0F);
        addLeg(bark, "left_hind_leg", 2.1F, 3.0F);
        addLeg(bark, "right_hind_leg", -2.1F, 3.0F);

        // Two dark bark cubes give the face readable eyes without drawing pixels into a PNG.
        root.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.75F, -0.75F, -2.15F, 1.0F, 1.0F, 0.25F)
                .addBox(0.75F, -0.75F, -2.15F, 1.0F, 1.0F, 0.25F), PartPose.offset(0.0F, 17.0F, -6.0F));
        PartDefinition tail = root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 18.5F, 4.0F, 0.6F, 0.0F, 0.0F));
        PartDefinition middle = tail.addOrReplaceChild("tail_middle", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 5.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.65F, 0.0F, 0.0F));
        middle.addOrReplaceChild("tail_tip", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.5F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 16, 16);
    }

    private static void addLeg(PartDefinition bark, String name, float x, float z) {
        bark.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(x, 20.0F, z));
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void prepareMobModel(CanopySentinel entity, float limbSwing, float limbSwingAmount, float partialTick) {
        this.partialTick = partialTick;
    }

    @Override
    public void setupAnim(CanopySentinel entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        boolean chase = switch (entity.phase()) {
            case CHASE -> true;
            default -> false;
        };
        float warning = switch (entity.phase()) {
            case WARNING -> Mth.clamp(entity.warningProgress(partialTick), 0.0F, 1.0F);
            case CHASE -> 1.0F;
            default -> 0.0F;
        };
        float breath = Mth.sin(ageInTicks * 0.09F) * 0.12F * (1.0F - warning);
        body.y += breath;
        head.y += breath;
        head.xRot = Mth.clamp(headPitch, -35.0F, 35.0F) * Mth.DEG_TO_RAD;
        head.yRot = Mth.clamp(netHeadYaw, -65.0F, 65.0F) * Mth.DEG_TO_RAD;
        leftEar.zRot = warning * 0.48F;
        rightEar.zRot = -warning * 0.48F;
        leftEar.xRot = rightEar.xRot = chase ? -0.22F : 0.0F;
        eyes.copyFrom(head);

        float stride = limbSwing * (chase ? 1.15F : 0.75F);
        float amplitude = Mth.clamp(limbSwingAmount, 0.0F, 1.0F) * (chase ? 1.35F : 0.9F);
        leftFrontLeg.xRot = rightHindLeg.xRot = Mth.cos(stride) * amplitude;
        rightFrontLeg.xRot = leftHindLeg.xRot = Mth.cos(stride + Mth.PI) * amplitude;

        // The warning grows through silhouette alone: the large tail straightens upward.
        tail.y += breath;
        tail.xRot = Mth.lerp(warning, 0.6F, chase ? 1.0F : 1.15F)
                + Mth.sin(ageInTicks * 0.09F) * 0.035F * (1.0F - warning);
        tailMiddle.xRot = Mth.lerp(warning, 0.65F, 0.2F);
        tailTip.xRot = Mth.lerp(warning, 0.5F, 0.1F);
        tail.yRot = Mth.sin(ageInTicks * (chase ? 0.3F : 0.045F)) * (chase ? 0.06F : 0.035F);
    }

    @Override
    public void renderToBuffer(PoseStack poses, VertexConsumer vertices, int light, int overlay, int color) {
        bark.render(poses, vertices, light, overlay, color);
    }

    void renderLeaves(PoseStack poses, VertexConsumer vertices, int light, int overlay, int color) {
        tail.render(poses, vertices, light, overlay, color);
    }

    void renderEyes(PoseStack poses, VertexConsumer vertices, int light, int overlay, int color) {
        eyes.render(poses, vertices, light, overlay, color);
    }
}
