package azbd.morpher.mixin;

import azbd.morpher.morph.MorphCache;
import azbd.morpher.morph.MorphData;
import azbd.morpher.morph.MorphEntity;
import azbd.morpher.render.MorphRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerRenderMixin<T extends LivingEntity> {

    @Inject(
            method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    public void onRender(T entity, float yaw, float partialTicks, MatrixStack matrices,
                         VertexConsumerProvider buffers, int light, CallbackInfo ci) {

        if (!(entity instanceof AbstractClientPlayerEntity player)) {
            return;
        }

        MorphData.PlayerMorphState state = MorphData.get(player);

        if (!state.renderCustomModel || player.isSpectator()) {
            MorphCache.removeRenderer(player.getUuid());
            return;
        }

        if (state.isDead) {
            return;
        }

        MorphRenderer renderer = MorphCache.getRenderer(player.getUuid());

        if (renderer != null && renderer.getCurrentEntity() != null && renderer.getCurrentEntity() != player) {
            MorphCache.removeRenderer(player.getUuid());
            renderer = null;
        }

        if (renderer == null) {
            renderer = MorphCache.getOrCreateRenderer(player.getUuid());
        }

        if (renderer == null) {
            return;
        }

        renderer.setCurrentEntity(player);
        MorphEntity morphEntity = (MorphEntity) renderer.getAnimatable();

        if (morphEntity != null) {
            morphEntity.setRenderer(renderer, player);
            renderer.render(player, yaw, partialTicks, matrices, buffers, light);
            ci.cancel();
        }
    }
}
