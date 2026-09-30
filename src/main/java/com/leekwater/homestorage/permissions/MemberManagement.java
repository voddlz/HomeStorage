package com.leekwater.homestorage.permissions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.leekwater.homestorage.network.HomeDeletedPayload;
import com.leekwater.homestorage.network.HomeMembersPayload;
import com.leekwater.homestorage.network.PickablePlayer;
import com.leekwater.homestorage.storage.HomeMember;
import com.leekwater.homestorage.storage.HomeRemoval;
import com.leekwater.homestorage.storage.StorageHome;
import com.leekwater.homestorage.storage.StorageManager;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side logic behind the members screen. The client only sends requests; every one is re-checked
 * here (is this the owner, is the Home still active, is the player still near the Home Chest).
 */
public final class MemberManagement {
    /** How close (in blocks, squared) the owner must stay to the Home Chest to change anything. */
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    private MemberManagement() {}

    /** Opens (or refreshes) the members screen for the owner. */
    public static void open(ServerPlayer player, StorageHome home) {
        StorageManager manager = manager(player);
        // The owner's name may have changed since the Home was created.
        manager.updateOwnerName(home.id(), player.nameAndId().name());
        send(player, manager.find(home.id()).orElse(home), "");
    }

    /** The owner picked an online player from the list. */
    public static void add(ServerPlayer player, UUID homeId, UUID targetId) {
        StorageHome home = authorize(player, homeId);
        if (home == null) {
            return;
        }

        // The name comes from the server's own record of the player, never from the client.
        ServerPlayer target = player.level().getServer().getPlayerList().getPlayer(targetId);
        if (target == null) {
            send(player, home, "message.homestorage.member_offline");
        } else if (home.isOwner(targetId)) {
            send(player, home, "message.homestorage.member_is_owner");
        } else if (home.isMember(targetId)) {
            send(player, home, "message.homestorage.member_exists");
        } else if (home.members().size() >= StoragePermissions.MAX_MEMBERS) {
            send(player, home, "message.homestorage.member_limit");
        } else {
            StorageManager manager = manager(player);
            manager.addMember(homeId, new HomeMember(targetId, target.nameAndId().name()));
            send(player, manager.find(homeId).orElse(home), "");
        }
    }

    public static void remove(ServerPlayer player, UUID homeId, UUID memberId) {
        StorageHome home = authorize(player, homeId);
        if (home == null) {
            return;
        }
        StorageManager manager = manager(player);
        manager.removeMember(homeId, memberId);
        send(player, manager.find(homeId).orElse(home), "");
    }

    /** Permanently deletes the Home, but only if its storage is empty: the owner can never lose items by clicking. */
    public static void delete(ServerPlayer player, UUID homeId) {
        StorageHome home = authorize(player, homeId);
        if (home == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (StorageManager.get(server).storageTypeCount(homeId) > 0) {
            send(player, home, "message.homestorage.delete_not_empty");
            return;
        }
        HomeRemoval.delete(server, home);
        ServerPlayNetworking.send(player, new HomeDeletedPayload(homeId)); // closes the screen
        player.sendOverlayMessage(Component.translatable("message.homestorage.home_deleted"));
    }

    /** The Home if this player is really allowed to manage it right now, otherwise null (and they are told). */
    private static @Nullable StorageHome authorize(ServerPlayer player, UUID homeId) {
        StorageHome home = manager(player).find(homeId).orElse(null);
        boolean allowed = home != null
                && home.active()
                && StoragePermissions.canManage(home, player)
                && home.dimension().equals(player.level().dimension())
                && player.distanceToSqr(Vec3.atCenterOf(home.chestPos())) <= MAX_DISTANCE_SQR;
        if (!allowed) {
            player.sendOverlayMessage(Component.translatable("message.homestorage.manage_denied"));
            return null;
        }
        return home;
    }

    /** Online players who aren't the owner or a member yet, sorted by name. */
    private static List<PickablePlayer> candidates(MinecraftServer server, StorageHome home) {
        List<PickablePlayer> result = new ArrayList<>();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (!home.isOwner(online.getUUID()) && !home.isMember(online.getUUID())) {
                result.add(new PickablePlayer(online.getUUID(), online.nameAndId().name()));
            }
        }
        result.sort(Comparator.comparing((PickablePlayer p) -> p.name().toLowerCase(Locale.ROOT)));
        return result.size() > HomeMembersPayload.MAX_CANDIDATES
                ? new ArrayList<>(result.subList(0, HomeMembersPayload.MAX_CANDIDATES))
                : result;
    }

    private static void send(ServerPlayer player, StorageHome home, String status) {
        ServerPlayNetworking.send(player, new HomeMembersPayload(home.id(), home.ownerName(), home.members(),
                candidates(player.level().getServer(), home), status));
    }

    private static StorageManager manager(ServerPlayer player) {
        return StorageManager.get(player.level().getServer());
    }
}
