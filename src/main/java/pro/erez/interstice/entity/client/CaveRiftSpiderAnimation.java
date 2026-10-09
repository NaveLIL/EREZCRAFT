package pro.erez.interstice.entity.client;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

public final class CaveRiftSpiderAnimation {
    private CaveRiftSpiderAnimation() {}

    public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(4.0F).looping()
            .addAnimation("prosoma", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("prosoma", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.000F, KeyframeAnimations.posVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.posVec(0.00F, 1.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.posVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.posVec(0.00F, -1.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.posVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("opisthosoma", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.000F, KeyframeAnimations.posVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.posVec(0.00F, 0.50F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.posVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.posVec(0.00F, -0.50F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.posVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("opisthosoma2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 5.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -5.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_chelifore", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(2.50F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(-2.81F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_chelifore", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(-2.50F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(2.81F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_palp", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.36F, 1.02F, -4.91F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(2.29F, -0.50F, 2.46F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_palp", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(-0.36F, -1.02F, -4.91F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(-2.29F, 0.50F, 2.46F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("proboscis", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 5.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 5.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_first_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(1.19F, -1.71F, 2.18F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(-2.71F, 3.30F, -4.63F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_first_leg3", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_first_leg4", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_first_leg5", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -5.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_second_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(2.47F, 0.77F, 3.45F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(-2.50F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_second_leg3", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(2.50F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(2.50F, -0.07F, -1.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_second_leg4", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(-7.49F, 0.18F, 0.27F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(-0.50F, -0.87F, -0.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_third_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.02F, 0.77F, 3.45F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(-2.50F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_third_leg3", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(2.50F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(2.50F, 0.04F, -2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_third_leg4", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(-5.00F, -0.43F, 0.25F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(-0.00F, 0.87F, 0.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("_left_fourth_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(-1.18F, -1.34F, 2.42F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("_leftfourth_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.083F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("_leftfourth_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.083F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("_leftfourth_leg3", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.083F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 10.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_first_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(-1.19F, 1.71F, 2.18F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(2.71F, -3.30F, -4.63F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_first_leg3", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_first_leg4", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_first_leg5", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -5.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_second_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(-2.47F, -0.77F, 3.45F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(5.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_second_leg3", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(-2.50F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(-2.50F, -0.07F, -1.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_second_leg4", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(7.49F, -0.13F, 0.18F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(-2.00F, -0.87F, -0.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_third_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(-0.02F, -0.77F, 3.45F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(2.50F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_third_leg3", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(-2.50F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(-2.50F, 0.04F, -2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_third_leg4", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(5.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(0.00F, 0.87F, 0.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_fourth_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(1.18F, 1.34F, 2.42F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_fourth_leg3", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.083F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_fourth_leg4", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.083F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_fourth_leg5", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.083F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 10.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_palp2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(5.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(-5.00F, 0.22F, 2.49F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_palp2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(-5.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(5.00F, -0.22F, 2.49F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_chelifore2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(2.50F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(-5.31F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_chelifore2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(-2.50F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.000F, KeyframeAnimations.degreeVec(5.31F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .build();

    public static final AnimationDefinition MOVE = AnimationDefinition.Builder.withLength(1.0F).looping()
            .addAnimation("prosoma", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.750F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -2.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("prosoma", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.000F, KeyframeAnimations.posVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.125F, KeyframeAnimations.posVec(0.00F, 1.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.posVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.375F, KeyframeAnimations.posVec(0.00F, -1.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.posVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.625F, KeyframeAnimations.posVec(0.00F, 1.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.750F, KeyframeAnimations.posVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.875F, KeyframeAnimations.posVec(0.00F, -1.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.posVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("opisthosoma", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.000F, KeyframeAnimations.posVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.125F, KeyframeAnimations.posVec(0.00F, 1.25F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.posVec(0.00F, 0.50F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.375F, KeyframeAnimations.posVec(0.00F, -0.65F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.417F, KeyframeAnimations.posVec(0.00F, -0.57F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.posVec(0.00F, 0.02F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.625F, KeyframeAnimations.posVec(0.00F, 0.57F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.750F, KeyframeAnimations.posVec(0.00F, -0.62F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.875F, KeyframeAnimations.posVec(0.00F, -1.32F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.posVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("opisthosoma2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -7.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.750F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -7.50F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_chelifore", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.degreeVec(5.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.750F, KeyframeAnimations.degreeVec(-8.13F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_chelifore", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.degreeVec(-5.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.750F, KeyframeAnimations.degreeVec(8.12F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_palp", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.degreeVec(-7.50F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.degreeVec(1.94F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.750F, KeyframeAnimations.degreeVec(9.53F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_palp", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.degreeVec(7.50F, -0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.degreeVec(-1.94F, -0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.750F, KeyframeAnimations.degreeVec(-9.53F, -0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_first_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(-16.07F, -21.85F, 5.91F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.degreeVec(-20.65F, -14.59F, -10.67F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.750F, KeyframeAnimations.degreeVec(7.09F, -14.53F, 15.48F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(-16.07F, -21.85F, 5.91F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_second_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.degreeVec(20.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.degreeVec(22.41F, -20.97F, -2.31F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.750F, KeyframeAnimations.degreeVec(2.41F, -20.97F, -2.31F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_third_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(22.46F, -24.80F, 0.89F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.degreeVec(2.46F, -24.80F, 0.89F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.degreeVec(0.32F, -0.09F, -1.46F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.750F, KeyframeAnimations.degreeVec(20.32F, -0.09F, -1.46F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(22.46F, -24.80F, 0.89F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_third_leg3", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -5.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -5.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, -5.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("_left_fourth_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.degreeVec(4.76F, 7.25F, -11.24F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.degreeVec(30.89F, -0.12F, -25.68F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.750F, KeyframeAnimations.degreeVec(24.54F, -15.42F, -15.06F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_first_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.degreeVec(-7.09F, 14.53F, 15.48F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.degreeVec(18.52F, 22.54F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.750F, KeyframeAnimations.degreeVec(20.65F, 14.59F, -10.67F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_second_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(-22.41F, 20.97F, -2.31F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.degreeVec(-2.41F, 20.97F, -2.31F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.750F, KeyframeAnimations.degreeVec(-20.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(-22.41F, 20.97F, -2.31F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_third_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.degreeVec(-20.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.degreeVec(-22.46F, 24.80F, -0.89F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.750F, KeyframeAnimations.degreeVec(-2.46F, 24.80F, 0.89F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_fourth_leg2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(-30.89F, 0.12F, -25.68F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.250F, KeyframeAnimations.degreeVec(-26.87F, 11.15F, -18.55F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.750F, KeyframeAnimations.degreeVec(-4.76F, -7.25F, -11.24F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(-30.89F, 0.12F, -25.68F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("right_chelifore2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.degreeVec(-5.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .addAnimation("left_chelifore2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.500F, KeyframeAnimations.degreeVec(5.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.000F, KeyframeAnimations.degreeVec(0.00F, 0.00F, 0.00F), AnimationChannel.Interpolations.CATMULLROM)))
            .build();

}
