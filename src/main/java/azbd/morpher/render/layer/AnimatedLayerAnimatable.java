package azbd.morpher.render.layer;

import net.minecraft.client.MinecraftClient;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class AnimatedLayerAnimatable implements GeoAnimatable {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private String currentAnim = "";
    private String lastAnim = "";
    private int currentVersion = 0;
    private int lastVersion = -1;

    public void setCurrentAnim(String anim, int version) {
        this.currentAnim = (anim == null) ? "" : anim;
        this.currentVersion = version;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<AnimatedLayerAnimatable> ctrl =
                new AnimationController<>(this, "layer_anim_controller", 0, state -> {

                    if (currentAnim.isEmpty()
                            || "none".equalsIgnoreCase(currentAnim)
                            || "empty".equalsIgnoreCase(currentAnim)) {
                        lastAnim = "";
                        lastVersion = currentVersion;
                        return PlayState.STOP;
                    }

                    if (!currentAnim.equals(lastAnim) || currentVersion != lastVersion) {
                        lastAnim = currentAnim;
                        lastVersion = currentVersion;
                        state.getController().forceAnimationReset();
                        state.getController().setAnimation(RawAnimation.begin().thenLoop(currentAnim));
                    }

                    return PlayState.CONTINUE;
                });

        controllers.add(ctrl);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public double getTick(Object animatable) {
        return MinecraftClient.getInstance().world != null
                ? MinecraftClient.getInstance().world.getTime()
                : 0;
    }
}
