package azbd.morpher.api;

import azbd.morpher.MorpherMod;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ArmorModelRegistry {
    private static final Gson GSON = new Gson();
    private static final String RESOURCE_PATH = "morpher/armor_models";
    private static final Map<Identifier, Entry> ENTRIES = new HashMap<>();
    private static boolean loaded = false;

    private ArmorModelRegistry() {
    }

    public static Optional<Entry> get(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        loadIfNeeded();
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        return Optional.ofNullable(ENTRIES.get(itemId));
    }

    public static void reload() {
        loaded = false;
        ENTRIES.clear();
        loadIfNeeded();
    }

    private static void loadIfNeeded() {
        if (loaded) {
            return;
        }

        loaded = true;
        ResourceManager resources = MinecraftClient.getInstance().getResourceManager();
        Map<Identifier, Resource> files = resources.findResources(RESOURCE_PATH, id -> id.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
            loadFile(file.getKey(), file.getValue());
        }

        if (!ENTRIES.isEmpty()) {
            MorpherMod.LOGGER.info("[Morpher] Se cargaron {} entradas de modelos de armadura custom.", ENTRIES.size());
        }
    }

    private static void loadFile(Identifier fileId, Resource resource) {
        try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("items") || !root.get("items").isJsonObject()) {
                return;
            }

            JsonObject items = root.getAsJsonObject("items");
            for (Map.Entry<String, com.google.gson.JsonElement> itemEntry : items.entrySet()) {
                if (!itemEntry.getValue().isJsonObject()) {
                    continue;
                }

                Identifier itemId = Identifier.tryParse(itemEntry.getKey());
                if (itemId == null) {
                    MorpherMod.LOGGER.warn("[Morpher] ID de item inválido '{}' en {}.", itemEntry.getKey(), fileId);
                    continue;
                }

                JsonObject value = itemEntry.getValue().getAsJsonObject();
                Identifier model = readIdentifier(value, "geo");
                Identifier texture = readIdentifier(value, "texture");
                Identifier animation = readIdentifier(value, "animation");

                if (model == null || texture == null) {
                    MorpherMod.LOGGER.warn("[Morpher] La entrada de armadura '{}' en {} necesita geo y texture.", itemId, fileId);
                    continue;
                }

                ENTRIES.put(itemId, new Entry(model, texture, animation));
            }
        } catch (Exception exception) {
            MorpherMod.LOGGER.error("[Morpher] No se pudo cargar el archivo de modelos de armadura {}.", fileId, exception);
        }
    }

    private static Identifier readIdentifier(JsonObject object, String key) {
        if (!object.has(key)) {
            return null;
        }

        String value = object.get(key).getAsString();
        if (value == null || value.isBlank()) {
            return null;
        }

        return Identifier.tryParse(value);
    }

    public record Entry(Identifier model, Identifier texture, Identifier animation) {
    }
}
