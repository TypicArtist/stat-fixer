package net.typicartist.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.s2c.play.StatisticsS2CPacket;
import net.minecraft.stat.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.IntFunction;

@Mixin(StatisticsS2CPacket.class)
public class StatisticsS2CPacketMixin {
    private static final Logger LOG = LoggerFactory.getLogger("stat-fixer");

    @Redirect(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/codec/PacketCodecs;map(Ljava/util/function/IntFunction;Lnet/minecraft/network/codec/PacketCodec;Lnet/minecraft/network/codec/PacketCodec;)Lnet/minecraft/network/codec/PacketCodec;"
        )
    )
    private static PacketCodec<RegistryByteBuf, Object2IntMap<Stat<?>>> statfix$wrapStatMapCodec(
        IntFunction<Object2IntMap<Stat<?>>> mapFactory,
        PacketCodec<RegistryByteBuf, ?> keyCodec,
        PacketCodec<RegistryByteBuf, ?> valueCodec) {

        // Safely cast codecs
        @SuppressWarnings("unchecked")
        PacketCodec<RegistryByteBuf, Stat<?>> castKeyCodec =
                (PacketCodec<RegistryByteBuf, Stat<?>>) (PacketCodec<?, ?>) keyCodec;

        @SuppressWarnings("unchecked")
        PacketCodec<RegistryByteBuf, Integer> castValueCodec =
                (PacketCodec<RegistryByteBuf, Integer>) (PacketCodec<?, ?>) valueCodec;

        // Align types and create the base PacketCodec
        PacketCodec<RegistryByteBuf, Object2IntMap<Stat<?>>> base =
                PacketCodecs.map(mapFactory, castKeyCodec, castValueCodec);

        return new PacketCodec<>() {
            @Override
            public Object2IntMap<Stat<?>> decode(RegistryByteBuf buf) {
                int before = buf.readerIndex();
                try {
                    return base.decode(buf);
                } catch (Exception e) {
                    LOG.warn("[stat-fixer] Failed to decode stats, skipping. {}", e.toString());
                    // Skip remaining bytes to prevent affecting the next packet
                    buf.skipBytes(buf.readableBytes());
                    return new Object2IntOpenHashMap<>();
                } finally {
                    if (LOG.isDebugEnabled()) {
                        int after = buf.readerIndex();
                        LOG.debug("[stat-fixer] decode consumed {} bytes", after - before);
                    }
                }
            }

            @Override
            public void encode(RegistryByteBuf buf, Object2IntMap<Stat<?>> value) {
                try {
                    base.encode(buf, value);
                } catch (Exception e) {
                    LOG.error("[stat-fixer] Failed to encode stats. {}", e.toString());
                    throw new RuntimeException("Stat encoding failed", e);
                }
            }
        };
    }
}
