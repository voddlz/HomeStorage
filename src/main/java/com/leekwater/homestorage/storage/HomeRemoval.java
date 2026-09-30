package com.leekwater.homestorage.storage;

import com.leekwater.homestorage.HomeStorage;
import com.leekwater.homestorage.block.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** The one way a Home is permanently deleted (used by the owner's button and the admin command). */
public final class HomeRemoval {
    private HomeRemoval() {}

    /**
     * Deletes the Home and its storage for good, and breaks its Home Chest (dropping it as an item) if it is standing.
     * This checks nothing: callers decide whether deleting is allowed and what happens to the items, because
     * this WILL destroy any items still stored.
     */
    public static void delete(MinecraftServer server, StorageHome home) {
        StorageManager manager = StorageManager.get(server);
        int itemTypes = manager.storageTypeCount(home.id());

        // Remove the record first: breaking the chest afterwards would otherwise only switch the Home off.
        manager.deleteHome(home.id());
        HomeStorage.LOGGER.info("Deleted Home {} (owner {}, chest at {}, {} item type(s) destroyed)",
                home.id(), home.ownerName(), home.chestPos(), itemTypes);

        if (home.active()) {
            ServerLevel level = server.getLevel(home.dimension());
            BlockPos pos = home.chestPos();
            // Only touch a chunk that is loaded; an unloaded one would have to be force-loaded just to break a block.
            if (level != null && level.hasChunkAt(pos) && level.getBlockState(pos).is(ModBlocks.INFINITY_HOME_CHEST)) {
                level.destroyBlock(pos, true);
            }
        }
    }
}
