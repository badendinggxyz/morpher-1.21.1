package azbd.morpher.render.layer;

import azbd.morpher.morph.MorphData;
import azbd.morpher.morph.MorphEntity;
import azbd.morpher.render.MorphRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class AnimatedGeoLayer extends GeoRenderLayer<MorphEntity> {

    private final AnimatedLayerModel layerAnimModel = new AnimatedLayerModel();
    private final AnimatedLayerAnimatable animatableLayer = new AnimatedLayerAnimatable();
    private final Map<GeoBone, float[]> boneStateMap = new IdentityHashMap<>();

    public AnimatedGeoLayer(GeoRenderer<MorphEntity> entityRenderer) {
        super(entityRenderer);
    }

    @Override
    public void render(MatrixStack poseStack, MorphEntity animatableEntity, BakedGeoModel playerBakedModel,
                       RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {

        MorphRenderer morphRenderer = (MorphRenderer) this.getRenderer();
        if (morphRenderer.isRenderingLayer()) return;

        PlayerEntity player = animatableEntity.getCurrentEntity();
        if (player == null) return;

        MorphData.PlayerMorphState state = MorphData.get(player);
        if (!state.layerEnabled) return;

        List<String> allLayers = buildLayerList(state);
        if (allLayers.isEmpty()) return;

        String globalAnim = state.activeLayerAnimation;
        int version = state.layerAnimationVersion;

        for (String layerName : allLayers) {
            if (layerName == null || layerName.isEmpty() || "empty".equalsIgnoreCase(layerName)) continue;

            String activeAnim = state.layerAnimations.getOrDefault(layerName, globalAnim);
            layerAnimModel.setLayerName(layerName);
            animatableLayer.setCurrentAnim(activeAnim, version);

            Identifier modelId  = layerAnimModel.getModelResource(animatableLayer);
            Identifier textureId = layerAnimModel.getTextureResource(animatableLayer);

            BakedGeoModel layerGeoModel = layerAnimModel.getBakedModel(modelId);
            if (layerGeoModel == null) continue;

            RenderLayer layerRenderType = RenderLayer.getEntityTranslucent(textureId);
            VertexConsumer layerConsumer = bufferSource.getBuffer(layerRenderType);

            saveAndReset(layerGeoModel.topLevelBones());
            copyPlayerBones(playerBakedModel, layerGeoModel.topLevelBones());

            if (!activeAnim.isEmpty() && !"none".equalsIgnoreCase(activeAnim) && !"empty".equalsIgnoreCase(activeAnim)) {
                try {
                    float animTick = (float) animatableLayer.getTick(animatableLayer);
                    AnimationState<AnimatedLayerAnimatable> animState =
                            new AnimationState<>(animatableLayer, 0, 0, partialTick, false);
                    layerAnimModel.handleAnimations(animatableLayer, getLayerInstanceId(player, layerName), animState, animTick);
                } catch (Exception ignored) {}
            }

            poseStack.push();
            try {
                morphRenderer.renderDirectly(poseStack, animatableEntity, layerGeoModel, layerRenderType,
                        bufferSource, layerConsumer, false, partialTick, packedLight, packedOverlay,
                        0xFFFFFFFF);
            } finally {
                poseStack.pop();
            }

            restoreSnapshot(layerGeoModel.topLevelBones());
        }
    }

    private List<String> buildLayerList(MorphData.PlayerMorphState state) {
        List<String> list = new ArrayList<>();
        if (state.layerName != null && !state.layerName.isEmpty() && !"empty".equalsIgnoreCase(state.layerName)) {
            list.add(state.layerName);
        }
        if (state.extraLayers != null) {
            for (MorphData.ExtraLayer el : state.extraLayers) {
                if (el.name != null && !el.name.isEmpty() && !"empty".equalsIgnoreCase(el.name)) {
                    list.add(el.name);
                }
            }
        }
        return list;
    }

    private void saveAndReset(List<? extends GeoBone> bones) {
        for (GeoBone bone : bones) {
            boneStateMap.put(bone, new float[]{
                    bone.getRotX(),   bone.getRotY(),   bone.getRotZ(),
                    bone.getPosX(),   bone.getPosY(),   bone.getPosZ(),
                    bone.getScaleX(), bone.getScaleY(), bone.getScaleZ(),
                    bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()
            });

            var init = bone.getInitialSnapshot();
            bone.setRotX(init.getRotX());   bone.setRotY(init.getRotY());   bone.setRotZ(init.getRotZ());
            bone.markRotationAsChanged();
            bone.setPosX(init.getOffsetX()); bone.setPosY(init.getOffsetY()); bone.setPosZ(init.getOffsetZ());
            bone.markPositionAsChanged();
            bone.setScaleX(init.getScaleX()); bone.setScaleY(init.getScaleY()); bone.setScaleZ(init.getScaleZ());
            bone.markScaleAsChanged();

            saveAndReset(bone.getChildBones());
        }
    }

    private void restoreSnapshot(List<? extends GeoBone> bones) {
        for (GeoBone bone : bones) {
            float[] s = boneStateMap.remove(bone);
            if (s != null) {
                bone.setRotX(s[0]);   bone.setRotY(s[1]);   bone.setRotZ(s[2]);   bone.markRotationAsChanged();
                bone.setPosX(s[3]);   bone.setPosY(s[4]);   bone.setPosZ(s[5]);   bone.markPositionAsChanged();
                bone.setScaleX(s[6]); bone.setScaleY(s[7]); bone.setScaleZ(s[8]); bone.markScaleAsChanged();
                bone.setPivotX(s[9]); bone.setPivotY(s[10]); bone.setPivotZ(s[11]);
            }
            restoreSnapshot(bone.getChildBones());
        }
    }

    private void copyPlayerBones(BakedGeoModel playerModel, List<? extends GeoBone> layerBones) {
        if (playerModel == null || layerBones == null) return;
        for (GeoBone dst : layerBones) {
            playerModel.getBone(dst.getName()).ifPresent(src -> {
                dst.setPosX(src.getPosX());   dst.setPosY(src.getPosY());   dst.setPosZ(src.getPosZ());
                dst.setRotX(src.getRotX());   dst.setRotY(src.getRotY());   dst.setRotZ(src.getRotZ());
                dst.setScaleX(src.getScaleX()); dst.setScaleY(src.getScaleY()); dst.setScaleZ(src.getScaleZ());
                dst.setPivotX(src.getPivotX()); dst.setPivotY(src.getPivotY()); dst.setPivotZ(src.getPivotZ());
                dst.markRotationAsChanged();
                dst.markPositionAsChanged();
                dst.markScaleAsChanged();
            });
            copyPlayerBones(playerModel, dst.getChildBones());
        }
    }

    private long getLayerInstanceId(PlayerEntity player, String layerName) {
        long playerId = player == null ? 0L : player.getId();
        long layerId  = layerName == null ? 0L : layerName.hashCode();
        return (playerId << 32) ^ (layerId & 0xFFFFFFFFL);
    }
}
