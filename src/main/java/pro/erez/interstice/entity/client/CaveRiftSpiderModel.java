package pro.erez.interstice.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.entity.CaveRiftSpiderEntity;

public final class CaveRiftSpiderModel extends HierarchicalModel<CaveRiftSpiderEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Interstice.ID, "cave_rift_spider"), "main");

    private final ModelPart root;

    public CaveRiftSpiderModel(ModelPart root) {
        this.root = root.getChild("root");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();

        PartDefinition root = parts.addOrReplaceChild("root",
                CubeListBuilder.create(),
                PartPose.offset(2F, 3.0F, 0F));
        PartDefinition opisthosoma = root.addOrReplaceChild("opisthosoma",
                CubeListBuilder.create()
                .texOffs(106, 25).addBox(-0.75F, -1.8500000000000014F, -2F, 4.0F, 4.000000000000002F, 4F),
                PartPose.offsetAndRotation(10F, 0F, 0F, -0.00000F, 0.00000F, 0.39270F));
        PartDefinition opisthosoma2 = opisthosoma.addOrReplaceChild("opisthosoma2",
                CubeListBuilder.create()
                .texOffs(72, 68).addBox(0.25F, -3.1000000000000014F, -2.5F, 12.0F, 4.000000000000002F, 5.0F),
                PartPose.offset(3F, 1.25F, 0F));
        PartDefinition prosoma = root.addOrReplaceChild("prosoma",
                CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8F, -2F, -3F, 20F, 4F, 6F),
                PartPose.offset(-2F, 0F, 0F));
        PartDefinition proboscis = prosoma.addOrReplaceChild("proboscis",
                CubeListBuilder.create()
                .texOffs(106, 20).addBox(-6.878679999999999F, -1.1213200000000008F, -1.5F, 8F, 2F, 3.0F),
                PartPose.offsetAndRotation(-8.12132F, 1.1213200000000008F, 0F, -0.00000F, 0.00000F, -0.78540F));
        PartDefinition palps = prosoma.addOrReplaceChild("palps",
                CubeListBuilder.create(),
                PartPose.offset(-3.5F, 2F, 0F));
        PartDefinition right_palp = palps.addOrReplaceChild("right_palp",
                CubeListBuilder.create()
                .texOffs(106, 33).addBox(-0.5F, 0F, -0.5F, 1F, 6F, 1F),
                PartPose.offsetAndRotation(0.0F, 0F, -2.5F, -0.20203F, 0.08292F, 0.38429F));
        PartDefinition right_palp2 = right_palp.addOrReplaceChild("right_palp2",
                CubeListBuilder.create()
                .texOffs(106, 47).addBox(-0.5F, 0F, -0.5F, 1F, 6F, 1F),
                PartPose.offsetAndRotation(0.0F, 6F, 0.0F, -0.00000F, 0.00000F, -1.39626F));
        PartDefinition left_palp = palps.addOrReplaceChild("left_palp",
                CubeListBuilder.create()
                .texOffs(106, 40).addBox(-0.5F, 0F, -0.5F, 1F, 6F, 1F),
                PartPose.offsetAndRotation(0.0F, 0F, 2.5F, 0.20203F, -0.08292F, 0.38429F));
        PartDefinition left_palp2 = left_palp.addOrReplaceChild("left_palp2",
                CubeListBuilder.create()
                .texOffs(106, 54).addBox(-0.5F, 0F, -0.5F, 1F, 6F, 1F),
                PartPose.offsetAndRotation(0.0F, 6F, 0.0F, -0.00000F, 0.00000F, -1.39626F));
        PartDefinition chelifores = prosoma.addOrReplaceChild("chelifores",
                CubeListBuilder.create(),
                PartPose.offset(0F, 10F, 0F));
        PartDefinition right_chelifore = chelifores.addOrReplaceChild("right_chelifore",
                CubeListBuilder.create()
                .texOffs(20, 100).addBox(-0.5F, 0F, -1F, 1F, 8F, 2F),
                PartPose.offsetAndRotation(-6.5F, -10F, -3F, -0.39270F, 0.00000F, 0.39270F));
        PartDefinition right_chelifore2 = right_chelifore.addOrReplaceChild("right_chelifore2",
                CubeListBuilder.create()
                .texOffs(106, 61).addBox(-1F, 0F, -0.5F, 2F, 4F, 1F),
                PartPose.offsetAndRotation(-0.5F, 8F, -0.5F, 0.78540F, 0.00000F, -0.00000F));
        PartDefinition left_chelifore = chelifores.addOrReplaceChild("left_chelifore",
                CubeListBuilder.create()
                .texOffs(26, 100).addBox(-0.5F, 0F, -1F, 1F, 8F, 2F),
                PartPose.offsetAndRotation(-6.5F, -10F, 3F, 0.39270F, 0.00000F, 0.39270F));
        PartDefinition left_chelifore2 = left_chelifore.addOrReplaceChild("left_chelifore2",
                CubeListBuilder.create()
                .texOffs(106, 66).addBox(-1F, 0F, -0.5F, 2F, 4F, 1F),
                PartPose.offsetAndRotation(-0.5F, 8F, 0.5F, -0.78540F, 0.00000F, -0.00000F));
        PartDefinition left_legs = prosoma.addOrReplaceChild("left_legs",
                CubeListBuilder.create(),
                PartPose.offset(2F, 0F, 3F));
        PartDefinition left_second_leg = left_legs.addOrReplaceChild("left_second_leg",
                CubeListBuilder.create()
                .texOffs(54, 99).addBox(-1F, -1F, 0F, 2F, 2F, 8F),
                PartPose.offsetAndRotation(-3F, 0F, 0F, -0.00000F, -0.21817F, -0.00000F));
        PartDefinition left_second_leg2 = left_second_leg.addOrReplaceChild("left_second_leg2",
                CubeListBuilder.create()
                .texOffs(0, 10).addBox(-1F, -1F, 0F, 2F, 2F, 16F),
                PartPose.offsetAndRotation(0F, 0F, 8F, 0.52360F, 0.00000F, -0.00000F));
        PartDefinition left_second_leg3 = left_second_leg2.addOrReplaceChild("left_second_leg3",
                CubeListBuilder.create()
                .texOffs(36, 10).addBox(-1F, -1F, 0F, 2F, 2F, 16F),
                PartPose.offsetAndRotation(0F, 0F, 16F, -1.57080F, 0.00000F, -0.00000F));
        PartDefinition left_second_leg4 = left_second_leg3.addOrReplaceChild("left_second_leg4",
                CubeListBuilder.create()
                .texOffs(72, 17).addBox(-0.5F, -0.5F, 0F, 1F, 1F, 16F),
                PartPose.offsetAndRotation(-0.5F, -0.5F, 16F, -0.52360F, 0.00000F, -0.00000F));
        PartDefinition left_third_leg = left_legs.addOrReplaceChild("left_third_leg",
                CubeListBuilder.create()
                .texOffs(106, 0).addBox(-1F, -1F, 0F, 2F, 2F, 8F),
                PartPose.offsetAndRotation(3F, 0F, 0F, -0.00000F, 0.21817F, -0.00000F));
        PartDefinition left_third_leg2 = left_third_leg.addOrReplaceChild("left_third_leg2",
                CubeListBuilder.create()
                .texOffs(0, 10).addBox(-1F, -1F, 0F, 2F, 2F, 16F),
                PartPose.offsetAndRotation(0F, 0F, 8F, 0.52360F, 0.00000F, -0.00000F));
        PartDefinition left_third_leg3 = left_third_leg2.addOrReplaceChild("left_third_leg3",
                CubeListBuilder.create()
                .texOffs(36, 10).addBox(-1F, -1F, 0F, 2F, 2F, 16F),
                PartPose.offsetAndRotation(0F, 0F, 16F, -1.57080F, 0.00000F, -0.00000F));
        PartDefinition left_third_leg4 = left_third_leg3.addOrReplaceChild("left_third_leg4",
                CubeListBuilder.create()
                .texOffs(72, 17).addBox(-0.5F, -0.5F, 0F, 1F, 1F, 16F),
                PartPose.offsetAndRotation(0.5F, -0.5F, 16F, -0.52360F, 0.00000F, -0.00000F));
        PartDefinition left_first_leg = left_legs.addOrReplaceChild("left_first_leg",
                CubeListBuilder.create()
                .texOffs(52, 0).addBox(-1F, -1F, 0F, 2F, 2F, 8F),
                PartPose.offsetAndRotation(-7F, 0F, 0F, -0.00000F, -0.39270F, -0.00000F));
        PartDefinition left_first_leg2 = left_first_leg.addOrReplaceChild("left_first_leg2",
                CubeListBuilder.create()
                .texOffs(36, 90).addBox(-16F, -1F, -1F, 16F, 2F, 2F),
                PartPose.offsetAndRotation(-1F, 0F, 7F, 0.74458F, 0.59558F, 1.02396F));
        PartDefinition left_first_leg3 = left_first_leg2.addOrReplaceChild("left_first_leg3",
                CubeListBuilder.create()
                .texOffs(0, 90).addBox(-16F, -1F, -1F, 16F, 2F, 2F),
                PartPose.offsetAndRotation(-16F, 0F, 0F, -0.00000F, 0.00000F, -0.82903F));
        PartDefinition left_first_leg4 = left_first_leg3.addOrReplaceChild("left_first_leg4",
                CubeListBuilder.create()
                .texOffs(72, 89).addBox(-16F, -1F, -1F, 16F, 2F, 2F),
                PartPose.offsetAndRotation(-16F, 0F, 0F, -0.00000F, 0.00000F, -1.57080F));
        PartDefinition left_first_leg5 = left_first_leg4.addOrReplaceChild("left_first_leg5",
                CubeListBuilder.create()
                .texOffs(70, 97).addBox(-16F, -0.5F, -0.5F, 16F, 1F, 1F),
                PartPose.offset(-16F, -0.5F, 0.5F));
        PartDefinition left_fourth_leg2 = left_legs.addOrReplaceChild("_left_fourth_leg2",
                CubeListBuilder.create()
                .texOffs(94, 99).addBox(-1F, -1F, 0F, 2F, 2F, 8F),
                PartPose.offsetAndRotation(7F, 0F, 0F, -0.00000F, 0.39270F, -0.00000F));
        PartDefinition left_fourth_leg = left_fourth_leg2.addOrReplaceChild("_left_fourth_leg",
                CubeListBuilder.create()
                .texOffs(36, 90).addBox(0F, -1F, -1F, 16F, 2F, 2F),
                PartPose.offsetAndRotation(1F, 0F, 7F, 0.57493F, -0.49805F, -0.93549F));
        PartDefinition leftfourth_leg = left_fourth_leg.addOrReplaceChild("_leftfourth_leg",
                CubeListBuilder.create()
                .texOffs(0, 90).addBox(0F, -1F, -1F, 16F, 2F, 2F),
                PartPose.offsetAndRotation(16F, 0F, 0F, -0.00000F, 0.00000F, 0.78540F));
        PartDefinition leftfourth_leg2 = leftfourth_leg.addOrReplaceChild("_leftfourth_leg2",
                CubeListBuilder.create()
                .texOffs(72, 89).addBox(0F, -1F, -1F, 16F, 2F, 2F),
                PartPose.offsetAndRotation(16F, 0F, 0F, -0.00000F, 0.00000F, 1.57080F));
        PartDefinition leftfourth_leg3 = leftfourth_leg2.addOrReplaceChild("_leftfourth_leg3",
                CubeListBuilder.create()
                .texOffs(70, 97).addBox(0F, -0.5F, -0.5F, 16F, 1F, 1F),
                PartPose.offset(16F, -0.5F, 0.5F));
        PartDefinition right_legs = prosoma.addOrReplaceChild("right_legs",
                CubeListBuilder.create(),
                PartPose.offset(2F, 0F, -3F));
        PartDefinition right_second_leg = right_legs.addOrReplaceChild("right_second_leg",
                CubeListBuilder.create()
                .texOffs(74, 99).addBox(-1F, -1F, -8F, 2F, 2F, 8F),
                PartPose.offsetAndRotation(-3F, 0F, 0F, -0.00000F, 0.21817F, -0.00000F));
        PartDefinition right_second_leg2 = right_second_leg.addOrReplaceChild("right_second_leg2",
                CubeListBuilder.create()
                .texOffs(0, 10).addBox(-1F, -1F, -16F, 2F, 2F, 16F),
                PartPose.offsetAndRotation(0F, 0F, -8F, -0.52360F, 0.00000F, -0.00000F));
        PartDefinition right_second_leg3 = right_second_leg2.addOrReplaceChild("right_second_leg3",
                CubeListBuilder.create()
                .texOffs(36, 10).addBox(-1F, -1F, -16F, 2F, 2F, 16F),
                PartPose.offsetAndRotation(0F, 0F, -16F, 1.57080F, 0.00000F, -0.00000F));
        PartDefinition right_second_leg4 = right_second_leg3.addOrReplaceChild("right_second_leg4",
                CubeListBuilder.create()
                .texOffs(72, 17).addBox(-0.5F, -0.5F, -16F, 1F, 1F, 16F),
                PartPose.offsetAndRotation(-0.5F, -0.5F, -16F, 0.52360F, 0.00000F, -0.00000F));
        PartDefinition right_third_leg = right_legs.addOrReplaceChild("right_third_leg",
                CubeListBuilder.create()
                .texOffs(106, 10).addBox(-1F, -1F, -8F, 2F, 2F, 8F),
                PartPose.offsetAndRotation(3F, 0F, 0F, -0.00000F, -0.21817F, -0.00000F));
        PartDefinition right_third_leg2 = right_third_leg.addOrReplaceChild("right_third_leg2",
                CubeListBuilder.create()
                .texOffs(0, 10).addBox(-1F, -1F, -16F, 2F, 2F, 16F),
                PartPose.offsetAndRotation(0F, 0F, -8F, -0.52360F, 0.00000F, -0.00000F));
        PartDefinition right_third_leg3 = right_third_leg2.addOrReplaceChild("right_third_leg3",
                CubeListBuilder.create()
                .texOffs(36, 10).addBox(-1F, -1F, -16F, 2F, 2F, 16F),
                PartPose.offsetAndRotation(0F, 0F, -16F, 1.57080F, 0.00000F, -0.00000F));
        PartDefinition right_third_leg4 = right_third_leg3.addOrReplaceChild("right_third_leg4",
                CubeListBuilder.create()
                .texOffs(72, 17).addBox(-0.5F, -0.5F, -16F, 1F, 1F, 16F),
                PartPose.offsetAndRotation(0.5F, -0.5F, -16F, 0.52360F, 0.00000F, -0.00000F));
        PartDefinition right_first_leg = right_legs.addOrReplaceChild("right_first_leg",
                CubeListBuilder.create()
                .texOffs(34, 98).addBox(-1F, -1F, -8F, 2F, 2F, 8F),
                PartPose.offsetAndRotation(-7F, 0F, 0F, -0.00000F, 0.39270F, -0.00000F));
        PartDefinition right_first_leg2 = right_first_leg.addOrReplaceChild("right_first_leg2",
                CubeListBuilder.create()
                .texOffs(36, 90).addBox(-16F, -1F, -1F, 16F, 2F, 2F),
                PartPose.offsetAndRotation(-1F, 0F, -7F, -0.74458F, -0.59558F, 1.02396F));
        PartDefinition right_first_leg3 = right_first_leg2.addOrReplaceChild("right_first_leg3",
                CubeListBuilder.create()
                .texOffs(0, 90).addBox(-16F, -1F, -1F, 16F, 2F, 2F),
                PartPose.offsetAndRotation(-16F, 0F, 0F, -0.00000F, 0.00000F, -0.82903F));
        PartDefinition right_first_leg4 = right_first_leg3.addOrReplaceChild("right_first_leg4",
                CubeListBuilder.create()
                .texOffs(72, 89).addBox(-16F, -1F, -1F, 16F, 2F, 2F),
                PartPose.offsetAndRotation(-16F, 0F, 0F, -0.00000F, 0.00000F, -1.57080F));
        PartDefinition right_first_leg5 = right_first_leg4.addOrReplaceChild("right_first_leg5",
                CubeListBuilder.create()
                .texOffs(70, 97).addBox(-16F, -0.5F, -0.5F, 16F, 1F, 1F),
                PartPose.offset(-16F, -0.5F, -0.5F));
        PartDefinition right_fourth_leg = right_legs.addOrReplaceChild("right_fourth_leg",
                CubeListBuilder.create()
                .texOffs(0, 100).addBox(-1F, -1F, -8F, 2F, 2F, 8F),
                PartPose.offsetAndRotation(7F, 0F, 0F, -0.00000F, -0.39270F, -0.00000F));
        PartDefinition right_fourth_leg2 = right_fourth_leg.addOrReplaceChild("right_fourth_leg2",
                CubeListBuilder.create()
                .texOffs(36, 90).addBox(0F, -1F, -1F, 16F, 2F, 2F),
                PartPose.offsetAndRotation(1F, 0F, -7F, -0.57493F, 0.49805F, -0.93549F));
        PartDefinition right_fourth_leg3 = right_fourth_leg2.addOrReplaceChild("right_fourth_leg3",
                CubeListBuilder.create()
                .texOffs(0, 90).addBox(0F, -1F, -1F, 16F, 2F, 2F),
                PartPose.offsetAndRotation(16F, 0F, 0F, -0.00000F, 0.00000F, 0.78540F));
        PartDefinition right_fourth_leg4 = right_fourth_leg3.addOrReplaceChild("right_fourth_leg4",
                CubeListBuilder.create()
                .texOffs(72, 89).addBox(0F, -1F, -1F, 16F, 2F, 2F),
                PartPose.offsetAndRotation(16F, 0F, 0F, -0.00000F, 0.00000F, 1.57080F));
        PartDefinition right_fourth_leg5 = right_fourth_leg4.addOrReplaceChild("right_fourth_leg5",
                CubeListBuilder.create()
                .texOffs(70, 97).addBox(0F, -0.5F, -0.5F, 16F, 1F, 1F),
                PartPose.offset(16F, -0.5F, -0.5F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(CaveRiftSpiderEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        this.animate(entity.idleAnimationState, CaveRiftSpiderAnimation.IDLE, ageInTicks, 1.0F);
        this.animateWalk(CaveRiftSpiderAnimation.MOVE, limbSwing, limbSwingAmount, 2.5F, 2.5F);
    }
}
