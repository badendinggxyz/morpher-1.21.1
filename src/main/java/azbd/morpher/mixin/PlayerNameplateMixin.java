package azbd.morpher.mixin;

import azbd.morpher.morph.MorphData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerNameplateMixin {

    private static final float DEFAULT_MORPH_SCALE = 0.9375F;

    @Shadow
    protected abstract void renderLabelIfPresent(AbstractClientPlayerEntity entity, Text text,
                                                 MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                 int light, float tickDelta);

    @Inject(method = "render", at = @At("TAIL"))
    private void morpher$renderVanillaNameplate(AbstractClientPlayerEntity player, float yaw, float tickDelta,
                                                 MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                 int light, CallbackInfo ci) {
        MorphData.PlayerMorphState state = MorphData.get(player);
        if (state.renderCustomModel && !player.isSpectator() && !state.isDead
                && ((EntityRendererInvoker) (Object) this).morpher$hasLabel(player)) {
            float automaticOffset = player.getHeight() * (state.scale - DEFAULT_MORPH_SCALE);
            matrices.push();
            matrices.translate(0.0, automaticOffset + state.nametagOffset, 0.0);
            this.renderLabelIfPresent(player, player.getDisplayName(), matrices, vertexConsumers, light, tickDelta);
            matrices.pop();
        }
    }
}
