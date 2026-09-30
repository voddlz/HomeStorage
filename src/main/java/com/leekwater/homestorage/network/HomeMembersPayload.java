package com.leekwater.homestorage.network;

import java.util.List;
import java.util.UUID;

import com.leekwater.homestorage.HomeStorage;
import com.leekwater.homestorage.permissions.StoragePermissions;
import com.leekwater.homestorage.storage.HomeMember;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: everything the members screen shows for a Home. Sent to its owner when they open the Home
 * Chest and after every change. The client opens the screen for it, or refreshes the one already open.
 *
 * @param members    the current members
 * @param candidates players who can be added (everyone the server has seen, minus the owner and members)
 * @param status     a translation key describing why the last request failed, or "" if it worked
 */
public record HomeMembersPayload(UUID homeId, String ownerName, List<HomeMember> members,
                                 List<PickablePlayer> candidates, String status) implements CustomPacketPayload {
    /** Cap on the candidate list so one packet stays small even on a server with a long player history. */
    public static final int MAX_CANDIDATES = 100;

    public static final CustomPacketPayload.Type<HomeMembersPayload> TYPE =
            new CustomPacketPayload.Type<>(HomeStorage.id("home_members"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HomeMembersPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, HomeMembersPayload::homeId,
            ByteBufCodecs.PLAYER_NAME, HomeMembersPayload::ownerName,
            HomeMember.STREAM_CODEC.apply(ByteBufCodecs.list(StoragePermissions.MAX_MEMBERS)), HomeMembersPayload::members,
            PickablePlayer.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CANDIDATES)), HomeMembersPayload::candidates,
            ByteBufCodecs.stringUtf8(96), HomeMembersPayload::status,
            HomeMembersPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
