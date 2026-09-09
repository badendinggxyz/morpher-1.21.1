package azbd.morpher.render;

import azbd.morpher.morph.MorphData;
import azbd.morpher.morph.MorphEntity;
import azbd.morpher.render.handlers.ArmorRenderHandler;
import azbd.morpher.render.handlers.ItemRenderHandler;
import azbd.morpher.render.handlers.VisibilityHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

public class MorphRenderer extends GeoReplacedEntityRenderer<PlayerEntity, MorphEntity> {

    private final GeoModel<MorphEntity> model;
    private final ArmorRenderHandler armor;
    private final ItemRenderHandler items;
    private final VisibilityHandler visibility;

    private static boolean bonesReady = false;
    private boolean isGuiRender = false;
    private boolean isRenderingLayer = false;
    private VertexConsumerProvider currentBuffer;
    private long lastInstanceId = -1;

    public boolean isRenderingLayer() { return this.isRenderingLayer; }

    public void setGuiRender(boolean gui) {
        this.isGuiRender = gui;
    }

    public boolean isGuiRender() {
        return this.isGuiRender;
    }

    public MorphRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new azbd.morpher.model.PlayerModel(), new MorphEntity());
        this.model = this.getGeoModel();
        this.armor = new ArmorRenderHandler(this);
        this.items = new ItemRenderHandler(this);
        this.visibility = new VisibilityHandler();

        this.addRenderLayer(items.createItemLayer());
        this.addRenderLayer(new azbd.morpher.render.layer.AnimatedGeoLayer(this));

        ((MorphEntity) this.animatable).setRenderer(this, this.getCurrentEntity());
    }

    @Override
    public void render(PlayerEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public boolean hasLabel(PlayerEntity entity) {
        return false;
    }

    public void setCurrentEntity(PlayerEntity p) {
        this.currentEntity = p;
    }

    public PlayerEntity getCurrentEntity() {
        return (PlayerEntity) this.currentEntity;
    }

    public long getInstanceId(MorphEntity e) {
        return this.getCurrentEntity().getId();
    }

    @Override
    protected void applyRotations(MorphEntity e, MatrixStack mat, float age,
                                  float yaw, float tick, float nativeScale) {
        super.applyRotations(e, mat, age, yaw, tick, nativeScale);

        PlayerEntity p = this.getCurrentEntity();
        if (p == null) return;

        EntityPose pose = p.getPose();
        boolean swim = pose == EntityPose.SWIMMING;
        boolean fly  = pose == EntityPose.FALL_FLYING;

        if ((swim || fly) && !p.isOnGround()) {
            float pitch = MathHelper.clamp(-p.getPitch(), -90.0F, 90.0F);
            mat.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        }
    }

    @Override
    public void preRender(MatrixStack mat, MorphEntity e, BakedGeoModel geoModel,
                          VertexConsumerProvider buf, VertexConsumer buffer, boolean reRender,
                          float tick, int light, int overlay, int packedColor) {

        PlayerEntity p = this.getCurrentEntity();
        if (p == null) return;
        e.currentPlayer = p;

        azbd.morpher.render.handlers.MorphMolang.updateForPlayer(p, tick);

        if (p.getId() != this.lastInstanceId) {
            this.lastInstanceId = p.getId();
            e.resetArmAndPoseControllers(p.getId());
        }

        MorphData.PlayerMorphState state = MorphData.get(p);

        String modelPath = SkinResolver.resolvePlayerModelPath(p, state);
        String animPath  = (state.animation == null || state.animation.isEmpty()) ? "default" : state.animation;

        e.modelPath     = modelPath;
        e.animationPath = animPath;

        if (this.model instanceof azbd.morpher.model.PlayerModel pm) {
            pm.updateModelPath(e.modelPath, e);
            pm.updateAnimPath(e.animationPath, e, p.getId());
        }

        String layer = state.layerName;
        e.setLayerName(layer);
        armor.setLayerName(layer);

        super.preRender(mat, e, geoModel, buf, buffer, reRender, tick, light, overlay, packedColor);

        if (isGuiRender) {
            RenderSystem.applyModelViewMatrix();
            DiffuseLighting.enableGuiDepthLighting();
        }
    }

    @Override
    public void actuallyRender(MatrixStack mat, MorphEntity e, BakedGeoModel geoModel,
                               RenderLayer renderLayer, VertexConsumerProvider buf, VertexConsumer buffer,
                               boolean reRender, float tick, int light, int overlay, int packedColor) {

        MinecraftClient mc = MinecraftClient.getInstance();
        this.currentBuffer = buf;
        PlayerEntity p = getCurrentEntity();
        MorphData.PlayerMorphState state = MorphData.get(p);

        float alpha = visibility.getBodyAlpha(p, mc.player);

        int effectiveColor = (packedColor & 0x00FFFFFF) | ((int)(alpha * 255) << 24);

        RenderLayer effectiveLayer = RenderLayer.getEntityTranslucent(this.getTextureLocation(e));

        try {
            if (alpha > 0.0F) {
                if (isGuiRender) {
                    RenderSystem.applyModelViewMatrix();
                    DiffuseLighting.enableGuiDepthLighting();
                }

                super.actuallyRender(mat, e, geoModel, effectiveLayer, buf, buffer,
                        false, tick, light, overlay, effectiveColor);

                if (isGuiRender) {
                    RenderSystem.applyModelViewMatrix();
                    DiffuseLighting.enableGuiDepthLighting();
                }
            }

            if (!bonesReady) {
                bonesReady = true;
                return;
            }

            if (visibility.shouldRenderArmor(p)) {
                armor.renderAllArmor(mat, e, buf, tick, light, overlay);
            }
        } catch (Throwable ignored) {
        }
    }

    public void renderDirectly(MatrixStack mat, MorphEntity e, BakedGeoModel geoModel,
                               RenderLayer layer, VertexConsumerProvider buf, VertexConsumer buffer,
                               boolean reRender, float tick, int light, int overlay, int packedColor) {
        this.currentBuffer = buf;
        this.isRenderingLayer = true;
        try {
            super.actuallyRender(mat, e, geoModel, layer, buf, buffer,
                    reRender, tick, light, overlay, packedColor);
        } finally {
            this.isRenderingLayer = false;
        }
    }

    @Override
    public Identifier getTextureLocation(MorphEntity e) {
        PlayerEntity p = getCurrentEntity();
        if (p == null) return getDefaultTex();

        MorphData.PlayerMorphState state = MorphData.get(p);
        String path = state.texture;

        if (path == null || path.isEmpty()) return getPlayerSkin(p);
        return getCustomTex(path);
    }

    private Identifier getDefaultTex() {
        return Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
    }

    private Identifier getPlayerSkin(PlayerEntity p) {
        MorphData.PlayerMorphState state = MorphData.get(p);
        if (state != null && state.skinPlayerName != null && !state.skinPlayerName.isEmpty()) {
            Identifier skin = SkinResolver.getSkin(state.skinPlayerName);
            if (skin != null) {
                return skin;
            }
        }
        return ((net.minecraft.client.network.AbstractClientPlayerEntity) p)
                .getSkinTextures().texture();
    }

    private Identifier getCustomTex(String path) {
        return Identifier.of("morpher", "textures/" + path + ".png");
    }

    @Override
    public RenderLayer getRenderType(MorphEntity e, Identifier tex,
                                     VertexConsumerProvider buf, float tick) {
        return RenderLayer.getEntityTranslucent(this.getTextureLocation(e));
    }

    public GeoModel<MorphEntity> getPlayerModel() { return model; }
    public ArmorRenderHandler getArmorHandler()    { return armor; }
    public static boolean areBonesReady()         { return bonesReady; }
    public VisibilityHandler getVisibility()       { return visibility; }

    @Override
    public void renderCubesOfBone(MatrixStack poseStack, GeoBone bone, VertexConsumer buffer,
                                  int packedLight, int packedOverlay, int packedColor) {
        String boneName = bone.getName();
        if ("squid_number".equals(boneName) || "squid_number2".equals(boneName)) {
            PlayerEntity player = this.getCurrentEntity();
            if (player != null && this.currentBuffer != null) {
                MorphData.PlayerMorphState state = MorphData.get(player);
                boolean isFront = "squid_number2".equals(boneName);

                String textToRender = isFront ? state.overlayNumberFront : state.overlayNumberBack;

                if (textToRender != null && !textToRender.isEmpty()
                        && !textToRender.equals("ninguno") && !textToRender.equals("clear")) {
                    net.minecraft.client.font.TextRenderer font = MinecraftClient.getInstance().textRenderer;
                    poseStack.push();

                    poseStack.translate(bone.getPivotX() / 16f, bone.getPivotY() / 16f, bone.getPivotZ() / 16f);

                    poseStack.multiply(RotationAxis.POSITIVE_X.rotation(-bone.getRotX()));
                    poseStack.multiply(RotationAxis.POSITIVE_Y.rotation(-bone.getRotY()));
                    poseStack.multiply(RotationAxis.POSITIVE_Z.rotation(-bone.getRotZ()));

                    if (!bone.getCubes().isEmpty()) {
                        GeoCube cube = bone.getCubes().get(0);
                        net.minecraft.util.math.Vec3d rot = cube.rotation();
                        poseStack.multiply(RotationAxis.POSITIVE_Z.rotation((float) rot.z));
                        poseStack.multiply(RotationAxis.POSITIVE_Y.rotation((float) rot.y));
                        poseStack.multiply(RotationAxis.POSITIVE_X.rotation((float) rot.x));
                    }

                    float scale = isFront ? state.overlayNumberSizeFront : state.overlayNumberSizeBack;

                    poseStack.scale(scale, -scale, scale);

                    float textWidth = font.getWidth(textToRender);

                    float xPos = -textWidth / 2.0f;
                    float yPos = -4.5f;

                    font.draw(textToRender, xPos, yPos, 0xFFFFFFFF, false,
                            poseStack.peek().getPositionMatrix(), this.currentBuffer,
                            net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL,
                            0, packedLight);

                    poseStack.pop();
                }
            }
            return;
        }

        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, packedColor);
    }
}
