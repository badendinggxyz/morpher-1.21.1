package azbd.morpher.model;

import azbd.morpher.morph.MorphData;
import azbd.morpher.morph.MorphEntity;
import azbd.morpher.render.handlers.MorphMolang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class PlayerModel extends GeoModel<MorphEntity> {

    private static final String DEFAULT_MODEL = "player_wide";
    private static final String DEFAULT_ANIM  = "default";
    private static final float  DEG_TO_RAD    = (float) Math.PI / 180F;

    private String modelPath = DEFAULT_MODEL;
    private String animPath  = DEFAULT_ANIM;

    public void updateModelPath(String path, MorphEntity entity) {
        if (this.modelPath.equals(path)) return;
        this.modelPath = path;
        try {
            this.getBakedModel(this.getModelResource(entity));
        } catch (Exception ignored) {}
    }

    public void updateAnimPath(String path, MorphEntity entity, long instanceId) {
        if (this.animPath.equals(path)) return;
        this.animPath = path;
        entity.resetArmAndPoseControllers(instanceId);
    }

    @Override
    public Identifier getModelResource(MorphEntity entity) {
        Identifier id = Identifier.of("morpher", "geo/" + modelPath + ".geo.json");
        if (exists(id)) return id;
        Identifier fallback = Identifier.of("morpher", "geo/player_wide.geo.json");
        return exists(fallback) ? fallback : id;
    }

    @Override
    public Identifier getAnimationResource(MorphEntity entity) {
        Identifier id = Identifier.of("morpher", "animations/" + animPath + ".animation.json");
        return exists(id) ? id : Identifier.of("morpher", "animations/default.animation.json");
    }

    @Override
    public Identifier getTextureResource(MorphEntity entity) {
        return null;
    }

    @Override
    public void setCustomAnimations(MorphEntity entity, long instanceId, AnimationState<MorphEntity> state) {
        super.setCustomAnimations(entity, entity.getCurrentEntity().getId(), state);

        PlayerEntity p = entity.getCurrentEntity();
        MorphData.PlayerMorphState morphState = MorphData.get(p);

        float delta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
        MorphMolang.updateForPlayer(p, delta);

        scaleBonesToDefault();

        GeoBone root = getAnimationProcessor().getBone("root");
        if (root != null && morphState.scale != 1.0f) {
            root.setScaleX(morphState.scale);
            root.setScaleY(morphState.scale);
            root.setScaleZ(morphState.scale);
        }

        GeoBone head = getAnimationProcessor().getBone("head");

        EntityModelData data  = state.getData(DataTickets.ENTITY_MODEL_DATA);
        float pitch = data.headPitch() * DEG_TO_RAD;
        float yaw   = data.netHeadYaw() * DEG_TO_RAD;

        boolean swimming = p.getPose() == EntityPose.SWIMMING;
        boolean flying   = p.getPose() == EntityPose.FALL_FLYING;
        boolean hasEmote = morphState.forcedEmote != null && !morphState.forcedEmote.isEmpty() && !morphState.forcedEmote.equals("empty");
        boolean blend    = hasEmote || swimming;

        rotateHead(head, pitch, yaw, swimming, flying, blend, state);

        updateLayerVisibility(p);
    }

    private void updateLayerVisibility(PlayerEntity p) {
        setBoneVisibility("head_scale_layer", p.isPartVisible(net.minecraft.entity.player.PlayerModelPart.HAT));

        boolean jacketVisible = p.isPartVisible(net.minecraft.entity.player.PlayerModelPart.JACKET);
        setBoneVisibility("body_upper_scale_layer", jacketVisible);
        setBoneVisibility("body_lower_scale_layer", jacketVisible);

        boolean leftSleeveVisible = p.isPartVisible(net.minecraft.entity.player.PlayerModelPart.LEFT_SLEEVE);
        setBoneVisibility("left_arm_scale_layer", leftSleeveVisible);
        setBoneVisibility("left_hand_scale_layer", leftSleeveVisible);

        boolean rightSleeveVisible = p.isPartVisible(net.minecraft.entity.player.PlayerModelPart.RIGHT_SLEEVE);
        setBoneVisibility("right_arm_scale_layer", rightSleeveVisible);
        setBoneVisibility("right_hand_scale_layer", rightSleeveVisible);

        boolean leftPantsVisible = p.isPartVisible(net.minecraft.entity.player.PlayerModelPart.LEFT_PANTS_LEG);
        setBoneVisibility("left_leg_scale_layer", leftPantsVisible);
        setBoneVisibility("left_foot_scale_layer", leftPantsVisible);

        boolean rightPantsVisible = p.isPartVisible(net.minecraft.entity.player.PlayerModelPart.RIGHT_PANTS_LEG);
        setBoneVisibility("right_leg_scale_layer", rightPantsVisible);
        setBoneVisibility("right_foot_scale_layer", rightPantsVisible);
    }

    private void setBoneVisibility(String boneName, boolean visible) {
        GeoBone bone = getAnimationProcessor().getBone(boneName);
        if (bone != null) {
            bone.setHidden(!visible);
        }
    }

    private void scaleBonesToDefault() {
        for (GeoBone bone : getAnimationProcessor().getRegisteredBones()) {
            bone.setScaleX(1.0f);
            bone.setScaleY(1.0f);
            bone.setScaleZ(1.0f);
        }
    }

    private void rotateHead(GeoBone head, float pitch, float yaw,
                            boolean swimming, boolean flying, boolean blend,
                            AnimationState<?> state) {
        if (head == null) return;
        if (swimming || flying) {
            headWithAnim(head, pitch, yaw);
        } else if (!blend && !hasAnim(state)) {
            headDirect(head, pitch, yaw);
        } else {
            headBlended(head, pitch, yaw);
        }
    }

    private void headWithAnim(GeoBone head, float pitch, float yaw) {
        float baseX = head.getInitialSnapshot().getRotX();
        float baseY = head.getInitialSnapshot().getRotY();
        head.setRotX(baseX + pitch);
        head.setRotY(baseY + yaw);
        head.setRotZ(0.0f);
        head.markRotationAsChanged();
    }

    private void headDirect(GeoBone head, float pitch, float yaw) {
        head.setRotX(pitch);
        head.setRotY(yaw);
        head.setRotZ(0.0f);
        head.markRotationAsChanged();
    }

    private void headBlended(GeoBone head, float pitch, float yaw) {
        float baseX = head.getInitialSnapshot().getRotX();
        float baseY = head.getInitialSnapshot().getRotY();
        head.setRotX(baseX + pitch);
        head.setRotY(baseY + yaw);
        head.markRotationAsChanged();
    }

    private boolean hasAnim(AnimationState<?> state) {
        return state.getController() != null &&
                state.getController().getCurrentAnimation() != null;
    }

    private boolean exists(Identifier id) {
        try {
            return MinecraftClient.getInstance().getResourceManager().getResource(id).isPresent();
        } catch (Exception e) {
            return false;
        }
    }
}