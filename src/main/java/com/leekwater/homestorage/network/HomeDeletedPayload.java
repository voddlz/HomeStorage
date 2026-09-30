package com.leekwater.homestorage.network;

import java.util.UUID;

import com.leekwater.homestorage.HomeStorage;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: this Home no longer exists, so a members screen open for it should close. */
public record HomeDeletedPayload(UUID homeId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HomeDeletedPayload> TYPE =
            new CustomPacketPayload.Type<>(HomeStorage.id("home_deleted"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HomeDeletedPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, HomeDeletedPayload::homeId,
            HomeDeletedPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
