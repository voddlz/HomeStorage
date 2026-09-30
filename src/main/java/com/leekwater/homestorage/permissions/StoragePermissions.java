package com.leekwater.homestorage.permissions;

import com.leekwater.homestorage.storage.StorageHome;

import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;

/**
 * The single place that answers "may this player do that with this Home?". Everything that checks a
 * permission goes through here, so changing a rule means changing one method.
 *
 * Server operators (game master level or higher, the same level vanilla asks for command blocks) bypass every
 * rule, so an admin can always clean up a Home whose owner is gone.
 */
public final class StoragePermissions {
    /** Most members one Home can have; keeps the member list packet and screen small. */
    public static final int MAX_MEMBERS = 30;

    private StoragePermissions() {}

    public static boolean isAdmin(Player player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /** Open the Access Chests and use the storage, and place Access Chests in the Home. */
    public static boolean canUse(StorageHome home, Player player) {
        return isAdmin(player) || home.isOwner(player.getUUID()) || home.isMember(player.getUUID());
    }

    /** Add and remove members. */
    public static boolean canManage(StorageHome home, Player player) {
        return isAdmin(player) || home.isOwner(player.getUUID());
    }

    /** Members (and the owner, and ops) may break the Access Chests of their Home. The Home Chest is never breakable. */
    public static boolean canBreakAccessChest(StorageHome home, Player player) {
        return canUse(home, player);
    }
}
