package com.leekwater.homestorage.storage;

import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One Home: the area anchored by an Infinity Home Chest.
 * Immutable on purpose, so the manager is the only thing that adds or changes Homes.
 *
 * @param ownerName last known name of the owner, for messages (the UUID in {@code owner} is the real identity)
 * @param active    false while the Home Chest is missing. The Home and its storage are kept, but Access Chests
 *                  don't open and the area stays reserved until the owner places a Home Chest on the same spot.
 * @param members   players besides the owner who may use the Home's Access Chests
 */
public record StorageHome(UUID id, UUID owner, String ownerName, ResourceKey<Level> dimension, BlockPos chestPos,
                          int radius, boolean active, List<HomeMember> members) {
    /** Blocks in each direction from the Home Chest. Kept as a constant until it becomes configurable. */
    public static final int DEFAULT_RADIUS = 128;

    // Fields added after the first release are optional, so older saves still load (as active Homes without members).
    public static final Codec<StorageHome> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(StorageHome::id),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(StorageHome::owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(StorageHome::ownerName),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(StorageHome::dimension),
            BlockPos.CODEC.fieldOf("chest_pos").forGetter(StorageHome::chestPos),
            Codec.INT.fieldOf("radius").forGetter(StorageHome::radius),
            Codec.BOOL.optionalFieldOf("active", true).forGetter(StorageHome::active),
            HomeMember.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(StorageHome::members)
    ).apply(instance, StorageHome::new));

    public StorageHome withActive(boolean newActive) {
        return new StorageHome(id, owner, ownerName, dimension, chestPos, radius, newActive, members);
    }

    public StorageHome withOwnerName(String newOwnerName) {
        return new StorageHome(id, owner, newOwnerName, dimension, chestPos, radius, active, members);
    }

    public StorageHome withMembers(List<HomeMember> newMembers) {
        return new StorageHome(id, owner, ownerName, dimension, chestPos, radius, active, List.copyOf(newMembers));
    }

    public boolean isOwner(UUID player) {
        return owner.equals(player);
    }

    public boolean isMember(UUID player) {
        for (HomeMember member : members) {
            if (member.id().equals(player)) {
                return true;
            }
        }
        return false;
    }

    /** True if a Home of {@code otherRadius} centered at {@code otherPos} would share any block with this one. */
    public boolean overlaps(ResourceKey<Level> otherDimension, BlockPos otherPos, int otherRadius) {
        int reach = radius + otherRadius;
        return dimension.equals(otherDimension)
                && Math.abs(otherPos.getX() - chestPos.getX()) <= reach
                && Math.abs(otherPos.getY() - chestPos.getY()) <= reach
                && Math.abs(otherPos.getZ() - chestPos.getZ()) <= reach;
    }

    /** Square (cube) area check: within {@code radius} on every axis, in the same dimension. */
    public boolean contains(ResourceKey<Level> otherDimension, BlockPos pos) {
        return dimension.equals(otherDimension)
                && Math.abs(pos.getX() - chestPos.getX()) <= radius
                && Math.abs(pos.getY() - chestPos.getY()) <= radius
                && Math.abs(pos.getZ() - chestPos.getZ()) <= radius;
    }
}
