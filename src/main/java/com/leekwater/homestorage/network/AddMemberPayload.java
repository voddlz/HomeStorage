package com.leekwater.homestorage.network;

import java.util.UUID;

import com.leekwater.homestorage.HomeStorage;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: the owner picked a player from the list. It carries only the player's id; the server
 * checks that it is a player it knows and takes the name from its own records, never from the client.
 */
public record AddMemberPayload(UUID homeId, UUID player) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AddMemberPayload> TYPE =
            new CustomPacketPayload.Type<>(HomeStorage.id("add_member"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AddMemberPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, AddMemberPayload::homeId,
            UUIDUtil.STREAM_CODEC, AddMemberPayload::player,
            AddMemberPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
