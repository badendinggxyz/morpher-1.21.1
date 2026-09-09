package azbd.morpher.render.handlers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.loading.math.MathParser;
import software.bernie.geckolib.loading.math.MolangQueries;
import software.bernie.geckolib.loading.math.value.Variable;
import azbd.morpher.morph.MorphEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public class MorphMolang {

    private static final Map<PlayerEntity, Map<String, Double>> actorVariables = new WeakHashMap<>();

    public static void initialize() {
        reg("query.ground_speed",        0.0);
        reg("query.vertical_speed",      0.0);
        reg("query.limb_swing",          0.0);
        reg("query.is_moving",           0.0);
        reg("query.is_on_ground",        0.0);
        reg("query.is_sprinting",        0.0);
        reg("query.is_sneaking",         0.0);
        reg("query.is_swimming",         0.0);
        reg("query.is_sleeping",         0.0);
        reg("query.is_flying",           0.0);
        reg("query.is_riding",           0.0);
        reg("query.is_using_item",       0.0);
        reg("query.item_use_time",       0.0);
        reg("query.pitch",               0.0);
        reg("query.yaw",                 0.0);
        reg("query.body_yaw",            0.0);
        reg("query.right_hand_swing",    0.0);
        reg("query.left_hand_swing",     0.0);
        reg("query.main_hand_item",      0.0);
        reg("query.off_hand_item",       0.0);
        reg("query.health",              20.0);
        reg("query.max_health",          20.0);
        reg("query.is_in_water",         0.0);
        reg("query.is_in_water_or_rain", 0.0);
        reg("query.is_on_fire",          0.0);
        reg("query.time_of_day",         0.0);
        reg("query.moon_phase",          0.0);
        reg("query.distance_from_camera",0.0);
        reg("query.actor_count",         0.0);
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        float delta = mc.getRenderTickCounter().getTickDelta(false);
        apply(mc.player, delta, mc);
    }

    public static void updateForPlayer(PlayerEntity player, float delta) {
        if (player == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        apply(player, delta, mc);
    }

    private static void apply(PlayerEntity p, float delta, MinecraftClient mc) {

        float pitch = MathHelper.lerp(delta, p.prevPitch, p.getPitch());

        float lerpBodyYaw = MathHelper.lerpAngleDegrees(delta, p.prevBodyYaw, p.bodyYaw);
        float lerpHeadYaw = MathHelper.lerpAngleDegrees(delta, p.prevHeadYaw, p.headYaw);

        if (p.hasVehicle() && p.getVehicle() instanceof LivingEntity vehicle) {
            lerpBodyYaw = MathHelper.lerpAngleDegrees(delta, vehicle.prevBodyYaw, vehicle.bodyYaw);
            float net     = lerpHeadYaw - lerpBodyYaw;
            float clamped = MathHelper.clamp(MathHelper.wrapDegrees(net), -85f, 85f);
            lerpBodyYaw   = lerpHeadYaw - clamped;
            if (clamped * clamped > 2500f) lerpBodyYaw += clamped * 0.2f;
        }

        float netHeadYaw = lerpHeadYaw - lerpBodyYaw;

        set(p, "query.pitch",    pitch);
        set(p, "query.yaw",      netHeadYaw);
        set(p, "query.body_yaw", lerpBodyYaw);

        double dx = p.getX() - p.prevX;
        double dz = p.getZ() - p.prevZ;
        double dy = p.getY() - p.prevY;
        float groundSpeed   = (float) Math.sqrt(dx * dx + dz * dz) * 20f;
        float verticalSpeed = (float) dy * 20f;
        float limbSpeed     = Math.min(p.limbAnimator.getSpeed(delta), 1f);
        float limbPos       = p.limbAnimator.getPos(delta);
        boolean isMoving    = limbSpeed > 0.01f;

        azbd.morpher.morph.MorphData.PlayerMorphState state = azbd.morpher.morph.MorphData.get(p);
        boolean hasEmote = state != null && state.forcedEmote != null && !state.forcedEmote.isEmpty() && !state.forcedEmote.equals("empty");
        if (hasEmote) {
            limbSpeed = 0f;
            limbPos = 0f;
            verticalSpeed = 0f;
            isMoving = false;
        }

        set(p, "query.ground_speed",   limbSpeed);
        set(p, "query.vertical_speed", verticalSpeed);
        set(p, "query.limb_swing",     limbPos);
        set(p, "query.is_moving",      isMoving ? 1f : 0f);

        set(p, "query.is_on_ground",         p.isOnGround()       ? 1f : 0f);
        set(p, "query.is_sprinting",         (p.isSprinting() && !hasEmote) ? 1f : 0f);
        set(p, "query.is_sneaking",          (p.isSneaking() && !hasEmote)  ? 1f : 0f);
        set(p, "query.is_swimming",          (p.isSwimming() && !hasEmote)  ? 1f : 0f);
        set(p, "query.is_sleeping",          p.isSleeping()       ? 1f : 0f);
        set(p, "query.is_flying",            p.getPose() == EntityPose.FALL_FLYING ? 1f : 0f);
        set(p, "query.is_riding",            p.hasVehicle()       ? 1f : 0f);
        set(p, "query.is_in_water",          p.isTouchingWater()  ? 1f : 0f);
        set(p, "query.is_in_water_or_rain",  p.isWet()            ? 1f : 0f);
        set(p, "query.is_on_fire",           p.isOnFire()         ? 1f : 0f);

        boolean usingItem = p.isUsingItem();
        set(p, "query.is_using_item", usingItem ? 1f : 0f);
        set(p, "query.item_use_time", usingItem ? (float) p.getItemUseTime() : 0f);

        ItemStack mainStack = p.getMainHandStack();
        ItemStack offStack  = p.getOffHandStack();
        set(p, "query.main_hand_item", (float) useActionOrdinal(mainStack));
        set(p, "query.off_hand_item",  (float) useActionOrdinal(offStack));

        set(p, "query.health",     p.getHealth());
        set(p, "query.max_health", p.getMaxHealth());

        if (mc.world != null) {
            set(p, "query.time_of_day",  (float) mc.world.getTimeOfDay() / 24000f);
            set(p, "query.moon_phase",   (float) mc.world.getMoonPhase());
            set(p, "query.actor_count",  (float) mc.world.getPlayers().size());
        }

        if (mc.gameRenderer != null) {
            set(p, "query.distance_from_camera",
                    (float) mc.gameRenderer.getCamera().getPos().distanceTo(p.getPos()));
        }

        float swingProgress = p.getHandSwingProgress(delta);
        Hand  activeHand    = p.preferredHand;
        boolean mainIsRight = p.getMainArm() == Arm.RIGHT;
        boolean mainHandActive = activeHand == Hand.MAIN_HAND;
        boolean rightSwing  = mainIsRight == mainHandActive;

        set(p, "query.right_hand_swing", rightSwing  ? swingProgress : 0f);
        set(p, "query.left_hand_swing",  !rightSwing ? swingProgress : 0f);
    }

    private static int useActionOrdinal(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return stack.getUseAction().ordinal();
    }

    private static void reg(String name, double def) {
        MathParser.registerVariable(new Variable(name, def));

        MolangQueries.setActorVariable(name, actor -> {
            if (actor.animatable() instanceof MorphEntity me && me.currentPlayer != null) {
                Map<String, Double> vars = actorVariables.get(me.currentPlayer);
                if (vars != null) return vars.getOrDefault(name, 0.0);
            }
            return 0.0;
        });
    }

    private static void set(PlayerEntity p, String name, float value) {
        double d = value;
        actorVariables.computeIfAbsent(p, k -> new HashMap<>()).put(name, d);
    }
}
