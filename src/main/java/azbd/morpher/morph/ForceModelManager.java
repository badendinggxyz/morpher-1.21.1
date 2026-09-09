package azbd.morpher.morph;

import azbd.morpher.MorpherMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.function.Supplier;

public class ForceModelManager extends PersistentState {

    private static final String DATA_NAME = "morpher_force_model";

    private boolean forceModelEnabled = false;

    private static MinecraftServer currentServer = null;

    public ForceModelManager() {}

    public boolean isEnabled() {
        return forceModelEnabled;
    }

    public void setEnabled(boolean enabled) {
        this.forceModelEnabled = enabled;
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putBoolean("forceModel", forceModelEnabled);
        return nbt;
    }

    private static ForceModelManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        ForceModelManager state = new ForceModelManager();
        state.forceModelEnabled = nbt.getBoolean("forceModel");
        return state;
    }

    private static final PersistentState.Type<ForceModelManager> TYPE = new PersistentState.Type<>(
            ForceModelManager::new,
            ForceModelManager::fromNbt,
            null
    );

    public static ForceModelManager get(MinecraftServer server) {
        if (server == null) return new ForceModelManager();
        currentServer = server;
        ServerWorld overworld = server.getOverworld();
        PersistentStateManager manager = overworld.getPersistentStateManager();
        return manager.getOrCreate(TYPE, DATA_NAME);
    }

    public static ForceModelManager get() {
        if (currentServer != null) {
            try {
                return get(currentServer);
            } catch (Exception ignored) {
            }
        }
        return new ForceModelManager();
    }

    public static boolean isGlobalForceModelEnabled() {
        return get().isEnabled();
    }

    public static boolean setGlobalForceModel(MinecraftServer server, boolean enabled) {
        ForceModelManager state = get(server);
        state.setEnabled(enabled);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            MorphData.PlayerMorphState ps = MorphData.get(player);
            if (enabled) {
                ps.renderCustomModel = true;
            }
            ps.sync(player);
        }
        MorpherMod.LOGGER.info("[Morpher] Global forceModel set to {}.", enabled);
        return enabled;
    }
}
