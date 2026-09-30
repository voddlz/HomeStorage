package com.leekwater.homestorage.network;

import com.leekwater.homestorage.HomeStorage;
import com.leekwater.homestorage.storage.SortMode;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: which slice of the storage the player wants to see. Only a request; the server
 * clamps the page and applies the search and sort itself.
 */
public record StorageViewPayload(int containerId, int page, String search, SortMode sortMode)
        implements CustomPacketPayload {
    /** Longest search text the codec accepts; a longer one from a modified client is rejected. */
    public static final int MAX_SEARCH_LENGTH = 50;

    public static final CustomPacketPayload.Type<StorageViewPayload> TYPE =
            new CustomPacketPayload.Type<>(HomeStorage.id("storage_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageViewPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageViewPayload::containerId,
            ByteBufCodecs.VAR_INT, StorageViewPayload::page,
            ByteBufCodecs.stringUtf8(MAX_SEARCH_LENGTH), StorageViewPayload::search,
            ByteBufCodecs.idMapper(SortMode::byId, SortMode::ordinal), StorageViewPayload::sortMode,
            StorageViewPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
