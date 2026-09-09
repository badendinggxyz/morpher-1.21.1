package azbd.morpher.mixin.geckolib;

import azbd.morpher.morph.MorphEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.EasingType;
import software.bernie.geckolib.animation.keyframe.AnimationPoint;
import software.bernie.geckolib.animation.keyframe.BoneAnimationQueue;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import net.minecraft.util.math.MathHelper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mixin(value={AnimationProcessor.class}, remap = false)
public abstract class MixinAnimationProcessor<T extends GeoAnimatable> {
    @Shadow
    public boolean reloadAnimations;
    @Shadow
    @Final
    private Map<String, GeoBone> bones;

    @Shadow
    protected abstract Map<String, BoneSnapshot> updateBoneSnapshots(Map<String, BoneSnapshot> var1);

    @Shadow
    public abstract Collection<GeoBone> getRegisteredBones();

    @Shadow
    protected abstract void resetBoneTransformationMarkers();

    @Unique
    private void customTickImpl(MorphEntity animatable, GeoModel<MorphEntity> model, AnimatableManager<MorphEntity> animatableManager, double animTime, AnimationState<MorphEntity> event, boolean crashWhenCantFindBone) {
        GeoBone bone;
        Map<String, BoneSnapshot> boneSnapshots = this.updateBoneSnapshots(animatableManager.getBoneSnapshotCollection());

        List<AnimationController<MorphEntity>> animationControllers = animatableManager.getAnimationControllers().values().stream().map(v -> (AnimationController<MorphEntity>) v).collect(Collectors.toList());

        for (AnimationController<MorphEntity> controller : animationControllers) {
            AnimationControllerAccessor<MorphEntity> accessor = (AnimationControllerAccessor<MorphEntity>) (Object) controller;
            if (this.reloadAnimations) {
                controller.forceAnimationReset();
                accessor.getBoneAnimationQueues().clear();
            }
            accessor.setJustStarting(animatableManager.isFirstTick());
            event.withController(controller);
            accessor.invokeProcess(model, event, this.bones, boneSnapshots, animTime, crashWhenCantFindBone);

            for (BoneAnimationQueue boneAnimation : accessor.getBoneAnimationQueues().values()) {
                bone = boneAnimation.bone();
                BoneSnapshot initialSnapshot = bone.getInitialSnapshot();
                if (boneAnimation.rotationXQueue().isEmpty() || boneAnimation.rotationYQueue().isEmpty() || boneAnimation.rotationZQueue().isEmpty()) continue;
                bone.setRotX(initialSnapshot.getRotX());
                bone.setRotY(initialSnapshot.getRotY());
                bone.setRotZ(initialSnapshot.getRotZ());
            }
        }

        for (AnimationController<MorphEntity> controller : animationControllers) {
            AnimationControllerAccessor<MorphEntity> accessor = (AnimationControllerAccessor<MorphEntity>) (Object) controller;
            for (BoneAnimationQueue boneAnimation : accessor.getBoneAnimationQueues().values()) {
                bone = boneAnimation.bone();
                BoneSnapshot snapshot = boneSnapshots.get(bone.getName());
                AnimationPoint rotXPoint = (AnimationPoint)boneAnimation.rotationXQueue().poll();
                AnimationPoint rotYPoint = (AnimationPoint)boneAnimation.rotationYQueue().poll();
                AnimationPoint rotZPoint = (AnimationPoint)boneAnimation.rotationZQueue().poll();
                AnimationPoint posXPoint = (AnimationPoint)boneAnimation.positionXQueue().poll();
                AnimationPoint posYPoint = (AnimationPoint)boneAnimation.positionYQueue().poll();
                AnimationPoint posZPoint = (AnimationPoint)boneAnimation.positionZQueue().poll();
                AnimationPoint scaleXPoint = (AnimationPoint)boneAnimation.scaleXQueue().poll();
                AnimationPoint scaleYPoint = (AnimationPoint)boneAnimation.scaleYQueue().poll();
                AnimationPoint scaleZPoint = (AnimationPoint)boneAnimation.scaleZQueue().poll();
                EasingType easingType = accessor.getOverrideEasingTypeFunction().apply(animatable);

                if (rotXPoint != null && rotYPoint != null && rotZPoint != null) {
                    bone.setRotX((float)EasingType.lerpWithOverride(rotXPoint, easingType) + bone.getRotX());
                    bone.setRotY((float)EasingType.lerpWithOverride(rotYPoint, easingType) + bone.getRotY());
                    bone.setRotZ((float)EasingType.lerpWithOverride(rotZPoint, easingType) + bone.getRotZ());
                    snapshot.updateRotation(bone.getRotX(), bone.getRotY(), bone.getRotZ());
                    snapshot.startRotAnim();
                    bone.markRotationAsChanged();
                }
                if (posXPoint != null && posYPoint != null && posZPoint != null) {
                    bone.setPosX((float)EasingType.lerpWithOverride(posXPoint, easingType));
                    bone.setPosY((float)EasingType.lerpWithOverride(posYPoint, easingType));
                    bone.setPosZ((float)EasingType.lerpWithOverride(posZPoint, easingType));
                    snapshot.updateOffset(bone.getPosX(), bone.getPosY(), bone.getPosZ());
                    snapshot.startPosAnim();
                    bone.markPositionAsChanged();
                }
                if (scaleXPoint != null && scaleYPoint != null && scaleZPoint != null) {
                    bone.setScaleX((float)EasingType.lerpWithOverride(scaleXPoint, easingType));
                    bone.setScaleY((float)EasingType.lerpWithOverride(scaleYPoint, easingType));
                    bone.setScaleZ((float)EasingType.lerpWithOverride(scaleZPoint, easingType));
                    snapshot.updateScale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
                    snapshot.startScaleAnim();
                    bone.markScaleAsChanged();
                }
            }
        }

        this.reloadAnimations = false;
        double resetTickLength = animatable.getBoneResetTime();

        for (GeoBone bone2 : this.getRegisteredBones()) {
            double percentageReset;
            BoneSnapshot saveSnapshot;
            BoneSnapshot initialSnapshot;
            if (!bone2.hasRotationChanged()) {
                initialSnapshot = bone2.getInitialSnapshot();
                saveSnapshot = boneSnapshots.get(bone2.getName());
                if (saveSnapshot.isRotAnimInProgress()) {
                    saveSnapshot.stopRotAnim(animTime);
                }
                percentageReset = Math.min((animTime - saveSnapshot.getLastResetRotationTick()) / resetTickLength, 1.0);
                bone2.setRotX((float)MathHelper.lerp(percentageReset, saveSnapshot.getRotX(), initialSnapshot.getRotX()));
                bone2.setRotY((float)MathHelper.lerp(percentageReset, saveSnapshot.getRotY(), initialSnapshot.getRotY()));
                bone2.setRotZ((float)MathHelper.lerp(percentageReset, saveSnapshot.getRotZ(), initialSnapshot.getRotZ()));
                if (percentageReset >= 1.0) {
                    saveSnapshot.updateRotation(bone2.getRotX(), bone2.getRotY(), bone2.getRotZ());
                }
            }
            if (!bone2.hasPositionChanged()) {
                initialSnapshot = bone2.getInitialSnapshot();
                saveSnapshot = boneSnapshots.get(bone2.getName());
                if (saveSnapshot.isPosAnimInProgress()) {
                    saveSnapshot.stopPosAnim(animTime);
                }
                percentageReset = Math.min((animTime - saveSnapshot.getLastResetPositionTick()) / resetTickLength, 1.0);
                bone2.setPosX((float)MathHelper.lerp(percentageReset, saveSnapshot.getOffsetX(), initialSnapshot.getOffsetX()));
                bone2.setPosY((float)MathHelper.lerp(percentageReset, saveSnapshot.getOffsetY(), initialSnapshot.getOffsetY()));
                bone2.setPosZ((float)MathHelper.lerp(percentageReset, saveSnapshot.getOffsetZ(), initialSnapshot.getOffsetZ()));
                if (percentageReset >= 1.0) {
                    saveSnapshot.updateOffset(bone2.getPosX(), bone2.getPosY(), bone2.getPosZ());
                }
            }
            if (!bone2.hasScaleChanged()) {
                initialSnapshot = bone2.getInitialSnapshot();
                saveSnapshot = boneSnapshots.get(bone2.getName());
                if (saveSnapshot.isScaleAnimInProgress()) {
                    saveSnapshot.stopScaleAnim(animTime);
                }
                percentageReset = Math.min((animTime - saveSnapshot.getLastResetScaleTick()) / resetTickLength, 1.0);
                bone2.setScaleX((float)MathHelper.lerp(percentageReset, saveSnapshot.getScaleX(), initialSnapshot.getScaleX()));
                bone2.setScaleY((float)MathHelper.lerp(percentageReset, saveSnapshot.getScaleY(), initialSnapshot.getScaleY()));
                bone2.setScaleZ((float)MathHelper.lerp(percentageReset, saveSnapshot.getScaleZ(), initialSnapshot.getScaleZ()));
                if (percentageReset >= 1.0) {
                    saveSnapshot.updateScale(bone2.getScaleX(), bone2.getScaleY(), bone2.getScaleZ());
                }
            }
        }

        this.resetBoneTransformationMarkers();
        ((AnimatableManagerInvoker)animatableManager).invokeFinishFirstTick();
    }

    @Inject(method="tickAnimation", at=@At("HEAD"), cancellable=true)
    public void onTickAnimationController(GeoAnimatable animatable, GeoModel<T> model, AnimatableManager<T> animatableManager, double animTime, AnimationState<T> state, boolean crashWhenCantFindBone, CallbackInfo ci) {
        if (animatable instanceof MorphEntity morpherEntity) {
            this.customTickImpl(morpherEntity, (GeoModel<MorphEntity>) model, (AnimatableManager<MorphEntity>) animatableManager, animTime, (AnimationState<MorphEntity>) state, crashWhenCantFindBone);
            ci.cancel();
        }
    }
}
