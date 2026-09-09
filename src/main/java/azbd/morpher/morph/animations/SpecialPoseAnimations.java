package azbd.morpher.morph.animations;

import azbd.morpher.morph.MorphEntity;
import azbd.morpher.morph.MorphData;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

public class SpecialPoseAnimations {
    private static final RawAnimation IDLE       = RawAnimation.begin().thenLoop("specialpose.idle");
    private static final RawAnimation WALK       = RawAnimation.begin().thenLoop("specialpose.walk");
    private static final RawAnimation RUN        = RawAnimation.begin().thenLoop("specialpose.run");
    private static final RawAnimation SNEAKING   = RawAnimation.begin().thenLoop("specialpose.sneaking");
    private static final RawAnimation SNEAK_WALK = RawAnimation.begin().thenLoop("specialpose.sneak_walk");
    private static final RawAnimation SWIM       = RawAnimation.begin().thenLoop("specialpose.swim");
    private static final RawAnimation SIT        = RawAnimation.begin().thenLoop("specialpose.sit");
    private static final RawAnimation SLEEP      = RawAnimation.begin().thenLoop("specialpose.sleep");
    private static final RawAnimation ELYTRA     = RawAnimation.begin().thenLoop("specialpose.elytra");

    private static final float MOVE_THRESHOLD = 0.01f;

    public static AnimationController<MorphEntity> register(MorphEntity me) {
        return new AnimationController<>(me, "special_pose", 4, as -> {
            PlayerEntity player = me.currentPlayer;
            if (player == null) return PlayState.STOP;

            MorphData.PlayerMorphState data = MorphData.get(player);

            if (!data.useSpecialPose) return PlayState.STOP;

            if (me.animationPath == null || me.animationPath.isEmpty()) return PlayState.STOP;

            if (!me.currentAnimation.equals("empty")) return PlayState.STOP;

            as.getController().setAnimationSpeed(1.0);

            float limbSpeed = Math.min(player.limbAnimator.getSpeed(0f), 1f);
            boolean isMoving = limbSpeed > MOVE_THRESHOLD;

            ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
            if (chest.getItem() instanceof ElytraItem && player.isFallFlying()) {
                return as.setAndContinue(ELYTRA);
            }

            if (player.isSleeping()) {
                return as.setAndContinue(SLEEP);
            }

            EntityPose pose = player.getPose();
            if (player.isSwimming() || pose == EntityPose.SWIMMING) {
                return as.setAndContinue(SWIM);
            }

            if (player.hasVehicle() || pose == EntityPose.SITTING) {
                return as.setAndContinue(SIT);
            }

            boolean sneaking = player.isSneaking() || pose == EntityPose.CROUCHING;
            if (sneaking) {
                if (isMoving) {
                    return as.setAndContinue(SNEAK_WALK);
                }
                return as.setAndContinue(SNEAKING);
            }

            if (isMoving) {
                if (player.isSprinting()) {
                    return as.setAndContinue(RUN);
                }
                return as.setAndContinue(WALK);
            }

            return as.setAndContinue(IDLE);
        });
    }
}
