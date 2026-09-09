package azbd.morpher.render.handlers;

import azbd.morpher.morph.MorphEntity;
import azbd.morpher.render.MorphRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class ItemRenderHandler {

    private final MorphRenderer morphRenderer;

    public ItemRenderHandler(MorphRenderer renderer) {
        this.morphRenderer = renderer;
    }

    public GeoRenderLayer<MorphEntity> createItemLayer() {
        return new BlockAndItemGeoLayer<MorphEntity>(morphRenderer) {

            @Override
            protected ItemStack getStackForBone(GeoBone bone, MorphEntity entity) {
                if (morphRenderer.isRenderingLayer()) return ItemStack.EMPTY;

                PlayerEntity p = morphRenderer.getCurrentEntity();
                if (p == null) return ItemStack.EMPTY;

                String name = bone.getName();
                if (name.contains("left_hand_item")) {
                    return p.getMainArm() == Arm.RIGHT ? p.getOffHandStack() : p.getMainHandStack();
                } else if (name.contains("right_hand_item")) {
                    return p.getMainArm() == Arm.RIGHT ? p.getMainHandStack() : p.getOffHandStack();
                } else if (name.equals("head")) {
                    return p.getEquippedStack(EquipmentSlot.HEAD);
                }
                return ItemStack.EMPTY;
            }

            @Override
            protected net.minecraft.client.render.model.json.ModelTransformationMode getTransformTypeForStack(
                    GeoBone bone, ItemStack stack, MorphEntity entity) {
                String name = bone.getName();

                if (name.contains("right_hand_item")) {
                    return net.minecraft.client.render.model.json.ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
                }
                if (name.contains("left_hand_item")) {
                    return net.minecraft.client.render.model.json.ModelTransformationMode.THIRD_PERSON_LEFT_HAND;
                }
                if (name.equals("head")) {
                    return net.minecraft.client.render.model.json.ModelTransformationMode.HEAD;
                }
                return net.minecraft.client.render.model.json.ModelTransformationMode.NONE;
            }

            @Override
            protected void renderStackForBone(MatrixStack matrices, GeoBone bone, ItemStack stack,
                                              MorphEntity entity, VertexConsumerProvider buffers,
                                              float partialTick, int light, int overlay) {
                PlayerEntity p = morphRenderer.getCurrentEntity();
                if (p == null) return;

                String name = bone.getName();

                if (name.contains("right_hand_item") || name.contains("left_hand_item")) {
                    renderHand(matrices, bone, stack, entity, p, buffers, partialTick, light, overlay);
                } else if (name.equals("head")) {
                    renderHead(matrices, bone, stack, entity, p, buffers, partialTick, light, overlay);
                }
            }

            private void renderHand(MatrixStack mat, GeoBone bone, ItemStack stack, MorphEntity entity,
                                    PlayerEntity p, VertexConsumerProvider buf, float tick, int light, int overlay) {
                boolean isRight = bone.getName().contains("right_hand_item");
                boolean isMain  = (isRight && p.getMainArm() == Arm.RIGHT) ||
                        (!isRight && p.getMainArm() == Arm.LEFT);

                ItemStack mainStack     = p.getMainHandStack();
                ItemStack offStack      = p.getOffHandStack();
                ItemStack expectedStack = isMain ? mainStack : offStack;

                if (stack != expectedStack) return;

                if (p.isUsingItem()) {
                    boolean usingMain       = p.getActiveHand() == net.minecraft.util.Hand.MAIN_HAND;
                    boolean itemIsInMainBone = isMain;
                    if (usingMain != itemIsInMainBone && stack == p.getActiveItem()) return;
                }

                Item item = stack.getItem();

                mat.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));

                if (item instanceof ShieldItem) {
                    if (p.getMainArm() == Arm.RIGHT) {
                        if (isMain) {
                            mat.translate(0.0, 0.031, -0.05);
                        } else {
                            mat.translate(0.0, 0.031, 1.45);
                            mat.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
                        }
                    } else {
                        if (!isMain) {
                            mat.translate(0.0, 0.031, -0.05);
                        } else {
                            mat.translate(0.0, 0.031, 1.45);
                            mat.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
                        }
                    }
                    super.renderStackForBone(mat, bone, stack, entity, buf, tick, light, overlay);
                    return;
                }

                if (item instanceof BowItem) {
                    mat.translate(0.0, 0.1, 0.0);
                    mat.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(isMain ? -5.0F : 5.0F));
                } else if (item instanceof CrossbowItem) {
                    mat.translate(0.0, 0.1, 0.0);
                    mat.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(isMain ? 10.0F : -10.0F));
                }

                MinecraftClient.getInstance().getItemRenderer().renderItem(
                        p, stack,
                        getTransformTypeForStack(bone, stack, entity),
                        !isRight, mat, buf, p.getWorld(), light, overlay, p.getId()
                );
            }

            private void renderHead(MatrixStack mat, GeoBone bone, ItemStack stack, MorphEntity entity,
                                    PlayerEntity p, VertexConsumerProvider buf, float tick, int light, int overlay) {
                if (stack.getItem() instanceof ArmorItem armor && armor.getSlotType() == EquipmentSlot.HEAD) {
                    return;
                }

                super.renderStackForBone(mat, bone, stack, entity, buf, tick, light, overlay);
            }
        };
    }
}