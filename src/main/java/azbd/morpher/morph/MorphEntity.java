package azbd.morpher.morph;

import azbd.morpher.morph.animations.*;
import azbd.morpher.render.MorphRenderer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.Arm;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.util.GeckoLibUtil;

public class MorphEntity extends LivingEntity implements GeoReplacedEntity {

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public MorphRenderer renderer;
    public String modelPath;
    public String texturePath;
    public String animationPath;
    public String textureLayer;
    public String layerName;
    public PlayerEntity currentPlayer;
    public AnimationController<MorphEntity> emoteController;
    public CombatAnimations combatAnimations;
    public AnimationController<MorphEntity> poseController;
    public AnimationController<MorphEntity> rightArmController;
    public AnimationController<MorphEntity> leftArmController;
    public AnimationController<MorphEntity> groundMovementController;

    public String currentAnimation = "empty";
    public String currentEmote = "undefined";

    private boolean emoteForced = false;

    public boolean isEmoteForced()              { return emoteForced; }
    public void setEmoteForced(boolean forced)  { this.emoteForced = forced; }

    public MorphEntity() {
        super(EntityType.PLAYER, (World) null);
        initializeAnimations();
    }

    private void initializeAnimations() {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    @Override
    public boolean shouldPlayAnimsWhileGamePaused() {
        return true;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
    }

    @Override
    public void tick() {
        super.tick();
    }

    public void setRenderer(MorphRenderer renderer, PlayerEntity player) {
        this.renderer      = renderer;
        this.currentPlayer = player;
    }

    public PlayerEntity getCurrentEntity() {
        return this.currentPlayer;
    }

    public String getEmote()           { return this.currentEmote; }
    public void setEmote(String emote) { this.currentEmote = emote; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        this.emoteController = new AnimationController<>(this, "emotes", 1, this::emotePredicate);
        controllers.add(this.emoteController);

        this.combatAnimations = new CombatAnimations(this);
        this.combatAnimations.registerController(controllers);

        this.groundMovementController = SpecialPoseAnimations.register(this);
        controllers.add(this.groundMovementController);

        this.poseController = MovementAnimations.register(this);
        controllers.add(this.poseController);

        this.rightArmController = ArmAnimations.registerRight(this);
        controllers.add(this.rightArmController);

        this.leftArmController = ArmAnimations.registerLeft(this);
        controllers.add(this.leftArmController);
    }

    private software.bernie.geckolib.animation.PlayState emotePredicate(software.bernie.geckolib.animation.AnimationState<MorphEntity> event) {
        PlayerEntity player = this.currentPlayer;
        if (player == null) return software.bernie.geckolib.animation.PlayState.STOP;

        if (this.animationPath == null || this.animationPath.isEmpty()) return software.bernie.geckolib.animation.PlayState.STOP;

        MorphData.PlayerMorphState data = MorphData.get(player);
        AnimationController<MorphEntity> controller = event.getController();

        String requestedAnim = data.forcedEmote;
        boolean loopMode = data.loopEmote;

        if (requestedAnim == null || requestedAnim.isEmpty() || requestedAnim.equals("empty")) {
            if (!this.currentAnimation.equals("empty")) {
                this.currentAnimation = "empty";
                this.currentEmote = "undefined";
                controller.forceAnimationReset();
                resetArmAndPoseControllers();
            }
            return software.bernie.geckolib.animation.PlayState.STOP;
        }

        if (!requestedAnim.equals(this.currentAnimation)) {
            this.currentAnimation = requestedAnim;
            this.currentEmote = requestedAnim;
            controller.forceAnimationReset();

            if (loopMode) {
                controller.setAnimation(software.bernie.geckolib.animation.RawAnimation.begin().thenLoop(this.currentAnimation));
            } else {
                controller.setAnimation(software.bernie.geckolib.animation.RawAnimation.begin().thenPlay(this.currentAnimation));
            }
        }

        if (!loopMode && controller.hasAnimationFinished()) {
            this.currentAnimation = "empty";
            this.currentEmote = "undefined";
            data.forcedEmote = "empty";
            resetArmAndPoseControllers();
            if (!player.getWorld().isClient() && player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                MorphData.SyncPayload payload = new MorphData.SyncPayload(serverPlayer.getUuid(), data.toNbt());
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer, payload);
            }
            return software.bernie.geckolib.animation.PlayState.STOP;
        }

        return software.bernie.geckolib.animation.PlayState.CONTINUE;
    }

    @Override
    public EntityType<?> getReplacingEntityType() {
        return EntityType.PLAYER;
    }

    @Override
    public Iterable<ItemStack> getArmorItems() {
        return null;
    }

    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack) {}

    @Override
    public Arm getMainArm() {
        return null;
    }

    public void resetArmAndPoseControllers(long id) {
        AnimatableManager<MorphEntity> manager = getAnimatableInstanceCache().getManagerForId(id);
        if (manager != null) {
            for (AnimationController<MorphEntity> controller : manager.getAnimationControllers().values()) {
                controller.forceAnimationReset();
            }
        }
    }

    public void resetArmAndPoseControllers() {
        if (this.currentPlayer != null) {
            resetArmAndPoseControllers(this.currentPlayer.getId());
        }
    }

    private void forceReset(software.bernie.geckolib.animation.AnimationController<?> ctrl) {
        if (ctrl != null) ctrl.forceAnimationReset();
    }

    public String getModelPath()             { return this.modelPath; }
    public void setModelPath(String path)    { this.modelPath = path; }

    public String getTexturePath()           { return this.texturePath; }
    public void setTexturePath(String path)  { this.texturePath = path; }

    public String getAnimationPath()             { return this.animationPath; }
    public void setAnimationPath(String path)    { this.animationPath = path; }

    public String getLayerName()             { return this.layerName; }
    public void setLayerName(String name)    { this.layerName = name; }
}
