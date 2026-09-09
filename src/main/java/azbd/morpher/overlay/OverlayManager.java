package azbd.morpher.overlay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import azbd.morpher.MorpherMod;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class OverlayManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path DATA_DIR    = Path.of("morpher");
    private static final Path OVERLAYS_DIR = DATA_DIR.resolve("overlays");

    private static final Map<String, CharacterOverlay> overlays = new LinkedHashMap<>();

    public static void initialize() {
        ensureFolderStructure();
        ensureExampleOverlay();
        reload();
    }

    public static void ensureFolderStructure() {
        Path[] dirs = {
                DATA_DIR,
                DATA_DIR.resolve("geo"),
                DATA_DIR.resolve("textures"),
                DATA_DIR.resolve("animations"),
                OVERLAYS_DIR
        };

        for (Path dir : dirs) {
            if (!Files.exists(dir)) {
                try {
                    Files.createDirectories(dir);
                } catch (Exception e) {
                    MorpherMod.LOGGER.error("[Morpher] No se pudo crear la carpeta: {}", dir, e);
                }
            }
        }
    }

    private static void ensureExampleOverlay() {
        Path example = OVERLAYS_DIR.resolve("example.json");
        if (Files.exists(example)) {
            return;
        }

        CharacterOverlay overlay = new CharacterOverlay();
        overlay.name = "Example";
        overlay.model = "player_wide";
        overlay.texture = "";
        overlay.animation = "default";
        overlay.emote_mode = "additive";
        overlay.layer = new CharacterOverlay.LayerDef();
        overlay.layer.enabled = false;
        overlay.equipment = new CharacterOverlay.Equipment();

        try {
            Files.writeString(example, GSON.toJson(overlay));
        } catch (Exception e) {
            MorpherMod.LOGGER.error("[Morpher] No se pudo crear el overlay de ejemplo en {}.", example, e);
        }
    }

    public static void reload() {
        overlays.clear();

        if (!Files.exists(OVERLAYS_DIR)) {
            ensureFolderStructure();
            return;
        }

        try (Stream<Path> files = Files.walk(OVERLAYS_DIR)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .forEach(OverlayManager::loadFile);
        } catch (Exception e) {
            MorpherMod.LOGGER.error("[Morpher] No se pudieron recargar los overlays.", e);
        }
    }

    private static void loadFile(Path path) {
        try (FileReader reader = new FileReader(path.toFile())) {
            String relative = OVERLAYS_DIR.relativize(path)
                    .toString()
                    .replace('\\', '/')
                    .replaceAll("\\.json$", "");

            CharacterOverlay overlay = GSON.fromJson(reader, CharacterOverlay.class);
            if (overlay != null) {
                overlays.put(relative, overlay);
            }
        } catch (Exception e) {
            MorpherMod.LOGGER.error("[Morpher] No se pudo cargar el overlay: {}", path, e);
        }
    }

    public static CharacterOverlay getOverlay(String name) {
        return overlays.get(name);
    }

    public static boolean hasOverlay(String name) {
        return overlays.containsKey(name);
    }

    public static Collection<String> getOverlayNames() {
        return Collections.unmodifiableSet(overlays.keySet());
    }

    public static Map<String, CharacterOverlay> getAll() {
        return Collections.unmodifiableMap(overlays);
    }

    public static Path getOverlaysDir() {
        return OVERLAYS_DIR;
    }

    public static Path getDataDir() {
        return DATA_DIR;
    }
}
