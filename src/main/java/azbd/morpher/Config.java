package azbd.morpher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class Config {

    private static final String FILENAME = "morpher.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(FILENAME);

    private static final int MIN_SYNC_DELAY = 1;
    private static final int MAX_SYNC_DELAY = 100;

    public static boolean debugLogging = false;
    public static boolean persistOnDeath = true;
    public static int syncDelayTicks = 5;

    private static class ConfigData {
        boolean debugLogging = false;
        boolean persistOnDeath = true;
        int syncDelayTicks = 5;
    }
}