package azbd.morpher.render.handlers;

import azbd.morpher.morph.MorphEntity;
import azbd.morpher.render.MorphRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ArmorAutoHandler {

    private final MorphRenderer renderer;

    public ArmorAutoHandler(MorphRenderer renderer) {
        this.renderer = renderer;
    }

    public boolean renderForSlot(MatrixStack matrices, PlayerEntity player, MorphEntity entity,
                                 VertexConsumerProvider buffers, float partialTick,
                                 int light, int overlay, ItemStack stack, EquipmentSlot slot) {
        if (!(stack.getItem() instanceof GeoItem)) return false;
        return tryRenderGeoArmorFromOtherMod(matrices, player, entity, buffers,
                partialTick, light, overlay, stack, slot);
    }

    public boolean renderOnArm(MatrixStack matrices, PlayerEntity player, MorphEntity entity,
                               VertexConsumerProvider buffers, float partialTick,
                               int light, int overlay, ItemStack stack, Arm arm) {
        if (!(stack.getItem() instanceof GeoItem)) return false;
        return tryRenderGeoArmorFromOtherModOnArm(matrices, player, entity, buffers,
                partialTick, light, overlay, stack, arm);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean tryRenderGeoArmorFromOtherMod(MatrixStack matrices, PlayerEntity player, MorphEntity entity,
                                                  VertexConsumerProvider buffers, float partialTick,
                                                  int light, int overlay, ItemStack stack, EquipmentSlot slot) {
        try {
            GeoRenderProvider renderProvider = GeoRenderProvider.of(stack);
            if (renderProvider == null) return false;

            BipedEntityModel<PlayerEntity> dummyBase = getDummyBaseModel();
            if (dummyBase == null) return false;

            BipedEntityModel<?> rawModel = renderProvider.getGeoArmorRenderer(player, stack, slot, dummyBase);
            if (!(rawModel instanceof GeoArmorRenderer<?>)) return false;

            GeoArmorRenderer<?> geoRenderer = (GeoArmorRenderer<?>) rawModel;
            GeoModel geoModel = geoRenderer.getGeoModel();
            if (geoModel == null) return false;

            GeoItem geoItem = (GeoItem) stack.getItem();
            Identifier modelId = geoModel.getModelResource(geoItem);
            Identifier textureId = geoModel.getTextureResource(geoItem);
            if (modelId == null || textureId == null) return false;

            BakedGeoModel bakedModel = geoModel.getBakedModel(modelId);
            if (bakedModel == null) return false;

            RenderLayer renderLayer = RenderLayer.getArmorCutoutNoCull(textureId);
            VertexConsumer buffer = ItemRenderer.getArmorGlintConsumer(buffers, renderLayer, stack.hasGlint());

            BakedGeoModel srcModel = renderer.getPlayerModel()
                    .getBakedModel(renderer.getPlayerModel().getModelResource(entity));

            saveAndReset(bakedModel.topLevelBones());
            try {
                copyRootAnimations(srcModel, bakedModel.topLevelBones());

                applyBoneVisibilityForSlot(bakedModel, slot);

                renderer.renderDirectly(matrices, entity, bakedModel, renderLayer, buffers,
                        buffer, false, partialTick, light, overlay, 0xFFFFFFFF);
            } finally {
                restoreSnapshot(bakedModel.topLevelBones());
            }
            return true;
        } catch (Throwable t) {
            System.err.println("[ArmorAutoHandler] Error rendering external Geo armor in slot " + slot + ": " + t.getMessage());
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean tryRenderGeoArmorFromOtherModOnArm(MatrixStack matrices, PlayerEntity player, MorphEntity entity,
                                                       VertexConsumerProvider buffers, float partialTick,
                                                       int light, int overlay, ItemStack stack, Arm arm) {
        try {
            GeoRenderProvider renderProvider = GeoRenderProvider.of(stack);
            if (renderProvider == null) return false;

            BipedEntityModel<PlayerEntity> dummyBase = getDummyBaseModel();
            if (dummyBase == null) return false;

            BipedEntityModel<?> rawModel = renderProvider.getGeoArmorRenderer(player, stack, EquipmentSlot.CHEST, dummyBase);
            if (!(rawModel instanceof GeoArmorRenderer<?>)) return false;

            GeoArmorRenderer<?> geoRenderer = (GeoArmorRenderer<?>) rawModel;
            GeoModel geoModel = geoRenderer.getGeoModel();
            if (geoModel == null) return false;

            GeoItem geoItem = (GeoItem) stack.getItem();
            Identifier modelId = geoModel.getModelResource(geoItem);
            Identifier textureId = geoModel.getTextureResource(geoItem);
            if (modelId == null || textureId == null) return false;

            BakedGeoModel bakedModel = geoModel.getBakedModel(modelId);
            if (bakedModel == null) return false;

            GeoBone armBone = getBoneFromBakedModel(bakedModel, arm == Arm.RIGHT ? "right_arm" : "left_arm");
            if (armBone == null) return false;

            RenderLayer renderLayer = RenderLayer.getArmorCutoutNoCull(textureId);
            VertexConsumer buffer = ItemRenderer.getArmorGlintConsumer(buffers, renderLayer, stack.hasGlint());

            saveAndReset(bakedModel.topLevelBones());
            try {
                matrices.push();
                matrices.translate(-0.0125F, 1.5F, -0.012F);
                matrices.multiply(new Quaternionf().rotationXYZ(
                        (float) Math.toRadians(180.0F),
                        (float) Math.toRadians(180.0F),
                        (float) Math.toRadians(0.0F)
                ));

                renderer.renderRecursively(
                        matrices, entity, armBone,
                        renderLayer, buffers, buffer,
                        true, partialTick, light, overlay, 0xFFFFFFFF);

                matrices.pop();
            } finally {
                restoreSnapshot(bakedModel.topLevelBones());
            }
            return true;
        } catch (Throwable t) {
            System.err.println("[ArmorAutoHandler] Error rendering external Geo armor on arm " + arm + ": " + t.getMessage());
            return false;
        }
    }

    private GeoBone getBoneFromBakedModel(BakedGeoModel bakedModel, String name) {
        if (name.equals("right_arm")) {
            return getBoneFromModel(bakedModel, "armorRightArm")
                    .orElse(getBoneFromModel(bakedModel, "right_arm").orElse(null));
        } else if (name.equals("left_arm")) {
            return getBoneFromModel(bakedModel, "armorLeftArm")
                    .orElse(getBoneFromModel(bakedModel, "left_arm").orElse(null));
        }
        return getBoneFromModel(bakedModel, name).orElse(null);
    }

    private Optional<GeoBone> getBoneFromModel(BakedGeoModel model, String name) {
        return model.getBone(name).filter(b -> b instanceof GeoBone).map(b -> (GeoBone) b);
    }

    private static final java.util.Set<String> ALL_SLOT_BONE_NAMES = java.util.Set.of(
        "armorHead", "head",
        "armorBody", "body",
        "armorRightArm", "right_arm",
        "armorLeftArm", "left_arm",
        "armorRightLeg", "right_leg",
        "armorLeftLeg", "left_leg",
        "armorRightBoot", "right_boot",
        "armorLeftBoot", "left_boot"
    );

    private void applyBoneVisibilityForSlot(BakedGeoModel bakedModel, EquipmentSlot slot) {
        hideAllBones(bakedModel.topLevelBones());
        switch (slot) {
            case HEAD -> {
                java.util.Set<String> active = java.util.Set.of("armorHead", "head");
                showBoneAndChildren(bakedModel, "armorHead", active);
                showBoneAndChildren(bakedModel, "head", active);
            }
            case CHEST -> {
                java.util.Set<String> active = java.util.Set.of("armorBody", "body",
                        "armorRightArm", "right_arm", "armorLeftArm", "left_arm");
                showBoneAndChildren(bakedModel, "armorBody", active);
                showBoneAndChildren(bakedModel, "body", active);
                showBoneAndChildren(bakedModel, "armorRightArm", active);
                showBoneAndChildren(bakedModel, "right_arm", active);
                showBoneAndChildren(bakedModel, "armorLeftArm", active);
                showBoneAndChildren(bakedModel, "left_arm", active);
            }
            case LEGS -> {
                java.util.Set<String> active = java.util.Set.of("armorRightLeg", "right_leg",
                        "armorLeftLeg", "left_leg");
                showBoneAndChildren(bakedModel, "armorRightLeg", active);
                showBoneAndChildren(bakedModel, "right_leg", active);
                showBoneAndChildren(bakedModel, "armorLeftLeg", active);
                showBoneAndChildren(bakedModel, "left_leg", active);
            }
            case FEET -> {
                java.util.Set<String> active = java.util.Set.of("armorRightBoot", "right_boot",
                        "armorLeftBoot", "left_boot");
                showBoneAndChildren(bakedModel, "armorRightBoot", active);
                showBoneAndChildren(bakedModel, "right_boot", active);
                showBoneAndChildren(bakedModel, "armorLeftBoot", active);
                showBoneAndChildren(bakedModel, "left_boot", active);
            }
        }
    }

    private static void hideAllBones(List<? extends GeoBone> bones) {
        for (GeoBone bone : bones) {
            bone.setHidden(true);
            hideAllBones(bone.getChildBones());
        }
    }

    private void showBoneAndChildren(BakedGeoModel model, String name, java.util.Set<String> activeSlotBones) {
        getBoneFromModel(model, name).ifPresent(bone -> {
            bone.setHidden(false);
            GeoBone parent = bone.getParent();
            while (parent != null) {
                parent.setHidden(false);
                parent = parent.getParent();
            }
            showDescendantsSlotAware(bone, activeSlotBones);
        });
    }

    private void showDescendantsSlotAware(GeoBone bone, java.util.Set<String> activeSlotBones) {
        for (GeoBone child : bone.getChildBones()) {
            String childName = child.getName();

            if (ALL_SLOT_BONE_NAMES.contains(childName) && !activeSlotBones.contains(childName)) {
                continue;
            }
            child.setHidden(false);
            showDescendantsSlotAware(child, activeSlotBones);
        }
    }

    private void saveAndReset(List<? extends GeoBone> bones) {
        for (GeoBone bone : bones) {
            BoneState.save(bone);
            var init = bone.getInitialSnapshot();
            bone.setRotX(init.getRotX()); bone.setRotY(init.getRotY()); bone.setRotZ(init.getRotZ());
            bone.markRotationAsChanged();
            bone.setPosX(init.getOffsetX()); bone.setPosY(init.getOffsetY()); bone.setPosZ(init.getOffsetZ());
            bone.markPositionAsChanged();
            bone.setScaleX(init.getScaleX()); bone.setScaleY(init.getScaleY()); bone.setScaleZ(init.getScaleZ());
            bone.markScaleAsChanged();

            bone.setHidden(false);
            saveAndReset(bone.getChildBones());
        }
    }

    private void restoreSnapshot(List<? extends GeoBone> bones) {
        for (GeoBone bone : bones) {
            BoneState.restore(bone);
            bone.setHidden(false);
            restoreSnapshot(bone.getChildBones());
        }
    }

    private void copyRootAnimations(BakedGeoModel src, List<? extends GeoBone> dstBones) {
        for (GeoBone dst : dstBones) {
            src.getBone(dst.getName()).ifPresent(srcBone -> {
                dst.setPosX(srcBone.getPosX());
                dst.setPosY(srcBone.getPosY());
                dst.setPosZ(srcBone.getPosZ());
                dst.setRotX(srcBone.getRotX());
                dst.setRotY(srcBone.getRotY());
                dst.setRotZ(srcBone.getRotZ());
                dst.setScaleX(srcBone.getScaleX());
                dst.setScaleY(srcBone.getScaleY());
                dst.setScaleZ(srcBone.getScaleZ());
                dst.setPivotX(srcBone.getPivotX());
                dst.setPivotY(srcBone.getPivotY());
                dst.setPivotZ(srcBone.getPivotZ());
                dst.markRotationAsChanged();
                dst.markPositionAsChanged();
                dst.markScaleAsChanged();
            });
            copyRootAnimations(src, dst.getChildBones());
        }
    }

    private void resetBoneRecursively(GeoBone bone) {
        bone.setRotX(0.0F); bone.setRotY(0.0F); bone.setRotZ(0.0F);
        bone.setPosX(0.0F); bone.setPosY(0.0F); bone.setPosZ(0.0F);
        bone.setScaleX(1.0F); bone.setScaleY(1.0F); bone.setScaleZ(1.0F);
        for (GeoBone child : bone.getChildBones()) resetBoneRecursively(child);
    }

    private static BipedEntityModel<PlayerEntity> getDummyBaseModel() {
        try {
            ModelPart p = MinecraftClient.getInstance()
                    .getEntityModelLoader()
                    .getModelPart(EntityModelLayers.PLAYER_INNER_ARMOR);
            return new BipedEntityModel<>(p);
        } catch (Throwable t) {
            return null;
        }
    }

    private static final class BoneState {
        private static final java.util.IdentityHashMap<GeoBone, float[]> MAP = new java.util.IdentityHashMap<>();

        private static final int HIDDEN_IDX = 12;

        static void save(GeoBone bone) {
            MAP.put(bone, new float[]{
                    bone.getRotX(), bone.getRotY(), bone.getRotZ(),
                    bone.getPosX(), bone.getPosY(), bone.getPosZ(),
                    bone.getScaleX(), bone.getScaleY(), bone.getScaleZ(),
                    bone.getPivotX(), bone.getPivotY(), bone.getPivotZ(),
                    bone.isHidden() ? 1.0f : 0.0f
            });
        }

        static void restore(GeoBone bone) {
            float[] state = MAP.remove(bone);
            if (state == null) return;
            bone.setRotX(state[0]);
            bone.setRotY(state[1]);
            bone.setRotZ(state[2]);
            bone.markRotationAsChanged();
            bone.setPosX(state[3]);
            bone.setPosY(state[4]);
            bone.setPosZ(state[5]);
            bone.markPositionAsChanged();
            bone.setScaleX(state[6]);
            bone.setScaleY(state[7]);
            bone.setScaleZ(state[8]);
            bone.markScaleAsChanged();
            bone.setPivotX(state[9]);
            bone.setPivotY(state[10]);
            bone.setPivotZ(state[11]);
            bone.setHidden(state[HIDDEN_IDX] != 0.0f);
        }
    }
}