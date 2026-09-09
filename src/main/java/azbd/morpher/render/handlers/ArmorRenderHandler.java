package azbd.morpher.render.handlers;

import azbd.morpher.api.ArmorModelRegistry;
import azbd.morpher.model.ArmorModel;
import azbd.morpher.morph.MorphData;
import azbd.morpher.morph.MorphEntity;
import azbd.morpher.render.MorphRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.util.math.MathHelper;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.List;
import java.util.Optional;

public class ArmorRenderHandler {

    private final MorphRenderer renderer;
    private final ArmorModel headArmor = new ArmorModel(EquipmentSlot.HEAD);
    private final ArmorModel chestArmor = new ArmorModel(EquipmentSlot.CHEST);
    private final ArmorModel legsArmor = new ArmorModel(EquipmentSlot.LEGS);
    private final ArmorModel feetArmor = new ArmorModel(EquipmentSlot.FEET);
    private final ArmorModel elytraModel = ArmorModel.createElytra();
    private final ArmorModel layerModel = ArmorModel.createLayer();

    private final azbd.morpher.model.OverlayModel headOverlay = new azbd.morpher.model.OverlayModel();
    private final azbd.morpher.model.OverlayModel chestOverlay = new azbd.morpher.model.OverlayModel();
    private final azbd.morpher.model.OverlayModel legsOverlay = new azbd.morpher.model.OverlayModel();
    private final azbd.morpher.model.OverlayModel feetOverlay = new azbd.morpher.model.OverlayModel();

    private final ArmorAutoHandler autoHandler;
    private boolean isRendering = false;

    public ArmorRenderHandler(MorphRenderer renderer) {
        this.renderer = renderer;
        this.autoHandler = new ArmorAutoHandler(renderer);
    }

    public void setLayerName(String layerName) {
        this.layerModel.setLayerName(layerName);
    }

    public void renderLayer(MatrixStack matrices, MorphEntity entity,
                            VertexConsumerProvider buffers, float partialTick,
                            int light, int overlay) {
        if (isRendering) return;

        try {
            Identifier modelId = this.layerModel.getModelResource(entity);
            if (!exists(modelId)) return;

            BakedGeoModel layerGeoModel = this.layerModel.getBakedModel(modelId);
            Identifier textureId = this.layerModel.getTextureResource(entity);
            RenderLayer renderLayer = RenderLayer.getEntityCutoutNoCull(textureId);
            VertexConsumer vertexBuffer = buffers.getBuffer(renderLayer);

            BakedGeoModel srcModel = renderer.getPlayerModel()
                    .getBakedModel(renderer.getPlayerModel().getModelResource(entity));

            saveAndReset(layerGeoModel.topLevelBones());
            copyRootAnimations(srcModel, layerGeoModel.topLevelBones());

            isRendering = true;
            try {
                renderer.renderDirectly(matrices, entity, layerGeoModel, renderLayer, buffers,
                        vertexBuffer, false, partialTick, light, overlay, 0xFFFFFFFF);
            } finally {
                isRendering = false;
            }

            restoreSnapshot(layerGeoModel.topLevelBones());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void renderAllArmor(MatrixStack matrices, MorphEntity entity,
                               VertexConsumerProvider buffers, float partialTick,
                               int light, int overlay) {
        if (isRendering) return;

        PlayerEntity player = renderer.getCurrentEntity();
        if (player == null) return;

        MorphData.PlayerMorphState data = MorphData.get(player);
        headOverlay.setOverlayName(data.equipHead);
        chestOverlay.setOverlayName(data.equipChest);
        legsOverlay.setOverlayName(data.equipLegs);
        feetOverlay.setOverlayName(data.equipFeet);

        ItemStack headStack = player.getEquippedStack(EquipmentSlot.HEAD);
        ItemStack bodyStack = player.getEquippedStack(EquipmentSlot.CHEST);
        ItemStack legsStack = player.getEquippedStack(EquipmentSlot.LEGS);
        ItemStack feetStack = player.getEquippedStack(EquipmentSlot.FEET);

        try {
            isRendering = true;

            if (!headStack.isEmpty() && headStack.getItem() instanceof ArmorItem) {
                Optional<ArmorModelRegistry.Entry> customArmor = ArmorModelRegistry.get(headStack);
                if (customArmor.isPresent() && renderCustomArmorPiece(matrices, entity, buffers, partialTick, light, overlay, customArmor.get(), headStack)) {
                } else if (headOverlay.hasOverlay() && headOverlay.modelExists(entity)) {
                    renderOverlayPiece(matrices, entity, buffers, partialTick, light, overlay, headOverlay, headArmor, headStack);
                } else if (!renderExternalArmorForSlot(matrices, renderer.getCurrentEntity(), entity, buffers, partialTick, light, overlay, headStack, EquipmentSlot.HEAD)) {
                    renderArmor(matrices, entity, buffers, partialTick, light, overlay, headStack, headArmor);
                }
            }

            if (!bodyStack.isEmpty() && bodyStack.getItem() instanceof ArmorItem) {
                Optional<ArmorModelRegistry.Entry> customArmor = ArmorModelRegistry.get(bodyStack);
                if (customArmor.isPresent() && renderCustomArmorPiece(matrices, entity, buffers, partialTick, light, overlay, customArmor.get(), bodyStack)) {
                } else if (chestOverlay.hasOverlay() && chestOverlay.modelExists(entity)) {
                    renderOverlayPiece(matrices, entity, buffers, partialTick, light, overlay, chestOverlay, chestArmor, bodyStack);
                } else if (!renderExternalArmorForSlot(matrices, renderer.getCurrentEntity(), entity, buffers, partialTick, light, overlay, bodyStack, EquipmentSlot.CHEST)) {
                    renderArmor(matrices, entity, buffers, partialTick, light, overlay, bodyStack, chestArmor);
                }
            }

            if (!legsStack.isEmpty() && legsStack.getItem() instanceof ArmorItem) {
                Optional<ArmorModelRegistry.Entry> customArmor = ArmorModelRegistry.get(legsStack);
                if (customArmor.isPresent() && renderCustomArmorPiece(matrices, entity, buffers, partialTick, light, overlay, customArmor.get(), legsStack)) {
                } else if (legsOverlay.hasOverlay() && legsOverlay.modelExists(entity)) {
                    renderOverlayPiece(matrices, entity, buffers, partialTick, light, overlay, legsOverlay, legsArmor, legsStack);
                } else if (!renderExternalArmorForSlot(matrices, renderer.getCurrentEntity(), entity, buffers, partialTick, light, overlay, legsStack, EquipmentSlot.LEGS)) {
                    renderArmor(matrices, entity, buffers, partialTick, light, overlay, legsStack, legsArmor);
                }
            }

            if (!feetStack.isEmpty() && feetStack.getItem() instanceof ArmorItem) {
                Optional<ArmorModelRegistry.Entry> customArmor = ArmorModelRegistry.get(feetStack);
                if (customArmor.isPresent() && renderCustomArmorPiece(matrices, entity, buffers, partialTick, light, overlay, customArmor.get(), feetStack)) {
                } else if (feetOverlay.hasOverlay() && feetOverlay.modelExists(entity)) {
                    renderOverlayPiece(matrices, entity, buffers, partialTick, light, overlay, feetOverlay, feetArmor, feetStack);
                } else if (!renderExternalArmorForSlot(matrices, renderer.getCurrentEntity(), entity, buffers, partialTick, light, overlay, feetStack, EquipmentSlot.FEET)) {
                    renderArmor(matrices, entity, buffers, partialTick, light, overlay, feetStack, feetArmor);
                }
            }

            if (!bodyStack.isEmpty() && bodyStack.getItem() instanceof ElytraItem) {
                renderElytra(matrices, entity, buffers, partialTick, light, overlay, elytraModel);
            }
        } finally {
            isRendering = false;
        }
    }

    public void renderExtraLayers(MatrixStack matrices, MorphEntity entity,
                                  VertexConsumerProvider buffers, float partialTick,
                                  int light, int overlay) {
        PlayerEntity player = renderer.getCurrentEntity();
        if (player == null) return;
        MorphData.PlayerMorphState state = MorphData.get(player);
        renderExtraLayersInternal(matrices, entity, buffers, partialTick, light, overlay,
            layerModel, state.layerName, isRendering, state);
    }

    public boolean renderExternalArmorForSlot(MatrixStack matrices, PlayerEntity player, MorphEntity entity,
                                              VertexConsumerProvider buffers, float partialTick,
                                              int light, int overlay, ItemStack stack, EquipmentSlot slot) {
        return this.autoHandler.renderForSlot(matrices, player, entity, buffers, partialTick,
                light, overlay, stack, slot);
    }

    public boolean renderExternalArmorOnArm(MatrixStack matrices, PlayerEntity player, MorphEntity entity,
                                            VertexConsumerProvider buffers, float partialTick,
                                            int light, int overlay, ItemStack stack, Arm arm) {
        return this.autoHandler.renderOnArm(matrices, player, entity, buffers, partialTick,
                light, overlay, stack, arm);
    }

    private boolean renderCustomArmorPiece(MatrixStack matrices, MorphEntity entity,
                                           VertexConsumerProvider buffers, float partialTick,
                                           int light, int overlay, ArmorModelRegistry.Entry entry,
                                           ItemStack armorStack) {
        try {
            azbd.morpher.model.ArmorModelCreator model = new azbd.morpher.model.ArmorModelCreator(entry);
            Identifier modelId = model.getModelResource(entity);
            Identifier textureId = model.getTextureResource(entity);
            if (!exists(modelId) || textureId == null || !exists(textureId)) {
                return false;
            }

            BakedGeoModel bakedModel = model.getBakedModel(modelId);
            BakedGeoModel srcModel = renderer.getPlayerModel()
                    .getBakedModel(renderer.getPlayerModel().getModelResource(entity));

            saveAndReset(bakedModel.topLevelBones());
            copyRootAnimations(srcModel, bakedModel.topLevelBones());

            RenderLayer renderLayer = RenderLayer.getArmorCutoutNoCull(textureId);
            VertexConsumer buffer = ItemRenderer.getArmorGlintConsumer(buffers, renderLayer, armorStack.hasGlint());

            renderer.renderDirectly(matrices, entity, bakedModel, renderLayer, buffers,
                    buffer, false, partialTick, light, overlay, 0xFFFFFFFF);

            restoreSnapshot(bakedModel.topLevelBones());
            return true;
        } catch (Exception exception) {
            System.err.println("[ArmorRenderHandler] Error rendering custom armor item.");
            return false;
        }
    }

    private void renderOverlayPiece(MatrixStack matrices, MorphEntity entity,
                                   VertexConsumerProvider buffers, float partialTick,
                                   int light, int overlay, azbd.morpher.model.OverlayModel overlayModel,
                                   ArmorModel vanillaFallback, ItemStack armorStack) {
        try {
            Identifier modelId = overlayModel.getModelResource(entity);
            BakedGeoModel bakedModel = overlayModel.getBakedModel(modelId);

            Identifier textureId = overlayModel.getTextureResource(entity);
            if (textureId == null || !exists(textureId)) {
                textureId = vanillaFallback.getTextureResource(entity);
            }
            if (textureId == null) return;

            BakedGeoModel srcModel = renderer.getPlayerModel()
                    .getBakedModel(renderer.getPlayerModel().getModelResource(entity));

            saveAndReset(bakedModel.topLevelBones());
            copyRootAnimations(srcModel, bakedModel.topLevelBones());

            boolean hasFoil = armorStack.hasGlint();
            RenderLayer renderLayer = RenderLayer.getArmorCutoutNoCull(textureId);
            VertexConsumer buffer = ItemRenderer.getArmorGlintConsumer(buffers, renderLayer, hasFoil);

            float r = 1.0F, g = 1.0F, b = 1.0F;
            int leatherColor = getLeatherColor(armorStack);
            if (leatherColor != -1) {
                r = (leatherColor >> 16 & 255) / 255.0F;
                g = (leatherColor >> 8 & 255) / 255.0F;
                b = (leatherColor & 255) / 255.0F;
            }

            int packedColor = (255 << 24)
                    | ((int) (r * 255) << 16)
                    | ((int) (g * 255) << 8)
                    | (int) (b * 255);

            renderer.renderDirectly(matrices, entity, bakedModel, renderLayer, buffers,
                    buffer, false, partialTick, light, overlay, packedColor);
            renderArmorTrim(matrices, entity, buffers, partialTick, light, overlay, armorStack, vanillaFallback, bakedModel);

            restoreSnapshot(bakedModel.topLevelBones());

        } catch (Exception e) {
            System.err.println("[ArmorRenderHandler] Error rendering overlay piece: " + overlayModel.getOverlayName());
        }
    }

    private void renderArmor(MatrixStack matrices, MorphEntity entity,
                            VertexConsumerProvider buffers, float partialTick,
                            int light, int overlay, ItemStack armorStack, ArmorModel model) {
        try {
            Identifier modelId = model.getModelResource(entity);
            BakedGeoModel bakedModel = model.getBakedModel(modelId);
            Identifier textureId = model.getTextureResource(entity);
            if (textureId == null) return;

            BakedGeoModel srcModel = renderer.getPlayerModel()
                    .getBakedModel(renderer.getPlayerModel().getModelResource(entity));

            saveAndReset(bakedModel.topLevelBones());
            copyRootAnimations(srcModel, bakedModel.topLevelBones());

            boolean hasFoil = armorStack.hasGlint();
            RenderLayer renderLayer = RenderLayer.getArmorCutoutNoCull(textureId);
            VertexConsumer vc = ItemRenderer.getArmorGlintConsumer(buffers, renderLayer, hasFoil);

            float r = 1.0F, g = 1.0F, b = 1.0F;
            int leatherColor = getLeatherColor(armorStack);
            if (leatherColor != -1) {
                r = (leatherColor >> 16 & 255) / 255.0F;
                g = (leatherColor >> 8 & 255) / 255.0F;
                b = (leatherColor & 255) / 255.0F;
            }

            int packedColor = (255 << 24)
                    | ((int) (r * 255) << 16)
                    | ((int) (g * 255) << 8)
                    | (int) (b * 255);

            renderer.renderDirectly(matrices, entity, bakedModel, renderLayer, buffers,
                    vc, false, partialTick, light, overlay, packedColor);
            renderArmorTrim(matrices, entity, buffers, partialTick, light, overlay, armorStack, model, bakedModel);

            restoreSnapshot(bakedModel.topLevelBones());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void renderExtraLayersInternal(MatrixStack matrices, MorphEntity entity,
                                  VertexConsumerProvider buffers, float partialTick,
                                  int light, int overlay, ArmorModel layerModel, String currentLayerName,
                                  boolean isRendering, MorphData.PlayerMorphState state) {
        if (state.extraLayers == null || state.extraLayers.isEmpty()) return;

        for (int i = 0; i < state.extraLayers.size(); i++) {
            MorphData.ExtraLayer extraLayer = state.extraLayers.get(i);
            if (extraLayer.name.isEmpty() || extraLayer.name.equals("empty")) continue;

            try {
                layerModel.setLayerName(extraLayer.name);
                Identifier modelId = layerModel.getModelResource(entity);
                if (!exists(modelId)) continue;

                BakedGeoModel layerGeoModel = layerModel.getBakedModel(modelId);

                String texName = extraLayer.texture.isEmpty() ? extraLayer.name : extraLayer.texture;
                Identifier textureId = Identifier.of("morpher", "textures/" + texName + ".png");
                RenderLayer renderLayer = RenderLayer.getEntityCutoutNoCull(textureId);
                VertexConsumer vertexBuffer = buffers.getBuffer(renderLayer);

                BakedGeoModel srcModel = renderer.getPlayerModel().getBakedModel(renderer.getPlayerModel().getModelResource(entity));

                saveAndReset(layerGeoModel.topLevelBones());
                copyRootAnimations(srcModel, layerGeoModel.topLevelBones());

                renderer.renderDirectly(matrices, entity, layerGeoModel, renderLayer, buffers,
                        vertexBuffer, false, partialTick, light, overlay, 0xFFFFFFFF);

                restoreSnapshot(layerGeoModel.topLevelBones());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        layerModel.setLayerName(currentLayerName);
    }

    private void renderElytra(MatrixStack matrices, MorphEntity entity,
                             VertexConsumerProvider buffers, float partialTick,
                             int light, int overlay, ArmorModel elytraModel) {
        try {
            Identifier modelId = elytraModel.getModelResource(entity);
            BakedGeoModel elytraGeoModel = elytraModel.getBakedModel(modelId);
            Identifier textureId = elytraModel.getTextureResource(entity);
            RenderLayer renderLayer = elytraModel.getRenderType(entity, textureId);
            VertexConsumer buffer = buffers.getBuffer(renderLayer);

            BakedGeoModel srcModel = renderer.getPlayerModel().getBakedModel(renderer.getPlayerModel().getModelResource(entity));

            saveAndReset(elytraGeoModel.topLevelBones());
            copyRootAnimations(srcModel, elytraGeoModel.topLevelBones());

            PlayerEntity player = renderer.getCurrentEntity();
            Optional<GeoBone> rightWing = getBoneFromModel(elytraGeoModel, "right_wing");
            Optional<GeoBone> leftWing = getBoneFromModel(elytraGeoModel, "left_wing");
            if (player != null && rightWing.isPresent() && leftWing.isPresent()) {
                animateWings(rightWing.get(), leftWing.get(), player, partialTick);
            }

            renderer.renderDirectly(matrices, entity, elytraGeoModel, renderLayer, buffers,
                    buffer, false, partialTick, light, overlay, 0xFFFFFFFF);

            restoreSnapshot(elytraGeoModel.topLevelBones());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void renderArmorTrim(MatrixStack matrices, MorphEntity entity,
                                 VertexConsumerProvider buffers, float partialTick,
                                 int light, int overlay, ItemStack armorStack,
                                 ArmorModel model, BakedGeoModel bakedModel) {
        try {
            if (!(armorStack.getItem() instanceof ArmorItem armorItem)) return;

            ArmorTrim trim = armorStack.get(DataComponentTypes.TRIM);
            if (trim == null) return;

            boolean leggings = model.getSlot() == EquipmentSlot.LEGS;
            Identifier textureId = leggings
                    ? trim.getLeggingsModelId(armorItem.getMaterial())
                    : trim.getGenericModelId(armorItem.getMaterial());
            boolean decal = trim.getPattern().value().decal();

            SpriteIdentifier sprite = new SpriteIdentifier(TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE, textureId);
            RenderLayer renderLayer = TexturedRenderLayers.getArmorTrims(decal);
            VertexConsumer buffer = sprite.getVertexConsumer(buffers, ignored -> renderLayer, false);

            matrices.push();
            renderer.renderDirectly(matrices, entity, bakedModel, renderLayer, buffers,
                    buffer, false, partialTick, light, overlay, 0xFFFFFFFF);
            matrices.pop();
        } catch (Exception ignored) {
        }
    }

    private void animateWings(GeoBone rightWing, GeoBone leftWing, PlayerEntity player, float partialTick) {
        boolean flying = player.isFallFlying();
        if (flying) {
            float pitch = player.getPitch();
            float factor = MathHelper.clamp((pitch + 60.0F) / 120.0F, 0.0F, 1.0F);
            leftWing.setRotZ(MathHelper.lerp(factor, (float) Math.toRadians(-85.0F), (float) Math.toRadians(-165.0F)));
            leftWing.setRotX(MathHelper.lerp(factor, (float) Math.toRadians(160.0F), (float) Math.toRadians(180.0F)));
            rightWing.setRotZ(MathHelper.lerp(factor, (float) Math.toRadians(85.0F), (float) Math.toRadians(165.0F)));
            rightWing.setRotX(MathHelper.lerp(factor, (float) Math.toRadians(160.0F), (float) Math.toRadians(180.0F)));
        } else {
            leftWing.setRotZ((float) Math.toRadians(-165.0F));
            leftWing.setRotX((float) Math.toRadians(180.0F));
            rightWing.setRotZ((float) Math.toRadians(165.0F));
            rightWing.setRotX((float) Math.toRadians(180.0F));
        }
    }

    private Optional<GeoBone> getBoneFromModel(BakedGeoModel model, String name) {
        return model.getBone(name).filter(b -> b instanceof GeoBone).map(b -> (GeoBone) b);
    }

    private Identifier getVanillaArmorTexture(ItemStack armorStack, EquipmentSlot slot) {
        Item item = armorStack.getItem();
        if (item == Items.TURTLE_HELMET) {
            return Identifier.of("minecraft", "textures/models/armor/turtle_layer_1.png");
        }
        if (!(item instanceof ArmorItem)) return null;
        ArmorItem armorItem = (ArmorItem) item;

        String layer = (slot == EquipmentSlot.LEGS) ? "layer_2" : "layer_1";
        String materialName = getVanillaMaterialName(armorItem);
        if (materialName == null) return null;
        return Identifier.of("minecraft", "textures/models/armor/" + materialName + "_" + layer + ".png");
    }

    private String getVanillaMaterialName(ArmorItem armorItem) {
        String key = armorItem.getMaterial().value().equipSound().value().getId().getPath();
        if (key.contains("leather")) return "leather";
        if (key.contains("chainmail")) return "chainmail";
        if (key.contains("iron")) return "iron";
        if (key.contains("gold")) return "gold";
        if (key.contains("diamond")) return "diamond";
        if (key.contains("netherite")) return "netherite";
        return null;
    }

    private int getLeatherColor(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armorItem) {
            String materialName = getVanillaMaterialName(armorItem);
            if ("leather".equals(materialName)) {
                DyedColorComponent dyed = stack.get(DataComponentTypes.DYED_COLOR);
                return dyed != null ? dyed.rgb() : 10511680;
            }
        }
        return -1;
    }

    private boolean exists(Identifier id) {
        try {
            return MinecraftClient.getInstance().getResourceManager().getResource(id).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    private void saveAndReset(List<? extends GeoBone> bones) {
        for (GeoBone bone : bones) {
            BoneState.save(bone);
            var init = bone.getInitialSnapshot();
            bone.setRotX(init.getRotX());
            bone.setRotY(init.getRotY());
            bone.setRotZ(init.getRotZ());
            bone.markRotationAsChanged();
            bone.setPosX(init.getOffsetX());
            bone.setPosY(init.getOffsetY());
            bone.setPosZ(init.getOffsetZ());
            bone.markPositionAsChanged();
            bone.setScaleX(init.getScaleX());
            bone.setScaleY(init.getScaleY());
            bone.setScaleZ(init.getScaleZ());
            bone.markScaleAsChanged();
            saveAndReset(bone.getChildBones());
        }
    }

    private void restoreSnapshot(List<? extends GeoBone> bones) {
        for (GeoBone bone : bones) {
            BoneState.restore(bone);
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

    static final class BoneState {
        private static final java.util.IdentityHashMap<GeoBone, float[]> MAP = new java.util.IdentityHashMap<>();

        static void save(GeoBone bone) {
            MAP.put(bone, new float[]{
                    bone.getRotX(), bone.getRotY(), bone.getRotZ(),
                    bone.getPosX(), bone.getPosY(), bone.getPosZ(),
                    bone.getScaleX(), bone.getScaleY(), bone.getScaleZ(),
                    bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()
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
        }
    }
}
