package azbd.morpher.mixin;

import azbd.morpher.morph.MorphData;
import azbd.morpher.morph.MorphEntity;
import azbd.morpher.render.MorphRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(InventoryScreen.class)
public abstract class InventoryMixin {

    @Unique
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Unique
    private static final Map<UUID, MorphRenderer> guiRendererCache = new ConcurrentHashMap<>();

    @Inject(
            method = "drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIIFFFLnet/minecraft/entity/LivingEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void onDrawEntity(DrawContext context, int x1, int y1, int x2, int y2, int size,
                                     float renderScale, float mouseX, float mouseY,
                                     LivingEntity entity, CallbackInfo ci) {

        if (!(entity instanceof AbstractClientPlayerEntity player)) return;
        if (player != mc.player) return;

        MorphData.PlayerMorphState playerState = MorphData.get(player);

        if (playerState == null || !playerState.renderCustomModel || player.isSpectator()) {
            guiRendererCache.remove(player.getUuid());
            return;
        }

        ci.cancel();

        MorphRenderer renderer = guiRendererCache.get(player.getUuid());

        if (renderer != null && renderer.getCurrentEntity() != null && renderer.getCurrentEntity() != player) {
            guiRendererCache.remove(player.getUuid());
            renderer = null;
        }

        if (renderer == null) {
            renderer = guiRendererCache.computeIfAbsent(
                    player.getUuid(),
                    uuid -> createRenderer()
            );
        }

        if (renderer == null || renderer.getAnimatable() == null) {
            renderVanilla(context, x1, y1, x2, y2, size, renderScale, mouseX, mouseY, player);
            return;
        }

        renderer.setCurrentEntity(player);
        ((MorphEntity) renderer.getAnimatable()).setRenderer(renderer, player);
        renderer.setGuiRender(true);

        float centerX = (float)(x1 + x2) / 2.0f;
        float centerY = (float)(y1 + y2) / 2.0f;
        float mouseDx = (float)Math.atan((centerX - mouseX) / 40.0f);
        float mouseDy = (float)Math.atan((mouseY - centerY) / 40.0f);

        Quaternionf rotation = new Quaternionf()
                .rotateZ((float)Math.PI)
                .rotateY((float)(-Math.PI))
                .rotateX(mouseDy * 20.0f * ((float)Math.PI / 180));

        float prevBodyYaw = entity.bodyYaw;
        float bodyYaw     = entity.bodyYaw;
        float prevYaw     = entity.prevYaw;
        float entityYaw   = entity.getYaw();
        float prevPitch   = entity.prevPitch;
        float entityPitch = entity.getPitch();
        float prevHeadYaw = entity.prevHeadYaw;
        float headYaw     = entity.headYaw;

        entity.bodyYaw = entity.prevBodyYaw = mouseDx * 20.0f;
        entity.setYaw(mouseDx * 40.0f);
        entity.prevYaw = entity.getYaw();
        entity.setPitch(mouseDy * 20.0f);
        entity.prevPitch = entity.getPitch();
        entity.headYaw = entity.getYaw();
        entity.prevHeadYaw = entity.getYaw();

        Vector3f transform = new Vector3f(
                0.0f,
                entity.getHeight() / 2.0f + renderScale,
                0.0f
        );

        context.enableScissor(x1, y1, x2, y2);

        try {
            var matrices = context.getMatrices();
            matrices.push();

            matrices.translate((int)centerX, (int)centerY, 150.0);
            matrices.scale(1.0f, 1.0f, -1.0f);

            float scale = (float)size;
            matrices.scale(scale, scale, scale);
            matrices.translate(transform.x, transform.y, transform.z);
            matrices.multiply(rotation);

            DiffuseLighting.disableGuiDepthLighting();
            VertexConsumerProvider.Immediate vertexConsumers = context.getVertexConsumers();

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            DiffuseLighting.enableGuiDepthLighting();

            float delta = mc.getRenderTickCounter().getTickDelta(false);
            int lightmap = LightmapTextureManager.pack(15, 15);

            renderer.render(player, 0.0f, delta, matrices, vertexConsumers, lightmap);

            vertexConsumers.draw();

            DiffuseLighting.disableGuiDepthLighting();
            RenderSystem.disableDepthTest();

            matrices.pop();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            renderer.setGuiRender(false);
        }

        context.disableScissor();

        entity.bodyYaw     = prevBodyYaw;
        entity.prevBodyYaw = bodyYaw;
        entity.prevYaw     = prevYaw;
        entity.setYaw(entityYaw);
        entity.prevPitch   = prevPitch;
        entity.setPitch(entityPitch);
        entity.prevHeadYaw = prevHeadYaw;
        entity.headYaw     = headYaw;
    }

    @Unique
    private static void renderVanilla(DrawContext context, int x1, int y1, int x2, int y2,
                                      int size, float renderScale, float mouseX, float mouseY,
                                      AbstractClientPlayerEntity player) {

        float centerX = (float)(x1 + x2) / 2.0f;
        float centerY = (float)(y1 + y2) / 2.0f;
        float mouseDx = (float)Math.atan((centerX - mouseX) / 40.0f);
        float mouseDy = (float)Math.atan((mouseY - centerY) / 40.0f);

        Quaternionf rotation = new Quaternionf()
                .rotateZ((float)Math.PI)
                .rotateY((float)(-Math.PI))
                .rotateX(mouseDy * 20.0f * ((float)Math.PI / 180));

        float prevBodyYaw = player.bodyYaw;
        float bodyYaw     = player.bodyYaw;
        float prevYaw     = player.prevYaw;
        float entityYaw   = player.getYaw();
        float prevPitch   = player.prevPitch;
        float entityPitch = player.getPitch();
        float prevHeadYaw = player.prevHeadYaw;
        float headYaw     = player.headYaw;

        player.bodyYaw = player.prevBodyYaw = mouseDx * 20.0f;
        player.setYaw(mouseDx * 40.0f);
        player.prevYaw = player.getYaw();
        player.setPitch(mouseDy * 20.0f);
        player.prevPitch = player.getPitch();
        player.headYaw = player.getYaw();
        player.prevHeadYaw = player.getYaw();

        Vector3f transform = new Vector3f(
                0.0f,
                player.getHeight() / 2.0f + renderScale,
                0.0f
        );

        context.enableScissor(x1, y1, x2, y2);

        InventoryScreen.drawEntity(
                context,
                centerX,
                centerY,
                (float)size,
                transform,
                rotation,
                rotation,
                player
        );

        context.disableScissor();

        player.bodyYaw     = prevBodyYaw;
        player.prevBodyYaw = bodyYaw;
        player.prevYaw     = prevYaw;
        player.setYaw(entityYaw);
        player.prevPitch   = prevPitch;
        player.setPitch(entityPitch);
        player.prevHeadYaw = prevHeadYaw;
        player.headYaw     = headYaw;
    }

    @Unique
    private static MorphRenderer createRenderer() {
        try {
            EntityRendererFactory.Context context = new EntityRendererFactory.Context(
                    mc.getEntityRenderDispatcher(),
                    mc.getItemRenderer(),
                    mc.getBlockRenderManager(),
                    mc.getEntityRenderDispatcher().getHeldItemRenderer(),
                    mc.getResourceManager(),
                    mc.getEntityModelLoader(),
                    mc.textRenderer
            );
            return new MorphRenderer(context);
        } catch (Exception e) {
            return null;
        }
    }
}
