package com.leekwater.homestorage.network;

import java.util.UUID;

import com.leekwater.homestorage.HomeStorage;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the owner asks to remove a member from a Home. */
public record RemoveMemberPayload(UUID homeId, UUID member) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RemoveMemberPayload> TYPE =
            new CustomPacketPayload.Type<>(HomeStorage.id("remove_member"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveMemberPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RemoveMemberPayload::homeId,
            UUIDUtil.STREAM_CODEC, RemoveMemberPayload::member,
            RemoveMemberPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
