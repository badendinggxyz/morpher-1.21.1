package azbd.morpher.command;

import azbd.morpher.assets.AssetServer;
import azbd.morpher.morph.ForceModelManager;
import azbd.morpher.morph.MorphData;
import azbd.morpher.overlay.CharacterOverlay;
import azbd.morpher.overlay.OverlayManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class MorphCommands {

    private static final long CACHE_TTL_MS = 10_000;
    private static final Map<String, CachedSuggestions> fileCache = new ConcurrentHashMap<>();

    private static String resolveEmpty(String value) {
        return "empty".equalsIgnoreCase(value) ? "" : value;
    }

    private static List<String> getCachedFiles(Path dir, String ext) {
        String key = dir.toString() + "|" + ext;
        CachedSuggestions cached = fileCache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.timestamp) < CACHE_TTL_MS) return cached.values;
        List<String> values = new ArrayList<>();
        try {
            if (Files.exists(dir)) {
                try (Stream<Path> files = Files.walk(dir)) {
                    files.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(ext))
                            .map(p -> dir.relativize(p).toString().replace('\\', '/').replace(ext, ""))
                            .forEach(values::add);
                }
            }
        } catch (IOException ignored) {}
        fileCache.put(key, new CachedSuggestions(values, now));
        return values;
    }

    public static void invalidateSuggestionCache() {
        fileCache.clear();
    }

    private static class CachedSuggestions {
        final List<String> values;
        final long timestamp;
        CachedSuggestions(List<String> v, long t) { values = v; timestamp = t; }
    }

    private static final SuggestionProvider<ServerCommandSource> MODELS = (ctx, b) -> {
        b.suggest("empty");
        getCachedFiles(AssetServer.getDataPath().resolve("geo"), ".geo.json").forEach(b::suggest);
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> TEXTURES = (ctx, b) -> {
        b.suggest("empty");
        getCachedFiles(AssetServer.getDataPath().resolve("textures"), ".png").forEach(b::suggest);
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> ANIMATIONS = (ctx, b) -> {
        b.suggest("empty");
        getCachedFiles(AssetServer.getDataPath().resolve("animations"), ".animation.json").forEach(b::suggest);
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> OVERLAYS = (ctx, b) -> {
        OverlayManager.getOverlayNames().forEach(b::suggest);
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> ACTIVE_LAYERS = (ctx, b) -> {
        try {
            java.util.Set<String> uniqueLayers = new java.util.HashSet<>();
            for (ServerPlayerEntity p : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
                MorphData.PlayerMorphState state = MorphData.get(p);
                if (state == null) continue;
                if (!state.layerName.isEmpty() && !state.layerName.equals("empty")) {
                    uniqueLayers.add(state.layerName);
                }
                if (state.extraLayers != null) {
                    for (MorphData.ExtraLayer layer : state.extraLayers) {
                        if (!layer.name.isEmpty() && !layer.name.equals("empty")) {
                            uniqueLayers.add(layer.name);
                        }
                    }
                }
            }
            uniqueLayers.forEach(b::suggest);
        } catch (Exception ignored) {}
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> EMOTE_MODE = (ctx, b) -> {
        b.suggest("additive");
        b.suggest("forced");
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> NUMBER_MODE = (ctx, b) -> {
        b.suggest("forward");
        b.suggest("back");
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> EMOTES = (ctx, b) -> {
        try {
            Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, "players");
            if (players.isEmpty()) return b.buildFuture();
            ServerPlayerEntity player = players.iterator().next();
            MorphData.PlayerMorphState state = MorphData.get(player);
            String animFile = state.animation.isEmpty() ? "default" : state.animation;
            Path animPath = AssetServer.getDataPath()
                    .resolve("animations")
                    .resolve(animFile + ".animation.json");
            if (Files.exists(animPath)) {
                String content = Files.readString(animPath);
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                if (json.has("animations")) {
                    for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("animations").entrySet()) {
                        b.suggest(e.getKey());
                    }
                }
            }
        } catch (Exception ignored) {}
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> LAYERS_WITH_ANIM = (ctx, b) -> {
        try {
            Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, "players");
            Set<String> uniqueLayers = new LinkedHashSet<>();
            for (ServerPlayerEntity player : players) {
                MorphData.PlayerMorphState state = MorphData.get(player);
                if (state == null) continue;
                if (!state.layerName.isEmpty() && !"empty".equalsIgnoreCase(state.layerName)) {
                    uniqueLayers.add(state.layerName);
                }
                if (state.extraLayers != null) {
                    for (MorphData.ExtraLayer layer : state.extraLayers) {
                        if (!layer.name.isEmpty() && !"empty".equalsIgnoreCase(layer.name)) {
                            uniqueLayers.add(layer.name);
                        }
                    }
                }
            }
            uniqueLayers.forEach(b::suggest);
        } catch (Exception ignored) {}
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> LAYER_ANIMS_FOR_LAYER = (ctx, b) -> {
        try {
            String layerName = StringArgumentType.getString(ctx, "layerName");
            b.suggest("clear");
            Path animPath = AssetServer.getDataPath()
                    .resolve("animations")
                    .resolve(layerName + ".animation.json");
            if (!Files.exists(animPath)) {
                animPath = AssetServer.getDataPath()
                        .resolve("animations")
                        .resolve("default.animation.json");
            }
            if (Files.exists(animPath)) {
                String content = Files.readString(animPath);
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                if (json.has("animations")) {
                    for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("animations").entrySet()) {
                        b.suggest(e.getKey());
                    }
                }
            }
        } catch (Exception ignored) {}
        return b.buildFuture();
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("morpher")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("reload")
                                .executes(MorphCommands::cmdGlobalReload)
                        )
                        .then(CommandManager.literal("forceModel")
                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(MorphCommands::cmdGlobalForceModel)
                                )
                        )
                        .then(CommandManager.literal("overlay")
                                .then(CommandManager.literal("reload")
                                        .executes(MorphCommands::cmdOverlaysReload)
                                )
                                .then(CommandManager.argument("players", EntityArgumentType.players())
                                        .then(CommandManager.literal("set")
                                                .then(CommandManager.argument("overlay", StringArgumentType.string())
                                                        .suggests(OVERLAYS)
                                                        .executes(MorphCommands::cmdOverlaySet)
                                                )
                                        )
                                        .then(CommandManager.literal("texturelayer")
                                                .then(CommandManager.argument("index", IntegerArgumentType.integer(0))
                                                        .executes(MorphCommands::cmdOverlayTextureLayer)
                                                )
                                        )
                                        .then(CommandManager.literal("emotemode")
                                                .then(CommandManager.argument("mode", StringArgumentType.word())
                                                        .suggests(EMOTE_MODE)
                                                        .executes(MorphCommands::cmdOverlayEmoteMode)
                                                )
                                        )
                                )
                        )
                        .then(CommandManager.argument("players", EntityArgumentType.players())
                                .then(CommandManager.literal("clear")
                                        .executes(MorphCommands::cmdClear)
                                )
                                .then(CommandManager.literal("visible")
                                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(MorphCommands::cmdVisible)
                                        )
                                )
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.literal("model")
                                                .then(CommandManager.argument("modelName", StringArgumentType.string())
                                                        .suggests(MODELS)
                                                        .executes(MorphCommands::cmdSetModel)
                                                )
                                        )
                                        .then(CommandManager.literal("texture")
                                                .then(CommandManager.argument("textureName", StringArgumentType.string())
                                                        .suggests(TEXTURES)
                                                        .executes(MorphCommands::cmdSetTexture)
                                                )
                                        )
                                        .then(CommandManager.literal("anim")
                                                .then(CommandManager.argument("animName", StringArgumentType.string())
                                                        .suggests(ANIMATIONS)
                                                        .executes(MorphCommands::cmdSetAnim)
                                                )
                                        )
                                        .then(CommandManager.literal("layer")
                                                .then(CommandManager.argument("layerName", StringArgumentType.string())
                                                        .suggests(MODELS)
                                                        .executes(MorphCommands::cmdSetLayer)
                                                )
                                        )
                                        .then(CommandManager.literal("skin")
                                                .then(CommandManager.argument("skinPlayerName", StringArgumentType.string())
                                                        .executes(MorphCommands::cmdSetSkin)
                                                )
                                        )
                                        .then(CommandManager.literal("extralayer")
                                                .then(CommandManager.literal("add")
                                                        .then(CommandManager.argument("modelName", StringArgumentType.string())
                                                                .suggests(MODELS)
                                                                .executes(MorphCommands::cmdExtraLayerAdd)
                                                        )
                                                )
                                                .then(CommandManager.literal("remove")
                                                        .then(CommandManager.argument("targetLayer", StringArgumentType.greedyString())
                                                                .suggests(ACTIVE_LAYERS)
                                                                .executes(MorphCommands::cmdExtraLayerRemove)
                                                        )
                                                )
                                                .then(CommandManager.literal("list")
                                                        .executes(MorphCommands::cmdExtraLayerList)
                                                )
                                                .then(CommandManager.literal("clear")
                                                        .executes(MorphCommands::cmdExtraLayerClear)
                                                )
                                                .then(CommandManager.literal("texture")
                                                        .then(CommandManager.argument("targetLayer", StringArgumentType.word())
                                                                .suggests(ACTIVE_LAYERS)
                                                                .then(CommandManager.argument("newTexture", StringArgumentType.string())
                                                                        .suggests(TEXTURES)
                                                                        .executes(MorphCommands::cmdExtraLayerSetTexture)
                                                                )
                                                        )
                                                )
                                        )
                                        .then(CommandManager.literal("scale")
                                                .then(CommandManager.argument("value", FloatArgumentType.floatArg(0.1f, 10.0f))
                                                        .executes(MorphCommands::cmdSetScale)
                                                )
                                                .then(CommandManager.literal("empty")
                                                        .executes(MorphCommands::cmdSetScaleDefault)
                                                )
                                                .then(CommandManager.literal("nametag")
                                                        .then(CommandManager.argument("offset", FloatArgumentType.floatArg(-10.0f, 10.0f))
                                                                .executes(MorphCommands::cmdNametagOffset)
                                                        )
                                                        .then(CommandManager.literal("empty")
                                                                .executes(MorphCommands::cmdNametagOffsetDefault)
                                                        )
                                                )
                                        )
                                        .then(CommandManager.literal("number")
                                                .then(CommandManager.argument("text", StringArgumentType.string())
                                                        .then(CommandManager.argument("mode", StringArgumentType.word())
                                                                .suggests(NUMBER_MODE)
                                                                .executes(MorphCommands::cmdSetNumber)
                                                                .then(CommandManager.argument("size", FloatArgumentType.floatArg(0.001f, 10.0f))
                                                                        .executes(MorphCommands::cmdSetNumberSize)
                                                                )
                                                        )
                                                )
                                        )
                                        .then(CommandManager.literal("layeranim")
                                                .then(CommandManager.argument("layerName", StringArgumentType.word())
                                                        .suggests(LAYERS_WITH_ANIM)
                                                        .then(CommandManager.argument("animName", StringArgumentType.string())
                                                                .suggests(LAYER_ANIMS_FOR_LAYER)
                                                                .executes(MorphCommands::cmdSetLayerAnim)
                                                        )
                                                        .then(CommandManager.literal("clear")
                                                                .executes(MorphCommands::cmdClearLayerAnim)
                                                        )
                                                )
                                        )
                                )
                                .then(CommandManager.literal("play")
                                        .then(CommandManager.argument("animName", StringArgumentType.string())
                                                .suggests(EMOTES)
                                                .then(CommandManager.argument("loop", BoolArgumentType.bool())
                                                        .executes(MorphCommands::cmdPlay)
                                                        .then(CommandManager.argument("mode", StringArgumentType.word())
                                                                .suggests(EMOTE_MODE)
                                                                .executes(MorphCommands::cmdPlayWithMode)
                                                        )
                                                )
                                        )
                                )
                                .then(CommandManager.literal("stop")
                                        .executes(MorphCommands::cmdStop)
                                )
                                .then(CommandManager.literal("specialpose")
                                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                                .executes(MorphCommands::cmdSpecialPose)
                                        )
                                )
                        )
        );
    }

    private static int cmdGlobalReload(CommandContext<ServerCommandSource> ctx) {
        invalidateSuggestionCache();
        OverlayManager.reload();
        AssetServer.invalidateCache();
        ctx.getSource().getServer().getPlayerManager().getPlayerList()
                .forEach(AssetServer::sendToPlayer);
        ctx.getSource().getServer().getPlayerManager().getPlayerList()
                .forEach(MorphData::reapplyActiveOverlay);
        return 1;
    }

    private static int cmdOverlaysReload(CommandContext<ServerCommandSource> ctx) {
        invalidateSuggestionCache();
        OverlayManager.reload();
        ctx.getSource().getServer().getPlayerManager().getPlayerList()
                .forEach(MorphData::reapplyActiveOverlay);
        return 1;
    }

    private static int cmdOverlaySet(CommandContext<ServerCommandSource> ctx) {
        try {
            Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, "players");
            String key = StringArgumentType.getString(ctx, "overlay");
            int count = 0;
            for (ServerPlayerEntity player : players) {
                if (MorphData.applyOverlay(player, key)) count++;
                else ctx.getSource().sendError(Text.literal("Overlay no encontrado: " + key));
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdOverlayTextureLayer(CommandContext<ServerCommandSource> ctx) {
        try {
            Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, "players");
            int index = IntegerArgumentType.getInteger(ctx, "index");
            for (ServerPlayerEntity player : players) {
                MorphData.PlayerMorphState state = MorphData.get(player);
                if (!state.activeOverlay.isEmpty()) {
                    CharacterOverlay overlay = OverlayManager.getOverlay(state.activeOverlay);
                    if (overlay != null && overlay.layer != null) {
                        int maxIdx = overlay.layer.textureCount() - 1;
                        if (index > maxIdx) {
                            ctx.getSource().sendError(Text.literal("Max texture index for this overlay is " + maxIdx));
                        } else {
                            state.layerTextureIndex = index;
                            state.sync(player);
                        }
                        continue;
                    }
                }
                state.layerTextureIndex = index;
                state.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdOverlayEmoteMode(CommandContext<ServerCommandSource> ctx) {
        try {
            String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
            if (!mode.equals("additive") && !mode.equals("forced")) {
                ctx.getSource().sendError(Text.literal("emotemode debe ser 'additive' o 'forced'."));
                return 0;
            }
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState state = MorphData.get(player);
                state.emoteMode = mode;
                state.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdClear(CommandContext<ServerCommandSource> ctx) {
        try {
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.model = "";
                s.texture = "";
                s.animation = "default";
                s.renderCustomModel = false;
                s.forcedEmote = "";
                s.layerName = "";
                s.layerEnabled = true;
                s.layerTextureIndex = 0;
                s.loopEmote = false;
                s.cancelAnimation = false;
                s.isDead = false;
                s.emoteMode = "additive";
                s.activeOverlay = "";
                s.useSpecialPose = false;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdVisible(CommandContext<ServerCommandSource> ctx) {
        try {
            boolean visible = BoolArgumentType.getBool(ctx, "value");
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState state = MorphData.get(player);
                state.renderCustomModel = visible;
                state.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdSetModel(CommandContext<ServerCommandSource> ctx) {
        try {
            String raw = StringArgumentType.getString(ctx, "modelName");
            String model = "empty".equalsIgnoreCase(raw) ? "player_wide" : raw;
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.model = model;
                s.renderCustomModel = true;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdSetTexture(CommandContext<ServerCommandSource> ctx) {
        try {
            String texture = resolveEmpty(StringArgumentType.getString(ctx, "textureName"));
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.texture = texture;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdSetAnim(CommandContext<ServerCommandSource> ctx) {
        try {
            String anim = resolveEmpty(StringArgumentType.getString(ctx, "animName"));
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.animation = anim;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdSetLayer(CommandContext<ServerCommandSource> ctx) {
        try {
            String layer = resolveEmpty(StringArgumentType.getString(ctx, "layerName"));
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.layerName = layer;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdSetScale(CommandContext<ServerCommandSource> ctx) {
        try {
            float scale = FloatArgumentType.getFloat(ctx, "value");
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.scale = scale;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdSetNumber(CommandContext<ServerCommandSource> ctx) {
        return doSetNumber(ctx, null);
    }

    private static int cmdSetNumberSize(CommandContext<ServerCommandSource> ctx) {
        return doSetNumber(ctx, FloatArgumentType.getFloat(ctx, "size"));
    }

    private static int doSetNumber(CommandContext<ServerCommandSource> ctx, Float size) {
        try {
            String rawText = StringArgumentType.getString(ctx, "text");
            String text = rawText.replace("&", "§");
            String mode = StringArgumentType.getString(ctx, "mode");
            boolean clearMode = rawText.isEmpty() || rawText.equalsIgnoreCase("ninguno") || rawText.equalsIgnoreCase("clear");

            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);

                if (!clearMode) {
                    boolean hasFront = false;
                    boolean hasBack = false;

                    String model = s.model.isEmpty() ? "player_wide" : s.model;
                    Path modelPath = AssetServer.getDataPath().resolve("geo").resolve(model + ".geo.json");
                    if (Files.exists(modelPath)) {
                        String content = Files.readString(modelPath);
                        if (content.contains("\"squid_number2\"")) hasFront = true;
                        if (content.contains("\"squid_number\"")) hasBack = true;
                    }

                    if (!s.layerName.isEmpty() && !s.layerName.equals("empty")) {
                        Path layerPath = AssetServer.getDataPath().resolve("geo").resolve(s.layerName + ".geo.json");
                        if (Files.exists(layerPath)) {
                            String content = Files.readString(layerPath);
                            if (content.contains("\"squid_number2\"")) hasFront = true;
                            if (content.contains("\"squid_number\"")) hasBack = true;
                        }
                    }

                    if (mode.equals("forward") && !hasFront) {
                        ctx.getSource().sendError(Text.literal("La carpeta squid_number2 (forward) no existe en el modelo de " + player.getName().getString()));
                        continue;
                    }
                    if (mode.equals("back") && !hasBack) {
                        ctx.getSource().sendError(Text.literal("La carpeta squid_number (back) no existe en el modelo de " + player.getName().getString()));
                        continue;
                    }
                }

                if (mode.equals("forward")) {
                    s.overlayNumberFront = clearMode ? "" : text;
                    if (size != null) s.overlayNumberSizeFront = size;
                }
                if (mode.equals("back")) {
                    s.overlayNumberBack = clearMode ? "" : text;
                    if (size != null) s.overlayNumberSizeBack = size;
                }

                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdPlay(CommandContext<ServerCommandSource> ctx) {
        return doPlay(ctx, null);
    }

    private static int cmdPlayWithMode(CommandContext<ServerCommandSource> ctx) {
        return doPlay(ctx, StringArgumentType.getString(ctx, "mode"));
    }

    private static int doPlay(CommandContext<ServerCommandSource> ctx, String modeOverride) {
        try {
            String emote = StringArgumentType.getString(ctx, "animName");
            boolean loop = BoolArgumentType.getBool(ctx, "loop");
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.forcedEmote = emote;
                s.loopEmote = loop;
                s.cancelAnimation = false;
                if (modeOverride != null) s.emoteMode = modeOverride.toLowerCase();
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdStop(CommandContext<ServerCommandSource> ctx) {
        try {
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.forcedEmote = "empty";
                s.loopEmote = false;
                s.cancelAnimation = true;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdSpecialPose(CommandContext<ServerCommandSource> ctx) {
        try {
            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.useSpecialPose = enabled;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }
    private static int cmdExtraLayerAdd(CommandContext<ServerCommandSource> ctx) {
        try {
            String model = StringArgumentType.getString(ctx, "modelName");
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                MorphData.ExtraLayer layer = new MorphData.ExtraLayer(model, "");
                s.extraLayers.add(layer);
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdExtraLayerRemove(CommandContext<ServerCommandSource> ctx) {
        try {
            String layerName = StringArgumentType.getString(ctx, "targetLayer");
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                if (s.layerName.equals(layerName)) {
                    s.layerName = "";
                }
                if (s.extraLayers != null) {
                    for (int i = 0; i < s.extraLayers.size(); i++) {
                        if (s.extraLayers.get(i).name.equals(layerName)) {
                            s.extraLayers.remove(i);
                            break;
                        }
                    }
                }
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdExtraLayerList(CommandContext<ServerCommandSource> ctx) {
        try {
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                int total = s.extraLayers.size() + (!s.layerName.isEmpty() ? 1 : 0);
                ctx.getSource().sendMessage(Text.literal("Layers de " + player.getName().getString() + ": " + total));
                if (!s.layerName.isEmpty()) {
                    ctx.getSource().sendMessage(Text.literal(" [principal] Model: " + s.layerName));
                }
                for (int i = 0; i < s.extraLayers.size(); i++) {
                    MorphData.ExtraLayer l = s.extraLayers.get(i);
                    ctx.getSource().sendMessage(Text.literal(" [" + i + "] Model: " + l.name + " | Tex: " + l.texture));
                }
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdExtraLayerClear(CommandContext<ServerCommandSource> ctx) {
        try {
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.layerName = "";
                s.extraLayers.clear();
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdExtraLayerSetTexture(CommandContext<ServerCommandSource> ctx) {
        try {
            String target = StringArgumentType.getString(ctx, "targetLayer");
            String newTex = resolveEmpty(StringArgumentType.getString(ctx, "newTexture"));
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                if (s.extraLayers != null) {
                    for (MorphData.ExtraLayer layer : s.extraLayers) {
                        if (layer.name.equals(target)) {
                            layer.texture = newTex;
                            break;
                        }
                    }
                    s.sync(player);
                }
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdGlobalForceModel(CommandContext<ServerCommandSource> ctx) {
        try {
            boolean force = BoolArgumentType.getBool(ctx, "value");
            ForceModelManager.setGlobalForceModel(ctx.getSource().getServer(), force);
            ctx.getSource().sendFeedback(() ->
                    Text.literal("forceModel global: " + (force ? "ACTIVADO" : "DESACTIVADO")), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("No se pudo cambiar forceModel: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdSetSkin(CommandContext<ServerCommandSource> ctx) {
        try {
            String rawSkin = StringArgumentType.getString(ctx, "skinPlayerName");
            String skin = "empty".equalsIgnoreCase(rawSkin) || "clear".equalsIgnoreCase(rawSkin) ? "" : rawSkin;
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.skinPlayerName = skin;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdNametagOffset(CommandContext<ServerCommandSource> ctx) {
        try {
            float offset = FloatArgumentType.getFloat(ctx, "offset");
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.nametagOffset = offset;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdSetScaleDefault(CommandContext<ServerCommandSource> ctx) {
        try {
            float scale = 0.9375f;
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.scale = scale;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdNametagOffsetDefault(CommandContext<ServerCommandSource> ctx) {
        try {
            float offset = 0.0f;
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.nametagOffset = offset;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdSetLayerAnim(CommandContext<ServerCommandSource> ctx) {
        try {
            String layerName = StringArgumentType.getString(ctx, "layerName");
            String anim = resolveEmpty(StringArgumentType.getString(ctx, "animName"));
            if ("clear".equalsIgnoreCase(anim)) {
                return cmdClearLayerAnim(ctx);
            }
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                if (anim.isEmpty()) {
                    s.layerAnimations.remove(layerName);
                } else {
                    s.layerAnimations.put(layerName, anim);
                }
                s.layerAnimationVersion++;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cmdClearLayerAnim(CommandContext<ServerCommandSource> ctx) {
        try {
            String layerName = StringArgumentType.getString(ctx, "layerName");
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.layerAnimations.remove(layerName);
                s.layerAnimationVersion++;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }
}
