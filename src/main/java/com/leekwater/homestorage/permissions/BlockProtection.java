package com.leekwater.homestorage.permissions;

import java.util.Optional;

import com.leekwater.homestorage.block.ModBlocks;
import com.leekwater.homestorage.storage.StorageHome;
import com.leekwater.homestorage.storage.StorageManager;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** Stops players who aren't allowed from breaking a Home's chests. Runs on the server, before the block breaks. */
public final class BlockProtection {
    private BlockProtection() {}

    public static void init() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel serverLevel)) {
                return true;
            }
            StorageManager manager = StorageManager.get(serverLevel.getServer());

            if (state.is(ModBlocks.INFINITY_HOME_CHEST)) {
                // A Home Chest that anchors a Home is never broken by hand, by anyone (ops and creative included).
                // It is removed only by the "Delete Home" button or the admin command, which both delete the Home
                // with it. (The block itself is also unbreakable in survival, so this mostly catches creative mode.)
                // A leftover Home Chest with no Home behind it is not protected, so it can still be cleaned up.
                if (manager.findByChest(serverLevel.dimension(), pos).isPresent()) {
                    player.sendOverlayMessage(Component.translatable("message.homestorage.home_chest_unbreakable"));
                    return false; // cancels the break; Fabric resyncs the block to the client
                }
            } else if (state.is(ModBlocks.INFINITY_ACCESS_CHEST)) {
                Optional<StorageHome> home = manager.findContaining(serverLevel.dimension(), pos);
                if (home.isPresent() && !StoragePermissions.canBreakAccessChest(home.get(), player)) {
                    player.sendOverlayMessage(Component.translatable("message.homestorage.break_access_denied", home.get().ownerName()));
                    return false;
                }
            }
            return true;
        });
    }
}
