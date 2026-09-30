package com.leekwater.homestorage.network;

import java.util.UUID;

import com.leekwater.homestorage.HomeStorage;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the owner asks to permanently delete a Home. Refused by the server unless its storage is empty. */
public record DeleteHomePayload(UUID homeId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DeleteHomePayload> TYPE =
            new CustomPacketPayload.Type<>(HomeStorage.id("delete_home"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteHomePayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, DeleteHomePayload::homeId,
            DeleteHomePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
