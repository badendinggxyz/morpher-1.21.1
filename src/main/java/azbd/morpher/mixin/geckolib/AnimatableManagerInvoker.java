package azbd.morpher.mixin.geckolib;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import software.bernie.geckolib.animation.AnimatableManager;

@Mixin(value={AnimatableManager.class}, remap = false)
public interface AnimatableManagerInvoker {
    @Invoker(value="finishFirstTick")
    void invokeFinishFirstTick();
}
