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

public class MovementAnimations {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation SNEAKING = RawAnimation.begin().thenLoop("sneaking");
    private static final RawAnimation SNEAKING_WALK = RawAnimation.begin().thenLoop("sneaking.walk");
    private static final RawAnimation SLEEPING = RawAnimation.begin().thenLoop("sleep");
    private static final RawAnimation SITTING = RawAnimation.begin().thenLoop("sit");
    private static final RawAnimation SWIMMING = RawAnimation.begin().thenLoop("swimming");
    private static final RawAnimation ELYTRA = RawAnimation.begin().thenLoop("elytras");

    public static AnimationController<MorphEntity> register(MorphEntity me) {
        return new AnimationController<>(me, "poses", 4, as -> {
            PlayerEntity player = me.currentPlayer;
            if (player == null)
                return PlayState.STOP;

            MorphData.PlayerMorphState data = MorphData.get(player);

            if (data.useSpecialPose) {
                return PlayState.STOP;
            }

            if (me.animationPath == null || me.animationPath.isEmpty())
                return PlayState.STOP;

            ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
            if (chest.getItem() instanceof ElytraItem && player.isFallFlying() && me.currentAnimation.equals("empty")) {
                as.getController().setAnimationSpeed(1.0);
                return as.setAndContinue(ELYTRA);
            }

            if (player.isSleeping()) {
                return as.setAndContinue(SLEEPING);
            }

            boolean isSitting = player.hasVehicle() || player.getPose() == EntityPose.SITTING;
            if (isSitting) {
                as.getController().setAnimationSpeed(1.0);
                return as.setAndContinue(SITTING);
            }

            EntityPose pose = player.getPose();
            if (pose == EntityPose.SWIMMING || player.isSwimming()) {
                as.getController().setAnimationSpeed(1.0);
                return as.setAndContinue(SWIMMING);
            }

            boolean isMoving = (player.getX() - player.prevX != 0.0F || player.getZ() - player.prevZ != 0.0F)
                    || player.getVelocity().horizontalLength() > 0.01F;

            if (pose == EntityPose.CROUCHING || player.isSneaking()) {
                as.getController().setAnimationSpeed(1.0);
                if (isMoving) {
                    return as.setAndContinue(SNEAKING_WALK);
                } else {
                    return as.setAndContinue(SNEAKING);
                }
            }

            if (pose == EntityPose.STANDING && isMoving) {
                as.getController().setAnimationSpeed(1.0);
                return as.setAndContinue(WALK);
            }

            if (pose == EntityPose.STANDING) {
                as.getController().setAnimationSpeed(1.0);
                return as.setAndContinue(IDLE);
            }

            return PlayState.STOP;
        });
    }
}