package azbd.morpher.mixin;

import azbd.morpher.api.ArmorModelRegistry;
import azbd.morpher.model.ArmorModel;
import azbd.morpher.model.ArmorModelCreator;
import azbd.morpher.morph.MorphData;
import azbd.morpher.morph.MorphEntity;
import azbd.morpher.render.MorphRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.world.LightType;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(HeldItemRenderer.class)
public abstract class ArmorRenderMixin {

    @Shadow @Final
    private MinecraftClient client;

    @Unique
    private final Map<UUID, MorphRenderer> armorRendererCache = new ConcurrentHashMap<>();

    @Inject(method = "renderArmHoldingItem", at = @At("TAIL"))
    private void onRenderArmArmor(MatrixStack matrices, VertexConsumerProvider buffers, int light,
                                  float equipProgress, float swingProgress, Arm arm, CallbackInfo ci) {

        AbstractClientPlayerEntity player = client.player;
        if (player == null) return;

        MorphData.PlayerMorphState state = MorphData.get(player);
        if (!state.renderCustomModel || state.isDead || player.isSpectator()) return;

        ItemStack chestStack = player.getEquippedStack(EquipmentSlot.CHEST);
        if (!(chestStack.getItem() instanceof ArmorItem)) return;
        ArmorItem armorItem = (ArmorItem) chestStack.getItem();

        String materialName = getMaterialName(armorItem);

        try {
            Optional<ArmorModelRegistry.Entry> customArmor = ArmorModelRegistry.get(chestStack);
            if (customArmor.isPresent() && customArmorResourcesExist(customArmor.get())) {
                renderCustomArmorSafely(matrices, buffers, light, arm, player, chestStack, customArmor.get());
            } else {
                UUID playerUuid = player.getUuid();
                MorphRenderer renderer = armorRendererCache.computeIfAbsent(playerUuid, uuid -> createRenderer());
                if (renderer == null) throw new Exception("Error al crear renderer");

                renderer.setCurrentEntity(player);
                MorphEntity animatable = (MorphEntity) renderer.getAnimatable();
                animatable.setRenderer(renderer, player);

                if (renderer.getArmorHandler().renderExternalArmorOnArm(
                        matrices, player, animatable, buffers, 1.0F, light,
                        getDamageOverlay(player), chestStack, arm)) {
                    return;
                }

                renderArmorSafely(matrices, buffers, light, arm, player, state, chestStack, materialName);
            }
        } catch (Exception e) {
            if (client.getDebugHud().shouldShowDebugHud()) {
                e.printStackTrace();
            }
        }
    }

    @Unique
    private void renderArmorSafely(MatrixStack matrices, VertexConsumerProvider buffers, int light,
                                   Arm arm, AbstractClientPlayerEntity player,
                                   MorphData.PlayerMorphState state, ItemStack chestStack,
                                   String materialName) throws Exception {

        UUID playerUuid = player.getUuid();

        MorphRenderer renderer = armorRendererCache.computeIfAbsent(playerUuid, uuid -> createRenderer());
        if (renderer == null) throw new Exception("Error al crear renderer");

        renderer.setCurrentEntity(player);
        MorphEntity animatable = (MorphEntity) renderer.getAnimatable();
        if (animatable == null) throw new Exception("Animatable es null");

        animatable.setRenderer(renderer, player);

        ArmorModel armorModel = new ArmorModel(EquipmentSlot.CHEST);
        armorModel.setCustomMaterial(materialName);

        try {
            armorModel.getBakedModel(armorModel.getModelResource(animatable));
        } catch (Exception e) {
            throw new Exception("Error al cargar modelo: " + materialName, e);
        }

        boolean isRightArm = arm == Arm.RIGHT;
        String  boneName   = isRightArm ? "right_arm" : "left_arm";

        Optional<GeoBone> armorBone = armorModel.getBone(boneName);
        if (!armorBone.isPresent()) throw new Exception("Hueso '" + boneName + "' no encontrado");

        RenderLayer armorTexture = RenderLayer.getArmorCutoutNoCull(
                armorModel.getTextureResource(animatable));

        int     packedLight   = calculateLight(player);
        int     damageOverlay = getDamageOverlay(player);
        float[] armorColor    = calculateArmorColor(chestStack);

        int packedColor = (255 << 24)
                | ((int)(armorColor[0] * 255) << 16)
                | ((int)(armorColor[1] * 255) << 8)
                |  (int)(armorColor[2] * 255);

        matrices.push();
        matrices.translate(-0.0125F, 1.5F, -0.012F);
        matrices.multiply(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(180.0F),
                (float) Math.toRadians(180.0F),
                (float) Math.toRadians(0.0F)
        ));

        GeoBone armorGeoBone = armorBone.get();
        resetBoneToInitialSnapshot(armorGeoBone);

        renderer.renderRecursively(
                matrices, animatable, armorGeoBone,
                armorTexture, buffers, ItemRenderer.getArmorGlintConsumer(
                        buffers, armorTexture, chestStack.hasGlint()),
                true, 1.0F, packedLight, damageOverlay, packedColor);
        if (chestStack.getItem() instanceof ArmorItem armorItem) {
            renderArmTrim(renderer, matrices, animatable, armorGeoBone, buffers,
                    chestStack, armorItem, packedLight, damageOverlay);
        }

        matrices.pop();
    }

    @Unique
    private void renderCustomArmorSafely(MatrixStack matrices, VertexConsumerProvider buffers, int light,
                                         Arm arm, AbstractClientPlayerEntity player,
                                         ItemStack chestStack, ArmorModelRegistry.Entry entry) throws Exception {
        UUID playerUuid = player.getUuid();

        MorphRenderer renderer = armorRendererCache.computeIfAbsent(playerUuid, uuid -> createRenderer());
        if (renderer == null) throw new Exception("Error al crear renderer");

        renderer.setCurrentEntity(player);
        MorphEntity animatable = (MorphEntity) renderer.getAnimatable();
        if (animatable == null) throw new Exception("Animatable es null");

        animatable.setRenderer(renderer, player);

        ArmorModelCreator armorModel = new ArmorModelCreator(entry);
        armorModel.getBakedModel(armorModel.getModelResource(animatable));

        boolean isRightArm = arm == Arm.RIGHT;
        String boneName = isRightArm ? "right_arm" : "left_arm";

        Optional<GeoBone> armorBone = armorModel.getBone(boneName);
        if (!armorBone.isPresent()) throw new Exception("Hueso '" + boneName + "' no encontrado");

        RenderLayer armorTexture = RenderLayer.getArmorCutoutNoCull(
                armorModel.getTextureResource(animatable));

        int packedLight = calculateLight(player);
        int damageOverlay = getDamageOverlay(player);

        matrices.push();
        matrices.translate(-0.0125F, 1.5F, -0.012F);
        matrices.multiply(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(180.0F),
                (float) Math.toRadians(180.0F),
                (float) Math.toRadians(0.0F)
        ));

        GeoBone armorGeoBone = armorBone.get();
        resetBoneToInitialSnapshot(armorGeoBone);

        renderer.renderRecursively(
                matrices, animatable, armorGeoBone,
                armorTexture, buffers, ItemRenderer.getArmorGlintConsumer(
                        buffers, armorTexture, chestStack.hasGlint()),
                true, 1.0F, packedLight, damageOverlay, 0xFFFFFFFF);

        matrices.pop();
    }

    @Unique
    private boolean customArmorResourcesExist(ArmorModelRegistry.Entry entry) {
        try {
            return client.getResourceManager().getResource(entry.model()).isPresent()
                    && client.getResourceManager().getResource(entry.texture()).isPresent();
        } catch (Exception exception) {
            return false;
        }
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
    private int calculateLight(AbstractClientPlayerEntity player) {
        net.minecraft.util.math.BlockPos pos = player.getBlockPos();

        int blockLight = client.world.getLightLevel(LightType.BLOCK, pos);
        int skyLight   = client.world.getLightLevel(LightType.SKY,   pos);

        if (blockLight == 0 && skyLight == 0) {
            int maxBlock = 0, maxSky = 0;
            for (net.minecraft.util.math.BlockPos n : new net.minecraft.util.math.BlockPos[]{
                    pos.up(), pos.north(), pos.south(), pos.east(), pos.west()
            }) {
                int bl = client.world.getLightLevel(LightType.BLOCK, n);
                int sl = client.world.getLightLevel(LightType.SKY,   n);
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
    private int getDamageOverlay(AbstractClientPlayerEntity player) {
        int hurtTime = player.hurtTime;
        if (hurtTime <= 0) return OverlayTexture.DEFAULT_UV;
        float intensity = (float) hurtTime / 10.0F;
        return OverlayTexture.packUv((int)(intensity * 3.0F), 3);
    }

    @Unique
    private float[] calculateArmorColor(ItemStack armorStack) {
        float[] color = new float[]{1.0F, 1.0F, 1.0F};
        if (armorStack.contains(DataComponentTypes.DYED_COLOR)) {
            int colorInt = getLeatherColor(armorStack);
            color[0] = (float)(colorInt >> 16 & 0xFF) / 255.0F;
            color[1] = (float)(colorInt >>  8 & 0xFF) / 255.0F;
            color[2] = (float)(colorInt        & 0xFF) / 255.0F;
        }
        return color;
    }

    @Unique
    private int getLeatherColor(ItemStack stack) {
        DyedColorComponent dyed = stack.get(DataComponentTypes.DYED_COLOR);
        return dyed != null ? dyed.rgb() : 10511680;
    }

    @Unique
    private void renderArmTrim(MorphRenderer renderer, MatrixStack matrices,
                               MorphEntity animatable, GeoBone armorGeoBone,
                               VertexConsumerProvider buffers, ItemStack chestStack,
                               ArmorItem armorItem, int packedLight, int damageOverlay) {
        try {
            ArmorTrim trim = chestStack.get(DataComponentTypes.TRIM);
            if (trim == null) return;

            Identifier textureId = trim.getGenericModelId(armorItem.getMaterial());
            boolean decal = trim.getPattern().value().decal();
            SpriteIdentifier sprite = new SpriteIdentifier(TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE, textureId);
            RenderLayer renderLayer = TexturedRenderLayers.getArmorTrims(decal);
            VertexConsumer buffer = sprite.getVertexConsumer(buffers, ignored -> renderLayer, false);

            matrices.push();
            renderer.renderRecursively(
                    matrices, animatable, armorGeoBone,
                    renderLayer, buffers, buffer,
                    true, 1.0F, packedLight, damageOverlay, 0xFFFFFFFF);
            matrices.pop();
        } catch (Exception ignored) {
        }
    }

    @Unique
    private MorphRenderer createRenderer() {
        try {
            EntityRendererFactory.Context context = new EntityRendererFactory.Context(
                    client.getEntityRenderDispatcher(),
                    client.getItemRenderer(),
                    client.getBlockRenderManager(),
                    client.getEntityRenderDispatcher().getHeldItemRenderer(),
                    client.getResourceManager(),
                    client.getEntityModelLoader(),
                    client.textRenderer
            );
            return new MorphRenderer(context);
        } catch (Exception e) {
            return null;
        }
    }

    @Unique
    private String getMaterialName(ArmorItem armorItem) {
        RegistryEntry<ArmorMaterial> material = armorItem.getMaterial();

        String id = material.getKey()
                .map(k -> k.getValue().getPath())
                .orElse("unknown");

        return switch (id) {
            case "leather"   -> "leather";
            case "chainmail" -> "chainmail";
            case "iron"      -> "iron";
            case "gold"      -> "gold";
            case "diamond"   -> "diamond";
            case "netherite" -> "netherite";
            default          -> "unknown";
        };
    }
}
