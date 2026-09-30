package com.leekwater.homestorage.network;

import com.leekwater.homestorage.permissions.MemberManagement;
import com.leekwater.homestorage.screen.InfinityStorageMenu;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ModNetworking {
    private ModNetworking() {}

    /** Both sides must know every payload type, so this runs from the common entrypoint. */
    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(StorageContentsPayload.TYPE, StorageContentsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StorageClickPayload.TYPE, StorageClickPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(StorageClickPayload.TYPE, (payload, context) -> {
            // The client could send anything, so only act if this really is the player's open storage menu.
            if (context.player().containerMenu instanceof InfinityStorageMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.handleStorageClick(payload.cell(), payload.rightClick(), payload.shift());
            }
        });

        // Member management: the client asks, MemberManagement checks everything again on the server.
        PayloadTypeRegistry.clientboundPlay().register(HomeMembersPayload.TYPE, HomeMembersPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AddMemberPayload.TYPE, AddMemberPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RemoveMemberPayload.TYPE, RemoveMemberPayload.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(DeleteHomePayload.TYPE, DeleteHomePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HomeDeletedPayload.TYPE, HomeDeletedPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DeleteHomePayload.TYPE, (payload, context) ->
                MemberManagement.delete(context.player(), payload.homeId()));

        ServerPlayNetworking.registerGlobalReceiver(AddMemberPayload.TYPE, (payload, context) ->
                MemberManagement.add(context.player(), payload.homeId(), payload.player()));
        ServerPlayNetworking.registerGlobalReceiver(RemoveMemberPayload.TYPE, (payload, context) ->
                MemberManagement.remove(context.player(), payload.homeId(), payload.member()));

        PayloadTypeRegistry.serverboundPlay().register(StorageViewPayload.TYPE, StorageViewPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(StorageViewPayload.TYPE, (payload, context) -> {
            if (context.player().containerMenu instanceof InfinityStorageMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.handleViewChange(payload.page(), payload.search(), payload.sortMode());
            }
        });
    }
}
