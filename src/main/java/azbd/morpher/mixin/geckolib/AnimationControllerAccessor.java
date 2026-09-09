package azbd.morpher.mixin.geckolib;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.EasingType;
import software.bernie.geckolib.animation.keyframe.BoneAnimationQueue;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.Map;
import java.util.function.Function;

import software.bernie.geckolib.animatable.GeoAnimatable;

@Mixin(value = AnimationController.class, remap = false)
public interface AnimationControllerAccessor<T extends GeoAnimatable> {

    @Accessor("boneAnimationQueues")
    Map<String, BoneAnimationQueue> getBoneAnimationQueues();

    @Accessor("isJustStarting")
    void setJustStarting(boolean starting);

    @Accessor("overrideEasingTypeFunction")
    Function<T, EasingType> getOverrideEasingTypeFunction();

    @Invoker("process")
    void invokeProcess(GeoModel<T> model, AnimationState<T> state, Map<String, GeoBone> bones, Map<String, BoneSnapshot> boneSnapshots, double animTime, boolean crashWhenCantFindBone);
}
