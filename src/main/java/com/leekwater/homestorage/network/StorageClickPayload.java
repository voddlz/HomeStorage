package com.leekwater.homestorage.network;

import com.leekwater.homestorage.HomeStorage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: the player clicked in the storage area. This is only a request; the server decides
 * what actually happens and validates every field.
 *
 * @param containerId which menu the click was made in
 * @param cell        index of the clicked cell (0-based, row by row) in the page the client is showing
 * @param rightClick  right button instead of left
 * @param shift       shift held
 */
public record StorageClickPayload(int containerId, int cell, boolean rightClick, boolean shift)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StorageClickPayload> TYPE =
            new CustomPacketPayload.Type<>(HomeStorage.id("storage_click"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageClickPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageClickPayload::containerId,
            ByteBufCodecs.VAR_INT, StorageClickPayload::cell,
            ByteBufCodecs.BOOL, StorageClickPayload::rightClick,
            ByteBufCodecs.BOOL, StorageClickPayload::shift,
            StorageClickPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
