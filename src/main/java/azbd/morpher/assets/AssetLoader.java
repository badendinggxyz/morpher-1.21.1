package azbd.morpher.assets;

import azbd.morpher.MorpherMod;
import pers.solid.brrp.v1.api.RuntimeResourcePack;
import net.minecraft.util.Identifier;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AssetLoader {

    private static RuntimeResourcePack runtimePack;
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized) return;
        runtimePack = RuntimeResourcePack.create(Identifier.of(MorpherMod.MOD_ID, "runtime"));
        initialized = true;
    }

    public static RuntimeResourcePack getRuntimePack() {
        return runtimePack;
    }

    public static void clearResources() {
        if (runtimePack == null) return;
        runtimePack = RuntimeResourcePack.create(Identifier.of(MorpherMod.MOD_ID, "runtime"));
    }

    public static void loadFromZip(byte[] zipData) throws IOException {
        if (runtimePack == null) {
            MorpherMod.LOGGER.error("[Morpher] Error: pack no inicializado");
            return;
        }

        Map<String, byte[]> files = extractZip(zipData);

        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String path = entry.getKey().replace('\\', '/');
            byte[] data = entry.getValue();

            if (path.startsWith("assets/")) path = path.substring("assets/".length());

            String[] parts = path.split("/", 2);
            if (parts.length < 2) continue;

            String namespace = parts[0].toLowerCase(java.util.Locale.ROOT).trim();
            String subPath   = parts[1].toLowerCase(java.util.Locale.ROOT).trim();

            try {
                Identifier id = Identifier.of(namespace, subPath);
                runtimePack.addAsset(id, data);
            } catch (Exception e) {
                MorpherMod.LOGGER.warn("[Morpher] Invalid asset identifier path: {}/{} ({})", namespace, subPath, e.getMessage());
            }
        }
    }

    public static void addTexture(String namespace, String path, byte[] data) {
        if (runtimePack == null) return;
        runtimePack.addAsset(Identifier.of(namespace, "textures/" + path), data);
    }

    public static void addModel(String namespace, String modelName, byte[] data) {
        if (runtimePack == null) return;
        runtimePack.addAsset(Identifier.of(namespace, "geo/" + modelName), data);
    }

    public static void addAnimation(String namespace, String animName, byte[] data) {
        if (runtimePack == null) return;
        runtimePack.addAsset(Identifier.of(namespace, "animations/" + animName), data);
    }

    private static Map<String, byte[]> extractZip(byte[] zipData) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) { zip.closeEntry(); continue; }
                files.put(entry.getName(), readAllBytes(zip));
                zip.closeEntry();
            }
        }
        return files;
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int bytesRead;
        while ((bytesRead = input.read(chunk, 0, chunk.length)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    public static boolean isReady() {
        return initialized && runtimePack != null;
    }
}