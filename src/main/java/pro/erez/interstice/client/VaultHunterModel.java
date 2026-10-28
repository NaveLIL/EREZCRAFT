package pro.erez.interstice.client;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import pro.erez.interstice.fauna.VaultHunter;

/** Wendigo by Willmood (CC BY 4.0), original gameplay animation by EREZ Dev. */
public final class VaultHunterModel extends HierarchicalModel<VaultHunter> {
    public static final float SCALE = .72F;
    private final ModelPart root, full, body, head, jaw, leftArm, rightArm, leftElbow, rightElbow;
    private final ModelPart leftLeg, rightLeg, leftKnee, rightKnee, leftFoot, rightFoot;
    public VaultHunterModel() {
        root = VaultHunterGeometry.create(); full = root.getChild("full");
        body = full.getChild("body"); head = body.getChild("head"); jaw = head.getChild("jaw");
        leftArm = body.getChild("left_arm"); rightArm = body.getChild("right_arm");
        leftElbow = leftArm.getChild("left_arm_down"); rightElbow = rightArm.getChild("right_arm_down");
        leftLeg = full.getChild("left_leg"); rightLeg = full.getChild("right_leg");
        leftKnee = leftLeg.getChild("left_leg_down"); rightKnee = rightLeg.getChild("right_leg_down");
        leftFoot = leftKnee.getChild("left_foot"); rightFoot = rightKnee.getChild("right_foot");
    }
    @Override public ModelPart root() { return root; }
    @Override public void setupAnim(VaultHunter entity, float walk, float amount, float age, float yaw, float pitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float breath = Mth.sin(age * .07F);
        float movement = Mth.clamp(amount * 1.7F, 0, 1);
        boolean running = entity.phase() == VaultHunter.Phase.CHASE || entity.phase() == VaultHunter.Phase.RETREAT;
        boolean waiting = entity.phase() == VaultHunter.Phase.AMBUSH || entity.phase() == VaultHunter.Phase.COOLDOWN;
        // Vanilla walk phase already advances with actual displacement. Match
        // this model's long legs to that distance instead of a small idle shuffle.
        float gait = walk * (running ? .95F : .8F);
        float stride = Mth.cos(gait) * movement * (running ? 1.05F : .8F);
        // Lower the source's spread arms to a hanging stance; preserve its curved claws.
        leftArm.zRot = 1.02F; rightArm.zRot = -1.02F;
        leftElbow.xRot = rightElbow.xRot = -.2F;
        leftElbow.zRot = .16F; rightElbow.zRot = -.16F;
        leftArm.xRot = .05F - stride * .65F; rightArm.xRot = .05F + stride * .65F;
        leftLeg.xRot = stride; rightLeg.xRot = -stride;
        leftKnee.xRot = -.1F - Math.max(0, -Mth.sin(gait)) * movement * .65F;
        rightKnee.xRot = -.1F - Math.max(0, Mth.sin(gait)) * movement * .65F;
        leftFoot.xRot = -leftKnee.xRot * .35F; rightFoot.xRot = -rightKnee.xRot * .35F;
        body.xRot = .07F + (running ? .15F * movement : 0) + breath * .018F;
        body.zRot = Mth.sin(gait) * movement * .035F;
        body.y -= breath * .08F;
        head.yRot = Mth.clamp(yaw, -60, 60) * Mth.DEG_TO_RAD;
        head.xRot = Mth.clamp(pitch, -35, 35) * Mth.DEG_TO_RAD - body.xRot;
        jaw.xRot += breath * .025F;
        if (entity.phase() == VaultHunter.Phase.SEARCH || entity.phase() == VaultHunter.Phase.INVESTIGATE)
            head.yRot += Mth.sin(age * .045F) * .22F;
        if (waiting) { body.xRot += .14F; head.xRot -= .12F; leftElbow.xRot -= .2F; rightElbow.xRot -= .2F; }
        if (entity.phase() == VaultHunter.Phase.RETREAT) head.yRot += Mth.sin(age * .12F) * .18F;
        float partial = Mth.clamp(age - entity.tickCount, 0, 1);
        float progress = entity.attackProgress(partial);
        if (progress > 0) {
            float raise = progress < .5F ? progress * 2 : Math.max(0, (1-progress) * 2);
            float swipe = Mth.sin(Mth.clamp((progress-.35F)/.5F,0,1) * Mth.PI);
            rightArm.xRot -= raise * 1.8F; rightArm.zRot -= swipe * .7F;
            rightElbow.xRot -= raise * .7F;
            body.yRot -= swipe * .22F; head.yRot += swipe * .22F;
            jaw.xRot -= raise * .35F;
        }
        if (entity.deathTime > 0) jaw.xRot -= .22F;
    }
}
