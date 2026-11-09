package dev.xingguang.terminalblade.network;

import dev.xingguang.terminalblade.TerminalBlade;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端把“初雪按住左键 + 目标实体”的轻量状态发给服务端。
 * 真正的攻击频率由服务端 FirstSnowHoldScheduler 控制。
 */
public record FirstSnowHoldPayload(int targetId, boolean held) implements CustomPacketPayload {
    public static final Type<FirstSnowHoldPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TerminalBlade.MOD_ID, "first_snow_hold"));

    public static final StreamCodec<FriendlyByteBuf, FirstSnowHoldPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.targetId);
                buf.writeBoolean(payload.held);
            },
            buf -> new FirstSnowHoldPayload(buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FirstSnowHoldPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> FirstSnowHoldScheduler.handle(context.player(), payload));
    }
}
