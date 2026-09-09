package azbd.morpher.morph.animations;

import azbd.morpher.morph.MorphEntity;
import azbd.morpher.morph.MorphData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

public class CombatAnimations {
    private final MorphEntity entity;
    private AnimationController<MorphEntity> controller;

    private Hand lastSwingHand = Hand.MAIN_HAND;

    public CombatAnimations(MorphEntity entity) {
        this.entity = entity;
    }

    public void registerController(AnimatableManager.ControllerRegistrar registrar) {
        this.controller = new AnimationController<>(this.entity, "combat", 0, this::animate);
        registrar.add(this.controller);
    }

    private PlayState animate(AnimationState<MorphEntity> state) {
        PlayerEntity player = entity.getCurrentEntity();
        if (player == null) return PlayState.STOP;

        MorphData.PlayerMorphState morphState = MorphData.get(player);
        if (morphState.isDead || player.isSleeping()) return PlayState.STOP;

        if (entity.animationPath == null || entity.animationPath.isEmpty()) return PlayState.STOP;

        if (!entity.currentAnimation.equals("empty")) return PlayState.STOP;

        if (player.handSwinging) {
            state.getController().forceAnimationReset();

            if (state.getController().getAnimationState() == AnimationController.State.STOPPED) {
                Hand swingHand;
                if (player.isUsingItem()) {
                    swingHand = player.getActiveHand() == Hand.MAIN_HAND
                            ? Hand.OFF_HAND
                            : Hand.MAIN_HAND;
                } else {
                    swingHand = Hand.MAIN_HAND;
                }

                lastSwingHand = swingHand;

                boolean isMainHandSwing = swingHand == Hand.MAIN_HAND;
                boolean mainIsRight     = player.getMainArm() == Arm.RIGHT;
                boolean rightArmSwing   = (isMainHandSwing == mainIsRight);

                if (rightArmSwing) {
                    state.setAnimation(RawAnimation.begin().thenPlay("swing.main_hand"));
                } else {
                    state.setAnimation(RawAnimation.begin().thenPlay("swing.off_hand"));
                }
            }
            return PlayState.CONTINUE;
        }

        if (this.controller.getAnimationState() == AnimationController.State.STOPPED) {
            player.handSwinging = false;
            return PlayState.STOP;
        }

        return PlayState.CONTINUE;
    }

    public AnimationController<MorphEntity> getController() {
        return this.controller;
    }
}