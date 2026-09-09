package azbd.morpher;

import azbd.morpher.assets.AssetLoader;
import azbd.morpher.assets.AssetReceiver;
import azbd.morpher.render.handlers.MorphMolang;
import azbd.morpher.morph.MorphCache;
import azbd.morpher.morph.MorphData;
import pers.solid.brrp.v1.fabric.api.RRPCallback;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class MorpherClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AssetLoader.initialize();

        RRPCallback.BEFORE_VANILLA.register(packs -> {
            if (AssetLoader.isReady()) {
                packs.add(AssetLoader.getRuntimePack());
            }
        });

        MorphData.registerClientSync();
        AssetReceiver.register();

        MorphMolang.initialize();

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MorphCache.clearAll();
            AssetReceiver.limpiarEstado();
        });
    }
}
