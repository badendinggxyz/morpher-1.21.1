package azbd.morpher.mixin;

import azbd.morpher.MorpherMod;
import azbd.morpher.model.ArmorModel;
import azbd.morpher.model.PlayerModel;
import azbd.morpher.morph.MorphData;
import azbd.morpher.morph.MorphEntity;
import azbd.morpher.render.MorphRenderer;
import azbd.morpher.render.SkinResolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.world.LightType;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(PlayerEntityRenderer.class)
public abstract class ArmRenderMixin {

    @Unique
    private final Map<UUID, MorphRenderer> armRendererCache = new ConcurrentHashMap<>();

    @Inject(
            method = "renderArm",
            at = @At("HEAD"),
            cancellable = true
    )
    public void onRenderArm(MatrixStack matrices, VertexConsumerProvider buffers, int light,
                            AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve,
                            CallbackInfo ci) {

        MorphData.PlayerMorphState state = MorphData.get(player);
        UUID playerUuid = player.getUuid();

        if (!state.renderCustomModel || state.isDead) {
            armRendererCache.remove(playerUuid);
            return;
        }

        try {
            MorphRenderer renderer = armRendererCache.computeIfAbsent(playerUuid, uuid -> createRenderer());
            if (renderer == null) return;

            renderer.setCurrentEntity(player);
            MorphEntity animatable = (MorphEntity) renderer.getAnimatable();
            if (animatable == null) return;

            animatable.setRenderer(renderer, player);

            String modelName = SkinResolver.resolvePlayerModelPath(player, state);

            Identifier modelId = Identifier.of("morpher", "geo/" + modelName + ".geo.json");
            if (!MinecraftClient.getInstance().getResourceManager().getResource(modelId).isPresent()) {
                modelId = Identifier.of("morpher", "geo/player_wide.geo.json");
                if (!MinecraftClient.getInstance().getResourceManager().getResource(modelId).isPresent()) {
                    return;
                }
            }

            ci.cancel();

            PlayerModel playerModel = new PlayerModel();
            playerModel.getBakedModel(modelId);

            ArmorModel layerModel = null;
            String layerName = state.layerName;
            if (!layerName.isEmpty() && !layerName.equals("empty")) {
                layerModel = ArmorModel.createLayer();
                layerModel.setLayerName(layerName);
                layerModel.getBakedModel(Identifier.of("morpher", "geo/" + layerName + ".geo.json"));
            }

            PlayerEntityRenderer vanillaRenderer = (PlayerEntityRenderer)(Object)this;
            net.minecraft.client.render.entity.model.PlayerEntityModel<?> playerEntityModel =
                    (net.minecraft.client.render.entity.model.PlayerEntityModel<?>) vanillaRenderer.getModel();

            boolean isRightArmCall = (arm == playerEntityModel.rightArm || arm == playerEntityModel.rightSleeve);
            String boneName = isRightArmCall ? "right_arm" : "left_arm";

            Optional<GeoBone> playerBone = playerModel.getBone(boneName);
            Optional<GeoBone> layerBone  = layerModel != null ? layerModel.getBone(boneName) : Optional.empty();

            if (!playerBone.isPresent()) return;

            RenderLayer playerTexture = getPlayerTexture(state, player);
            RenderLayer layerTexture  = layerName.isEmpty()
                    ? RenderLayer.getEntityTranslucent(resolvePlayerSkin(state, player))
                    : RenderLayer.getEntityTranslucent(Identifier.of("morpher", "textures/" + layerName + ".png"));

            int packedLight   = calculateLight(player);
            int damageOverlay = getDamageOverlay(player);

            matrices.push();
            try {
                matrices.translate(-0.0125F, 1.5F, -0.012F);
                matrices.scale(1.0F, 1.0F, 1.0F);
                matrices.multiply(new Quaternionf().rotationXYZ(
                        (float) Math.toRadians(180.0F),
                        (float) Math.toRadians(180.0F),
                        (float) Math.toRadians(0.0F)
                ));

                GeoBone mainBone = playerBone.get();
                resetBoneToInitialSnapshot(mainBone);

                renderer.renderRecursively(
                        matrices, animatable, mainBone, playerTexture, buffers,
                        buffers.getBuffer(playerTexture), true, 1.0F,
                        packedLight, damageOverlay, 0xFFFFFFFF
                );

                if (layerBone.isPresent()) {
                    GeoBone layerGeoBone = layerBone.get();
                    resetBoneToInitialSnapshot(layerGeoBone);

                    renderer.renderRecursively(
                            matrices, animatable, layerGeoBone, layerTexture, buffers,
                            buffers.getBuffer(layerTexture), true, 1.0F,
                            packedLight, damageOverlay, 0xFFFFFFFF
                    );
                }

                if (state.extraLayers != null) {
                    for (MorphData.ExtraLayer extraLayer : state.extraLayers) {
                        if (extraLayer.name.isEmpty() || extraLayer.name.equals("empty")) continue;

                        ArmorModel extraLayerModel = ArmorModel.createLayer();
                        extraLayerModel.setLayerName(extraLayer.name);
                        Identifier extModelId = Identifier.of("morpher", "geo/" + extraLayer.name + ".geo.json");

                        if (!MinecraftClient.getInstance().getResourceManager().getResource(extModelId).isPresent()) continue;
                        extraLayerModel.getBakedModel(extModelId);

                        Optional<GeoBone> extLayerBone = extraLayerModel.getBone(boneName);
                        if (extLayerBone.isPresent()) {
                            Identifier extTexId;
                            if (!extraLayer.texture.isEmpty() && !extraLayer.texture.equals("empty")) {
                                extTexId = Identifier.of("morpher", "textures/" + extraLayer.texture + ".png");
                            } else {
                                extTexId = Identifier.of("morpher", "textures/" + extraLayer.name + ".png");
                            }

                            RenderLayer extLayerTexture = RenderLayer.getEntityTranslucent(extTexId);
                            GeoBone extGeoBone = extLayerBone.get();
                            resetBoneToInitialSnapshot(extGeoBone);

                            renderer.renderRecursively(
                                    matrices, animatable, extGeoBone, extLayerTexture, buffers,
                                    buffers.getBuffer(extLayerTexture), true, 1.0F,
                                    packedLight, damageOverlay, 0xFFFFFFFF
                            );
                        }
                    }
                }
            } finally {
                matrices.pop();
            }
        } catch (Throwable t) {
            MorpherMod.LOGGER.warn("[Morpher] First-person arm render deferred due to missing model or resource reload.", t);
        }
    }

    @Unique
    private int calculateLight(AbstractClientPlayerEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        net.minecraft.util.math.BlockPos pos = player.getBlockPos();

        int blockLight = mc.world.getLightLevel(LightType.BLOCK, pos);
        int skyLight   = mc.world.getLightLevel(LightType.SKY,   pos);

        if (blockLight == 0 && skyLight == 0) {
            int maxBlock = 0, maxSky = 0;
            for (net.minecraft.util.math.BlockPos n : new net.minecraft.util.math.BlockPos[]{
                    pos.up(), pos.north(), pos.south(), pos.east(), pos.west()
            }) {
                int bl = mc.world.getLightLevel(LightType.BLOCK, n);
                int sl = mc.world.getLightLevel(LightType.SKY,   n);
                if (bl > maxBlock) maxBlock = bl;
                if (sl > maxSky)   maxSky   = sl;
            }
            if (maxBlock > 0 || maxSky > 0) {
                blockLight = maxBlock;
                skyLight   = maxSky;
            }
        }

        return LightmapTextureManager.pack(blockLight, skyLight);
    }

    @Unique
    private void resetBoneToInitialSnapshot(GeoBone bone) {
        var init = bone.getInitialSnapshot();
        bone.setRotX(init.getRotX()); bone.setRotY(init.getRotY()); bone.setRotZ(init.getRotZ());
        bone.markRotationAsChanged();
        bone.setPosX(init.getOffsetX()); bone.setPosY(init.getOffsetY()); bone.setPosZ(init.getOffsetZ());
        bone.markPositionAsChanged();
        bone.setScaleX(init.getScaleX()); bone.setScaleY(init.getScaleY()); bone.setScaleZ(init.getScaleZ());
        bone.markScaleAsChanged();
        for (GeoBone child : bone.getChildBones()) resetBoneToInitialSnapshot(child);
    }

    @Unique
    private void resetBoneRecursively(GeoBone bone) {
        bone.setRotX(0.0F); bone.setRotY(0.0F); bone.setRotZ(0.0F);
        bone.setPosX(0.0F); bone.setPosY(0.0F); bone.setPosZ(0.0F);
        bone.setScaleX(1.0F); bone.setScaleY(1.0F); bone.setScaleZ(1.0F);
        for (GeoBone child : bone.getChildBones()) resetBoneRecursively(child);
    }

    @Unique
    private RenderLayer getPlayerTexture(MorphData.PlayerMorphState state,
                                         AbstractClientPlayerEntity player) {
        if (state.texture.isEmpty())
            return RenderLayer.getEntityTranslucent(resolvePlayerSkin(state, player));
        return RenderLayer.getEntityTranslucent(
                Identifier.of("morpher", "textures/" + state.texture + ".png"));
    }

    @Unique
    private Identifier resolvePlayerSkin(MorphData.PlayerMorphState state,
                                        AbstractClientPlayerEntity player) {
        if (state.skinPlayerName != null && !state.skinPlayerName.isEmpty()) {
            Identifier skin = SkinResolver.getSkin(state.skinPlayerName);
            if (skin != null) return skin;
        }
        return player.getSkinTextures().texture();
    }

    @Unique
    private int getDamageOverlay(AbstractClientPlayerEntity player) {
        int hurtTime = player.hurtTime;
        if (hurtTime <= 0) return OverlayTexture.DEFAULT_UV;
        float intensity = (float) hurtTime / 10.0F;
        return OverlayTexture.packUv((int)(intensity * 3.0F), 3);
    }

    @Unique
    private MorphRenderer createRenderer() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
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
