package azbd.morpher.assets;

import azbd.morpher.MorpherMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.zip.CRC32;

public class AssetReceiver {

    public record InicioPayload(int tamañoTotal, long checksum) implements CustomPayload {
        public static final CustomPayload.Id<InicioPayload> ID =
                new CustomPayload.Id<>(Identifier.of(MorpherMod.MOD_ID, "inicio_descarga"));
        public static final PacketCodec<PacketByteBuf, InicioPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> { buf.writeInt(value.tamañoTotal()); buf.writeLong(value.checksum()); },
                        buf -> new InicioPayload(buf.readInt(), buf.readLong())
                );
        @Override public CustomPayload.Id<InicioPayload> getId() { return ID; }
    }

    public record FragmentoPayload(int indice, byte[] datos) implements CustomPayload {
        public static final CustomPayload.Id<FragmentoPayload> ID =
                new CustomPayload.Id<>(Identifier.of(MorpherMod.MOD_ID, "fragmento"));
        public static final PacketCodec<PacketByteBuf, FragmentoPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> { buf.writeInt(value.indice()); buf.writeInt(value.datos().length); buf.writeBytes(value.datos()); },
                        buf -> { int idx = buf.readInt(); int len = buf.readInt(); byte[] d = new byte[len]; buf.readBytes(d); return new FragmentoPayload(idx, d); }
                );
        @Override public CustomPayload.Id<FragmentoPayload> getId() { return ID; }
    }

    public record FinPayload(int totalFragmentos, long checksum) implements CustomPayload {
        public static final CustomPayload.Id<FinPayload> ID =
                new CustomPayload.Id<>(Identifier.of(MorpherMod.MOD_ID, "fin_descarga"));
        public static final PacketCodec<PacketByteBuf, FinPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> { buf.writeInt(value.totalFragmentos()); buf.writeLong(value.checksum()); },
                        buf -> new FinPayload(buf.readInt(), buf.readLong())
                );
        @Override public CustomPayload.Id<FinPayload> getId() { return ID; }
    }

    public record DescargaCompletaPayload() implements CustomPayload {
        public static final CustomPayload.Id<DescargaCompletaPayload> ID =
                new CustomPayload.Id<>(Identifier.of(MorpherMod.MOD_ID, "descarga_completa"));
        public static final PacketCodec<PacketByteBuf, DescargaCompletaPayload> CODEC =
                PacketCodec.of((value, buf) -> {}, buf -> new DescargaCompletaPayload());
        @Override public CustomPayload.Id<DescargaCompletaPayload> getId() { return ID; }
    }

    public static final int TAMAÑO_FRAGMENTO = 32768;

    private static final Object LOCK           = new Object();
    private static byte[]  buffer              = null;
    private static int     fragmentosRecibidos = 0;
    private static long    checksumEsperado    = 0L;
    private static boolean descargando         = false;

    public static void registerPayloads() {
        PayloadTypeRegistry.configurationS2C().register(InicioPayload.ID, InicioPayload.CODEC);
        PayloadTypeRegistry.configurationS2C().register(FragmentoPayload.ID, FragmentoPayload.CODEC);
        PayloadTypeRegistry.configurationS2C().register(FinPayload.ID, FinPayload.CODEC);
        PayloadTypeRegistry.configurationC2S().register(DescargaCompletaPayload.ID, DescargaCompletaPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(InicioPayload.ID,    InicioPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FragmentoPayload.ID, FragmentoPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FinPayload.ID,       FinPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DescargaCompletaPayload.ID, DescargaCompletaPayload.CODEC);
    }

    public static void register() {
        ClientConfigurationNetworking.registerGlobalReceiver(InicioPayload.ID, (payload, ctx) -> handleInicio(payload));
        ClientConfigurationNetworking.registerGlobalReceiver(FragmentoPayload.ID, (payload, ctx) -> handleFragmento(payload));
        ClientConfigurationNetworking.registerGlobalReceiver(FinPayload.ID, (payload, ctx) -> handleFin(payload, ctx.client(), true));
        ClientPlayNetworking.registerGlobalReceiver(InicioPayload.ID,    (payload, ctx) -> handleInicio(payload));
        ClientPlayNetworking.registerGlobalReceiver(FragmentoPayload.ID, (payload, ctx) -> handleFragmento(payload));
        ClientPlayNetworking.registerGlobalReceiver(FinPayload.ID,       (payload, ctx) -> handleFin(payload, ctx.client(), false));
    }

    private static void handleInicio(InicioPayload payload) {
        synchronized (LOCK) {
            buffer              = new byte[payload.tamañoTotal()];
            checksumEsperado    = payload.checksum();
            fragmentosRecibidos = 0;
            descargando         = true;
        }
    }

    private static void handleFragmento(FragmentoPayload payload) {
        synchronized (LOCK) {
            if (!descargando || buffer == null) return;
            int offset = payload.indice() * TAMAÑO_FRAGMENTO;
            if (offset + payload.datos().length <= buffer.length) {
                System.arraycopy(payload.datos(), 0, buffer, offset, payload.datos().length);
                fragmentosRecibidos++;
            }
        }
    }

    private static void handleFin(FinPayload payload, MinecraftClient client, boolean configurationPhase) {
        byte[] zipData;
        synchronized (LOCK) {
            if (!descargando || buffer == null) return;

            CRC32 crc = new CRC32();
            crc.update(buffer);
            long checksumCalculado = crc.getValue();

            if (checksumCalculado != payload.checksum() || checksumCalculado != checksumEsperado) {
                MorpherMod.LOGGER.error("[Morpher] Checksum mismatch (calc={}, expected={})", checksumCalculado, checksumEsperado);
                limpiarEstado();
                return;
            }

            zipData = buffer;
            limpiarEstado();
        }

        try {
            AssetLoader.loadFromZip(zipData);
            client.execute(() -> {
                azbd.morpher.morph.MorphCache.clearAll();
                MinecraftClient.getInstance().reloadResources().thenRun(() ->
                        client.execute(() -> {
                            try {
                                azbd.morpher.morph.MorphCache.clearAll();
                                if (configurationPhase) {
                                    ClientConfigurationNetworking.send(new DescargaCompletaPayload());
                                } else {
                                    ClientPlayNetworking.send(new DescargaCompletaPayload());
                                }
                            } catch (Exception e) {
                                MorpherMod.LOGGER.warn("[Morpher] Could not confirm asset download.", e);
                            }
                        })
                );
            });
        } catch (Exception e) {
            MorpherMod.LOGGER.error("[Morpher] Error loading assets from zip", e);
        }
    }

    public static void limpiarEstado() {
        synchronized (LOCK) {
            buffer              = null;
            fragmentosRecibidos = 0;
            checksumEsperado    = 0L;
            descargando         = false;
        }
    }

    public static boolean estaDescargando() {
        synchronized (LOCK) {
            return descargando;
        }
    }
}
