package azbd.morpher;

import azbd.morpher.assets.AssetReceiver;
import azbd.morpher.assets.AssetServer;
import azbd.morpher.assets.MorphConfigurationTask;
import azbd.morpher.command.MorphCommands;
import azbd.morpher.morph.MorphData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MorpherMod implements ModInitializer {

    public static final String MOD_ID = "morpher";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Collection<ScheduledTask> taskQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void onInitialize() {
        registerPayloads();
        registerCommands();
        registerServerEvents();
    }

    private void registerPayloads() {
        AssetReceiver.registerPayloads();
        MorphData.registerPayloads();
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                MorphCommands.register(dispatcher));
    }

    private void registerServerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server ->
                processScheduledTasks());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            scheduleTask(1, () -> {
                if (!player.isDisconnected()) MorphData.handlePlayerJoin(player);
            });
        });

        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, AssetReceiver.InicioPayload.ID)) {
                handler.addTask(new MorphConfigurationTask(handler));
            }
        });

        ServerConfigurationNetworking.registerGlobalReceiver(AssetReceiver.DescargaCompletaPayload.ID,
                (payload, context) -> context.networkHandler().completeTask(MorphConfigurationTask.KEY));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                MorphData.handlePlayerDisconnect(handler.getPlayer()));

        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                MorphData.handlePlayerDeath(player);
            }
        });

        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            MorphData.handlePlayerRespawn(newPlayer);
        });

        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            MorphData.handlePlayerClone(oldPlayer, newPlayer, alive);
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                AssetServer.initialize());

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            clearScheduledTasks();
            MorphData.clearAllData();
        });
    }

    private void processScheduledTasks() {
        List<ScheduledTask> tasksToRun = new ArrayList<>();
        for (ScheduledTask task : taskQueue) {
            task.ticksRemaining--;
            if (task.ticksRemaining <= 0) tasksToRun.add(task);
        }
        for (ScheduledTask task : tasksToRun) {
            try {
                task.action.run();
            } catch (Exception e) {
                LOGGER.error("[Morpher] Scheduled task failed.", e);
            } finally {
                taskQueue.remove(task);
            }
        }
    }

    public static void scheduleTask(int delayTicks, Runnable action) {
        taskQueue.add(new ScheduledTask(action, delayTicks));
    }

    public static void clearScheduledTasks() {
        taskQueue.clear();
    }

    private static class ScheduledTask {
        final Runnable action;
        int ticksRemaining;

        ScheduledTask(Runnable action, int ticksRemaining) {
            this.action = action;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
