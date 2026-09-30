package com.leekwater.homestorage.network;

import java.util.List;

import com.leekwater.homestorage.HomeStorage;
import com.leekwater.homestorage.storage.InfinityStorage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the page of storage rows the player's open menu should display.
 * Only one page is sent (not the whole storage), because a big storage can hold thousands of item types
 * and a single packet can't carry that.
 *
 * @param containerId  the menu this belongs to, so a late packet for a closed menu is ignored
 * @param page         the page the server actually used (0-based, after clamping the request)
 * @param totalMatches how many entries match the current search, across all pages
 * @param entries      the rows of this page
 */
public record StorageContentsPayload(int containerId, int page, int totalMatches, List<InfinityStorage.Entry> entries)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StorageContentsPayload> TYPE =
            new CustomPacketPayload.Type<>(HomeStorage.id("storage_contents"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageContentsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageContentsPayload::containerId,
            ByteBufCodecs.VAR_INT, StorageContentsPayload::page,
            ByteBufCodecs.VAR_INT, StorageContentsPayload::totalMatches,
            InfinityStorage.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), StorageContentsPayload::entries,
            StorageContentsPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
