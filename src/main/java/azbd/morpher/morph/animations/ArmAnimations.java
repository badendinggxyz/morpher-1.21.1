package azbd.morpher.morph.animations;

import azbd.morpher.morph.MorphEntity;
import azbd.morpher.morph.MorphData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

public class ArmAnimations {
    private static final RawAnimation LEFT_TRIDENT = RawAnimation.begin().thenLoop("trident_leftArm");
    private static final RawAnimation LEFT_BOW = RawAnimation.begin().thenLoop("bow_leftArm");
    private static final RawAnimation LEFT_CROSSBOW = RawAnimation.begin().thenLoop("crossbow_leftArm");
    private static final RawAnimation LEFT_CROSSBOW_CHARGE = RawAnimation.begin().thenLoop("crossbow_charge_leftArm");
    private static final RawAnimation LEFT_SHIELD = RawAnimation.begin().thenLoop("shield_leftArm");
    private static final RawAnimation LEFT_SPYGLASS = RawAnimation.begin().thenLoop("spyglass_leftArm");
    private static final RawAnimation LEFT_BRUSH = RawAnimation.begin().thenLoop("brush_leftArm");
    private static final RawAnimation LEFT_HORN = RawAnimation.begin().thenLoop("goat_horn_leftArm");
    private static final RawAnimation LEFT_ITEM = RawAnimation.begin().thenLoop("item_leftArm");

    private static final RawAnimation RIGHT_TRIDENT = RawAnimation.begin().thenLoop("trident_rightArm");
    private static final RawAnimation RIGHT_BOW = RawAnimation.begin().thenLoop("bow_rightArm");
    private static final RawAnimation RIGHT_CROSSBOW = RawAnimation.begin().thenLoop("crossbow_rightArm");
    private static final RawAnimation RIGHT_CROSSBOW_CHARGE = RawAnimation.begin().thenLoop("crossbow_charge_rightArm");
    private static final RawAnimation RIGHT_SHIELD = RawAnimation.begin().thenLoop("shield_rightArm");
    private static final RawAnimation RIGHT_SPYGLASS = RawAnimation.begin().thenLoop("spyglass_rightArm");
    private static final RawAnimation RIGHT_BRUSH = RawAnimation.begin().thenLoop("brush_rightArm");
    private static final RawAnimation RIGHT_HORN = RawAnimation.begin().thenLoop("goat_horn_rightArm");
    private static final RawAnimation RIGHT_ITEM = RawAnimation.begin().thenLoop("item_rightArm");

    public static AnimationController<MorphEntity> registerLeft(MorphEntity me) {
        return register(me, "left_arm", Arm.LEFT,
                LEFT_TRIDENT, LEFT_BOW, LEFT_CROSSBOW, LEFT_CROSSBOW_CHARGE,
                LEFT_SHIELD, LEFT_SPYGLASS, LEFT_BRUSH, LEFT_HORN,
                LEFT_ITEM);
    }

    public static AnimationController<MorphEntity> registerRight(MorphEntity me) {
        return register(me, "right_arm", Arm.RIGHT,
                RIGHT_TRIDENT, RIGHT_BOW, RIGHT_CROSSBOW, RIGHT_CROSSBOW_CHARGE,
                RIGHT_SHIELD, RIGHT_SPYGLASS, RIGHT_BRUSH, RIGHT_HORN,
                RIGHT_ITEM);
    }

    private static AnimationController<MorphEntity> register(
            MorphEntity me, String name, Arm arm,
            RawAnimation trident, RawAnimation bow, RawAnimation crossbow, RawAnimation crossbowCharge,
            RawAnimation shield, RawAnimation spyglass, RawAnimation brush, RawAnimation horn,
            RawAnimation itemAnimation) {

        return new AnimationController<>(me, name, 3, as -> {
            PlayerEntity player = me.currentPlayer;
            if (player == null) return PlayState.STOP;

            if (me.animationPath == null || me.animationPath.isEmpty()) return PlayState.STOP;

            if (!me.currentAnimation.equals("empty")) {
                return PlayState.STOP;
            }

            if (player.isSleeping()) {
                return PlayState.STOP;
            }

            MorphData.PlayerMorphState data = MorphData.get(player);

            Arm mainArm = player.getMainArm();
            Hand thisHand;
            if (arm == Arm.RIGHT) {
                thisHand = (mainArm == Arm.RIGHT) ? Hand.MAIN_HAND : Hand.OFF_HAND;
            } else {
                thisHand = (mainArm == Arm.LEFT) ? Hand.MAIN_HAND : Hand.OFF_HAND;
            }

            ItemStack itemStack = player.getStackInHand(thisHand);
            if (itemStack.isEmpty()) {
                return PlayState.STOP;
            }

            boolean usingThisHand = player.isUsingItem() && player.getActiveHand() == thisHand;

            Item item = itemStack.getItem();
            UseAction useAction = itemStack.getUseAction();

            if (usingThisHand) {
                if (item instanceof TridentItem && player.getItemUseTime() > 10) {
                    as.getController().setAnimationSpeed(1.0);
                    return as.setAndContinue(trident);
                }

                if (item instanceof BowItem) {
                    as.getController().setAnimationSpeed(1.0);
                    return as.setAndContinue(bow);
                }

                if (item instanceof CrossbowItem) {
                    as.getController().setAnimationSpeed(1.0);
                    if (player.getItemUseTime() > 0) {
                        return as.setAndContinue(crossbowCharge);
                    }
                    return as.setAndContinue(crossbow);
                }

                if (item instanceof ShieldItem) {
                    as.getController().setAnimationSpeed(1.0);
                    return as.setAndContinue(shield);
                }

                if (itemStack.isOf(Items.SPYGLASS)) {
                    as.getController().setAnimationSpeed(1.0);
                    return as.setAndContinue(spyglass);
                }

                if (item instanceof BrushItem) {
                    as.getController().setAnimationSpeed(1.0);
                    return as.setAndContinue(brush);
                }

                if (item instanceof GoatHornItem) {
                    as.getController().setAnimationSpeed(1.0);
                    return as.setAndContinue(horn);
                }

            }

            if (item instanceof CrossbowItem && CrossbowItem.isCharged(itemStack)) {
                as.getController().setAnimationSpeed(1.0);
                return as.setAndContinue(crossbow);
            }

            as.getController().setAnimationSpeed(1.0);
            return as.setAndContinue(itemAnimation);
        });
    }
}
