package azbd.morpher.morph;

import azbd.morpher.MorpherMod;
import azbd.morpher.overlay.CharacterOverlay;
import azbd.morpher.overlay.OverlayManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

public class MorphData {

    public record SyncPayload(UUID uuid, NbtCompound nbt) implements CustomPayload {
        public static final CustomPayload.Id<SyncPayload> ID =
                new CustomPayload.Id<>(Identifier.of(MorpherMod.MOD_ID, "morph_sync"));
        public static final PacketCodec<PacketByteBuf, SyncPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> { buf.writeUuid(value.uuid()); buf.writeNbt(value.nbt()); },
                        buf -> new SyncPayload(buf.readUuid(), buf.readNbt())
                );
        @Override public CustomPayload.Id<SyncPayload> getId() { return ID; }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
    }

    private static final Map<UUID, PlayerMorphState> clientData  = new HashMap<>();
    private static final Map<UUID, PlayerMorphState> serverData  = new HashMap<>();
    private static final Map<UUID, PlayerMorphState> deathBackup = new HashMap<>();

    public static class ExtraLayer {
        public String name = "";
        public String texture = "";

        public ExtraLayer() {}

        public ExtraLayer(String name, String texture) {
            this.name = name;
            this.texture = texture;
        }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("name", name);
            nbt.putString("texture", texture);
            return nbt;
        }

        public static ExtraLayer fromNbt(NbtCompound nbt) {
            ExtraLayer layer = new ExtraLayer();
            layer.name = nbt.getString("name");
            layer.texture = nbt.getString("texture");
            return layer;
        }

        public ExtraLayer copy() {
            return new ExtraLayer(name, texture);
        }
    }

    public static class PlayerMorphState {
        public String  model             = "";
        public String  texture           = "";
        public String  animation         = "";
        public boolean renderCustomModel = false;
        public boolean hasJoined         = false;
        public boolean isDead            = false;
        public String  forcedEmote       = "empty";
        public boolean loopEmote         = false;
        public boolean cancelAnimation   = false;
        public boolean instantEmote      = false;
        public String  emoteMode         = "additive";
        public String  layerName             = "";
        public boolean layerEnabled          = true;
        public int     layerTextureIndex     = 0;
        public String  activeLayerAnimation  = "";
        public int     layerAnimationVersion = 0;
        public Map<String, String> layerAnimations = new HashMap<>();
        public String  activeOverlay     = "";
        public boolean useSpecialPose    = false;
        public String  equipHead         = "";
        public String  equipChest        = "";
        public String  equipLegs         = "";
        public String  equipFeet         = "";
        public float   scale             = 0.9375f;
        public String  overlayNumberFront     = "";
        public String  overlayNumberBack      = "";
        public float   overlayNumberSizeFront = 1.0f;
        public float   overlayNumberSizeBack  = 1.0f;
        public List<ExtraLayer> extraLayers   = new ArrayList<>();
        public String  skinPlayerName    = "";
        public boolean forceModel        = false;
        public float   nametagOffset     = 0.0f;

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("model",              model);
            nbt.putString("texture",            texture);
            nbt.putString("animation",          animation);
            nbt.putBoolean("hasJoined",         hasJoined);
            nbt.putBoolean("renderCustomModel", renderCustomModel);
            nbt.putString("forcedEmote",        forcedEmote);
            nbt.putString("layerName",             layerName);
            nbt.putBoolean("layerEnabled",         layerEnabled);
            nbt.putInt("layerTextureIndex",        layerTextureIndex);
            nbt.putString("activeLayerAnimation",  activeLayerAnimation);
            nbt.putInt("layerAnimationVersion",    layerAnimationVersion);
            NbtCompound animsTag = new NbtCompound();
            for (Map.Entry<String, String> entry : layerAnimations.entrySet()) {
                animsTag.putString(entry.getKey(), entry.getValue());
            }
            nbt.put("layerAnimations", animsTag);
            nbt.putBoolean("loopEmote",            loopEmote);
            nbt.putBoolean("cancelAnimation",   cancelAnimation);
            nbt.putBoolean("isDead",            isDead);
            nbt.putBoolean("instantEmote",      instantEmote);
            nbt.putString("emoteMode",          emoteMode);
            nbt.putString("activeOverlay",      activeOverlay);
            nbt.putBoolean("useSpecialPose",    useSpecialPose);
            nbt.putString("equipHead",          equipHead);
            nbt.putString("equipChest",         equipChest);
            nbt.putString("equipLegs",          equipLegs);
            nbt.putString("equipFeet",          equipFeet);
            nbt.putFloat("scale",               scale);
            nbt.putString("overlayNumberFront", overlayNumberFront);
            nbt.putString("overlayNumberBack",  overlayNumberBack);
            nbt.putFloat("overlayNumberSizeFront", overlayNumberSizeFront);
            nbt.putFloat("overlayNumberSizeBack",  overlayNumberSizeBack);

            NbtList list = new NbtList();
            for (ExtraLayer layer : extraLayers) {
                list.add(layer.toNbt());
            }
            nbt.put("extraLayers", list);
            nbt.putString("skinPlayerName", skinPlayerName);
            nbt.putBoolean("forceModel", forceModel);
            nbt.putFloat("nametagOffset", nametagOffset);

            return nbt;
        }

        public void fromNbt(NbtCompound nbt) {
            model             = nbt.getString("model");
            texture           = nbt.getString("texture");
            animation         = nbt.getString("animation");
            hasJoined         = nbt.getBoolean("hasJoined");
            renderCustomModel = nbt.getBoolean("renderCustomModel");
            forcedEmote       = nbt.getString("forcedEmote");
            layerName             = nbt.getString("layerName");
            layerEnabled          = !nbt.contains("layerEnabled") || nbt.getBoolean("layerEnabled");
            layerTextureIndex     = nbt.contains("layerTextureIndex") ? nbt.getInt("layerTextureIndex") : 0;
            activeLayerAnimation  = nbt.contains("activeLayerAnimation") ? nbt.getString("activeLayerAnimation") : "";
            layerAnimationVersion = nbt.contains("layerAnimationVersion") ? nbt.getInt("layerAnimationVersion") : 0;
            layerAnimations.clear();
            if (nbt.contains("layerAnimations", NbtElement.COMPOUND_TYPE)) {
                NbtCompound animsTag = nbt.getCompound("layerAnimations");
                for (String key : animsTag.getKeys()) {
                    layerAnimations.put(key, animsTag.getString(key));
                }
            }
            loopEmote             = nbt.getBoolean("loopEmote");
            cancelAnimation   = nbt.getBoolean("cancelAnimation");
            isDead            = nbt.getBoolean("isDead");
            instantEmote      = nbt.getBoolean("instantEmote");
            emoteMode         = nbt.contains("emoteMode") ? nbt.getString("emoteMode") : "additive";
            activeOverlay     = nbt.contains("activeOverlay") ? nbt.getString("activeOverlay") : "";
            useSpecialPose    = nbt.contains("useSpecialPose") && nbt.getBoolean("useSpecialPose");
            equipHead         = nbt.contains("equipHead") ? nbt.getString("equipHead") : "";
            equipChest        = nbt.contains("equipChest") ? nbt.getString("equipChest") : "";
            equipLegs         = nbt.contains("equipLegs") ? nbt.getString("equipLegs") : "";
            equipFeet         = nbt.contains("equipFeet") ? nbt.getString("equipFeet") : "";
            scale             = nbt.contains("scale") ? nbt.getFloat("scale") : 0.9375f;
            overlayNumberFront     = nbt.contains("overlayNumberFront") ? nbt.getString("overlayNumberFront") : "";
            overlayNumberBack      = nbt.contains("overlayNumberBack") ? nbt.getString("overlayNumberBack") : "";
            overlayNumberSizeFront = nbt.contains("overlayNumberSizeFront") ? nbt.getFloat("overlayNumberSizeFront") : 1.0f;
            overlayNumberSizeBack  = nbt.contains("overlayNumberSizeBack") ? nbt.getFloat("overlayNumberSizeBack") : 1.0f;

            extraLayers.clear();
            if (nbt.contains("extraLayers", NbtElement.LIST_TYPE)) {
                NbtList list = nbt.getList("extraLayers", NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < list.size(); i++) {
                    extraLayers.add(ExtraLayer.fromNbt(list.getCompound(i)));
                }
            }
            skinPlayerName = nbt.contains("skinPlayerName") ? nbt.getString("skinPlayerName") : "";
            forceModel     = nbt.contains("forceModel") && nbt.getBoolean("forceModel");
            nametagOffset  = nbt.contains("nametagOffset") ? nbt.getFloat("nametagOffset") : 0.0f;
        }

        public void copyFrom(PlayerMorphState o) {
            model             = o.model;
            texture           = o.texture;
            animation         = o.animation;
            hasJoined         = o.hasJoined;
            renderCustomModel = o.renderCustomModel;
            forcedEmote       = o.forcedEmote;
            layerName             = o.layerName;
            layerEnabled          = o.layerEnabled;
            layerTextureIndex     = o.layerTextureIndex;
            activeLayerAnimation  = o.activeLayerAnimation;
            layerAnimationVersion = o.layerAnimationVersion;
            layerAnimations.clear();
            layerAnimations.putAll(o.layerAnimations);
            loopEmote             = o.loopEmote;
            cancelAnimation   = o.cancelAnimation;
            isDead            = o.isDead;
            instantEmote      = o.instantEmote;
            emoteMode         = o.emoteMode;
            activeOverlay     = o.activeOverlay;
            useSpecialPose    = o.useSpecialPose;
            equipHead         = o.equipHead;
            equipChest        = o.equipChest;
            equipLegs         = o.equipLegs;
            equipFeet         = o.equipFeet;
            scale             = o.scale;
            overlayNumberFront     = o.overlayNumberFront;
            overlayNumberBack      = o.overlayNumberBack;
            overlayNumberSizeFront = o.overlayNumberSizeFront;
            overlayNumberSizeBack  = o.overlayNumberSizeBack;

            extraLayers.clear();
            for (ExtraLayer layer : o.extraLayers) {
                extraLayers.add(layer.copy());
            }
            skinPlayerName = o.skinPlayerName;
            forceModel     = o.forceModel;
            nametagOffset  = o.nametagOffset;
        }

        public boolean isForcedEmoteMode() { return "forced".equalsIgnoreCase(emoteMode); }

        public void sync(Entity entity) {
            if (entity.getWorld().isClient) return;
            if (!(entity instanceof ServerPlayerEntity player)) return;
            sendSyncPacket(player, this);
        }
    }

    private static void sendSyncPacket(ServerPlayerEntity player, PlayerMorphState state) {
        if (player == null || player.isDisconnected()) return;

        SyncPayload payload = new SyncPayload(player.getUuid(), state.toNbt());
        sendPayload(player, payload);
        for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
            if (other != player) sendPayload(other, payload);
        }
    }

    public static void registerClientSync() {
        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, ctx) -> {
            UUID uuid        = payload.uuid();
            NbtCompound nbt  = payload.nbt();
            ctx.client().execute(() -> {
                if (nbt != null) {
                    PlayerMorphState oldState = clientData.get(uuid);
                    PlayerMorphState state = new PlayerMorphState();
                    state.fromNbt(nbt);
                    clientData.put(uuid, state);

                    if (oldState != null && oldState.isDead && !state.isDead) {
                        azbd.morpher.render.MorphRenderer renderer = MorphCache.getRenderer(uuid);
                        if (renderer != null) {
                            MorphEntity morphEntity = (MorphEntity) renderer.getAnimatable();
                            if (morphEntity != null) {
                                morphEntity.resetArmAndPoseControllers();
                            }
                        }
                    }
                }
            });
        });
    }

    public static void handlePlayerJoin(ServerPlayerEntity player) {
        PlayerMorphState state = getOrCreate(player);
        state.forcedEmote = "empty";
        boolean needsVisibilityDelay = !state.hasJoined || state.isDead;
        if (needsVisibilityDelay) state.isDead = true;
        if (!state.hasJoined) state.hasJoined = true;

        if (ForceModelManager.isGlobalForceModelEnabled()) {
            state.renderCustomModel = true;
        }
        state.sync(player);
        if (needsVisibilityDelay) {
            MorpherMod.scheduleTask(5, () -> {
                PlayerMorphState s = getOrCreate(player);
                s.isDead = false;
                s.sync(player);
            });
        }
        syncAllPlayersInWorld(player);
    }

    public static void handlePlayerDeath(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        PlayerMorphState current = getOrCreate(player);
        PlayerMorphState backup  = new PlayerMorphState();
        backup.copyFrom(current);
        deathBackup.put(uuid, backup);
        current.isDead      = true;
        current.forcedEmote = "empty";
        current.loopEmote   = false;
        current.sync(player);
        boolean instant = player.getWorld().getGameRules().getBoolean(GameRules.DO_IMMEDIATE_RESPAWN);
        if (instant) {
            MorpherMod.scheduleTask(2, () -> {
                if (player.isAlive()) {
                    PlayerMorphState s = getOrCreate(player);
                    s.isDead = false;
                    s.sync(player);
                }
            });
        }
    }

    public static void handlePlayerRespawn(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (deathBackup.containsKey(uuid)) {
            PlayerMorphState backup = deathBackup.get(uuid);
            MorpherMod.scheduleTask(5, () -> {
                PlayerMorphState s = getOrCreate(player);
                s.model             = backup.model;
                s.texture           = backup.texture;
                s.animation         = backup.animation;
                s.renderCustomModel = backup.renderCustomModel;
                s.layerName         = backup.layerName;
                s.layerEnabled          = backup.layerEnabled;
                s.layerTextureIndex     = backup.layerTextureIndex;
                s.activeLayerAnimation  = backup.activeLayerAnimation;
                s.layerAnimationVersion = backup.layerAnimationVersion;
                s.hasJoined         = backup.hasJoined;
                s.emoteMode         = backup.emoteMode;
                s.activeOverlay     = backup.activeOverlay;
                s.equipHead         = backup.equipHead;
                s.equipChest        = backup.equipChest;
                s.equipLegs         = backup.equipLegs;
                s.equipFeet         = backup.equipFeet;
                s.scale             = backup.scale;
                s.overlayNumberFront     = backup.overlayNumberFront;
                s.overlayNumberBack      = backup.overlayNumberBack;
                s.overlayNumberSizeFront = backup.overlayNumberSizeFront;
                s.overlayNumberSizeBack  = backup.overlayNumberSizeBack;

                s.extraLayers.clear();
                for (ExtraLayer layer : backup.extraLayers) {
                    s.extraLayers.add(layer.copy());
                }
                s.isDead            = false;
                s.forcedEmote       = "empty";
                s.loopEmote         = false;
                s.cancelAnimation   = false;
                s.sync(player);
            });
            deathBackup.remove(uuid);
        } else {
            MorpherMod.scheduleTask(3, () -> {
                PlayerMorphState s = getOrCreate(player);
                s.isDead = false;
                s.sync(player);
            });
        }
        syncAllPlayersInWorld(player);
        MorpherMod.scheduleTask(15, () -> refreshModel(player));
    }

    public static void handlePlayerClone(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        PlayerMorphState oldState = getOrCreate(oldPlayer);
        PlayerMorphState newState = getOrCreate(newPlayer);
        newState.copyFrom(oldState);
        newState.isDead      = false;
        newState.forcedEmote = "empty";
        newState.loopEmote   = false;
        MorpherMod.scheduleTask(3, () -> newState.sync(newPlayer));
        syncAllPlayersInWorld(newPlayer);
    }

    public static void handleDimensionChange(ServerPlayerEntity player) { syncAllPlayersInWorld(player); }

    public static void handlePlayerDisconnect(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        deathBackup.remove(uuid);
        PlayerMorphState state = getOrCreate(player);
        state.forcedEmote = "empty";
        state.isDead      = true;
        SyncPayload payload = new SyncPayload(uuid, state.toNbt());
        for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
            if (other != player) sendPayload(other, payload);
        }
    }

    public static boolean applyOverlay(ServerPlayerEntity player, String overlayKey) {
        CharacterOverlay overlay = OverlayManager.getOverlay(overlayKey);
        if (overlay == null) return false;
        PlayerMorphState state  = getOrCreate(player);
        state.model             = clean(overlay.model);
        state.texture           = clean(overlay.texture);
        state.animation         = clean(overlay.animation);
        state.renderCustomModel = true;
        state.activeOverlay     = overlayKey;
        state.emoteMode         = overlay.emote_mode != null ? overlay.emote_mode : "additive";
        if (overlay.layer != null) {
            state.layerName         = clean(overlay.layer.model);
            state.layerEnabled      = overlay.layer.enabled;
            state.layerTextureIndex = 0;
        } else {
            state.layerName = "";
        }
        if (overlay.equipment != null) {
            state.equipHead  = overlay.equipment.head  != null ? overlay.equipment.head  : "";
            state.equipChest = overlay.equipment.chest != null ? overlay.equipment.chest : "";
            state.equipLegs  = overlay.equipment.legs  != null ? overlay.equipment.legs  : "";
            state.equipFeet  = overlay.equipment.feet  != null ? overlay.equipment.feet  : "";
        } else {
            state.equipHead = state.equipChest = state.equipLegs = state.equipFeet = "";
        }
        state.sync(player);
        return true;
    }

    public static void reapplyActiveOverlay(ServerPlayerEntity player) {
        PlayerMorphState state = getOrCreate(player);
        if (state.activeOverlay == null || state.activeOverlay.isEmpty()) return;
        applyOverlay(player, state.activeOverlay);
    }

    public static void refreshModel(Entity entity) {
        if (!(entity instanceof ServerPlayerEntity player)) return;
        PlayerMorphState state = getOrCreate(player);
        if (!state.renderCustomModel || state.model.isEmpty()) return;
        String m = state.model, t = state.texture, a = state.animation, l = state.layerName;
        boolean r = state.renderCustomModel;
        state.renderCustomModel = false;
        state.sync(player);
        MorpherMod.scheduleTask(1, () -> {
            PlayerMorphState s = getOrCreate(player);
            s.model = m; s.texture = t; s.animation = a; s.layerName = l;
            s.renderCustomModel = r;
            s.sync(player);
        });
    }

    public static void reset(Entity entity) {
        if (entity.getWorld().isClient) return;
        if (!(entity instanceof ServerPlayerEntity player)) return;
        PlayerMorphState state  = getOrCreate(player);
        state.model             = "";
        state.texture           = "";
        state.animation         = "";
        state.renderCustomModel = false;
        state.forcedEmote       = "";
        state.layerName         = "";
        state.layerEnabled          = true;
        state.layerTextureIndex     = 0;
        state.activeLayerAnimation  = "";
        state.layerAnimationVersion = 0;
        state.loopEmote             = false;
        state.cancelAnimation   = false;
        state.isDead            = false;
        state.emoteMode         = "additive";
        state.activeOverlay     = "";
        state.overlayNumberFront     = "";
        state.overlayNumberBack      = "";
        state.overlayNumberSizeFront = 1.0f;
        state.overlayNumberSizeBack  = 1.0f;
        state.layerAnimations.clear();
        state.extraLayers.clear();
        state.sync(player);
    }

    private static void syncAllPlayersInWorld(Entity trigger) {
        if (!(trigger.getWorld() instanceof ServerWorld world)) return;
        for (ServerPlayerEntity p : world.getPlayers()) getOrCreate(p).sync(p);
    }

    private static PlayerMorphState getOrCreate(PlayerEntity player) {
        UUID uuid = player.getUuid();
        return player.getWorld().isClient
                ? clientData.computeIfAbsent(uuid, k -> new PlayerMorphState())
                : serverData.computeIfAbsent(uuid, k -> new PlayerMorphState());
    }

    public static PlayerMorphState get(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) return new PlayerMorphState();
        return getOrCreate(player);
    }

    public static boolean hasCustomModel(Entity entity) { return get(entity).renderCustomModel; }

    public static void setCustomModel(Entity entity, boolean enabled) {
        if (!(entity instanceof PlayerEntity player)) return;
        PlayerMorphState state = getOrCreate(player);
        state.renderCustomModel = enabled;
        if (!entity.getWorld().isClient && entity instanceof ServerPlayerEntity sp) state.sync(sp);
    }

    public static void clearAllData() {
        clientData.clear();
        serverData.clear();
        deathBackup.clear();
    }

    public static int getBackupCount() { return deathBackup.size(); }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replace(".geo.json", "").replace(".png", "").replace(".animation.json", "");
    }

    private static void sendPayload(ServerPlayerEntity player, CustomPayload payload) {
        if (player == null || player.isDisconnected()) return;
        try {
            ServerPlayNetworking.send(player, payload);
        } catch (Exception e) {
            MorpherMod.LOGGER.warn("[Morpher] Could not sync morph data to {}.", player.getName().getString());
        }
    }
}
