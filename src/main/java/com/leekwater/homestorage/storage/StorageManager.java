package com.leekwater.homestorage.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.leekwater.homestorage.HomeStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Owns every Home and every Home's storage, and saves them with the world.
 * One instance per world (not per dimension), because Homes can exist in any dimension.
 */
public class StorageManager extends SavedData {
    /** The saved shape: what is written to disk, separate from the live objects. */
    private record SavedStorage(UUID home, List<InfinityStorage.Entry> items) {
        static final Codec<SavedStorage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("home").forGetter(SavedStorage::home),
                InfinityStorage.Entry.CODEC.listOf().fieldOf("items").forGetter(SavedStorage::items)
        ).apply(instance, SavedStorage::new));
    }

    private record Saved(List<StorageHome> homes, List<SavedStorage> storages) {
        static final Codec<Saved> CURRENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                StorageHome.CODEC.listOf().fieldOf("homes").forGetter(Saved::homes),
                SavedStorage.CODEC.listOf().optionalFieldOf("storages", List.of()).forGetter(Saved::storages)
        ).apply(instance, Saved::new));

        /** The first save format was a bare list of Homes; still readable so existing worlds keep working. */
        static final Codec<Saved> CODEC = Codec.withAlternative(
                CURRENT_CODEC,
                StorageHome.CODEC.listOf().xmap(homes -> new Saved(homes, List.of()), Saved::homes));
    }

    private static final SavedDataType<StorageManager> TYPE = new SavedDataType<>(
            HomeStorage.id("storage_manager"),
            StorageManager::new,
            Saved.CODEC.xmap(StorageManager::fromSaved, StorageManager::toSaved),
            null); // no data fixers needed yet; revisit if the saved format ever changes

    private final Map<UUID, StorageHome> homes = new HashMap<>();
    private final Map<UUID, InfinityStorage> storages = new HashMap<>();

    public StorageManager() {}

    private static StorageManager fromSaved(Saved saved) {
        StorageManager manager = new StorageManager();
        for (StorageHome home : saved.homes()) {
            manager.homes.put(home.id(), home);
        }
        for (SavedStorage savedStorage : saved.storages()) {
            InfinityStorage storage = manager.createStorage();
            storage.restore(savedStorage.items());
            manager.storages.put(savedStorage.home(), storage);
        }
        return manager;
    }

    /** Every storage reports changes back here, so any insert/extract gets saved with the world. */
    private InfinityStorage createStorage() {
        return new InfinityStorage(() -> setDirty());
    }

    private Saved toSaved() {
        List<SavedStorage> savedStorages = new ArrayList<>();
        storages.forEach((homeId, storage) -> {
            List<InfinityStorage.Entry> items = storage.entries();
            if (!items.isEmpty()) {
                savedStorages.add(new SavedStorage(homeId, items));
            }
        });
        return new Saved(List.copyOf(homes.values()), savedStorages);
    }

    /** How many different item types the Home's storage holds (0 = empty). */
    public int storageTypeCount(UUID homeId) {
        InfinityStorage storage = storages.get(homeId);
        return storage == null ? 0 : storage.typeCount();
    }

    /**
     * Removes a Home and its storage for good. Callers must have decided what happens to the items first;
     * nothing is checked here, so this can destroy items.
     */
    public void deleteHome(UUID homeId) {
        boolean removedHome = homes.remove(homeId) != null;
        boolean removedStorage = storages.remove(homeId) != null;
        if (removedHome || removedStorage) {
            setDirty();
        }
    }

    public static StorageManager get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public StorageHome createHome(UUID owner, String ownerName, ResourceKey<Level> dimension, BlockPos chestPos) {
        StorageHome home = new StorageHome(UUID.randomUUID(), owner, ownerName, dimension, chestPos,
                StorageHome.DEFAULT_RADIUS, true, List.of());
        homes.put(home.id(), home);
        setDirty(); // without this, Minecraft doesn't know it must write the change to disk
        return home;
    }

    /** The storage of a Home, created empty on first use so Homes that predate storages just work. */
    public InfinityStorage storageFor(UUID homeId) {
        return storages.computeIfAbsent(homeId, id -> createStorage());
    }

    /** The Home whose area contains this position. Homes never overlap, so there is at most one. */
    public Optional<StorageHome> findContaining(ResourceKey<Level> dimension, BlockPos pos) {
        return homes.values().stream()
                .filter(home -> home.contains(dimension, pos))
                .findFirst();
    }

    /**
     * The existing Home that a new default-radius Home at this spot would collide with, if any.
     * A Home the placer is allowed to take back (see {@link #findReclaimable}) does not count as a conflict.
     *
     * @param placer who is placing the Home Chest, or null if unknown
     */
    public Optional<StorageHome> findConflicting(ResourceKey<Level> dimension, BlockPos chestPos, @Nullable UUID placer) {
        return homes.values().stream()
                .filter(home -> home.overlaps(dimension, chestPos, StorageHome.DEFAULT_RADIUS))
                .filter(home -> !isReclaimableBy(home, dimension, chestPos, placer))
                .findFirst();
    }

    /** An inactive Home the placer may take back: same spot, same owner. */
    public Optional<StorageHome> findReclaimable(ResourceKey<Level> dimension, BlockPos chestPos, UUID placer) {
        return findByChest(dimension, chestPos).filter(home -> isReclaimableBy(home, dimension, chestPos, placer));
    }

    private static boolean isReclaimableBy(StorageHome home, ResourceKey<Level> dimension, BlockPos chestPos, @Nullable UUID placer) {
        return placer != null
                && !home.active()
                && home.dimension().equals(dimension)
                && home.chestPos().equals(chestPos)
                && home.owner().equals(placer);
    }

    public Optional<StorageHome> find(UUID homeId) {
        return Optional.ofNullable(homes.get(homeId));
    }

    /** Adds a member unless they are already one. Ownership and limits are checked by the caller. */
    public void addMember(UUID homeId, HomeMember member) {
        StorageHome home = homes.get(homeId);
        if (home != null && !home.isMember(member.id())) {
            List<HomeMember> updated = new ArrayList<>(home.members());
            updated.add(member);
            homes.put(homeId, home.withMembers(updated));
            setDirty();
        }
    }

    public void removeMember(UUID homeId, UUID memberId) {
        StorageHome home = homes.get(homeId);
        if (home != null && home.isMember(memberId)) {
            List<HomeMember> updated = new ArrayList<>(home.members());
            updated.removeIf(member -> member.id().equals(memberId));
            homes.put(homeId, home.withMembers(updated));
            setDirty();
        }
    }

    /** Keeps the stored owner name current after a name change. */
    public void updateOwnerName(UUID homeId, String ownerName) {
        StorageHome home = homes.get(homeId);
        if (home != null && !home.ownerName().equals(ownerName)) {
            homes.put(homeId, home.withOwnerName(ownerName));
            setDirty();
        }
    }

    /** Switches a Home on or off. Its storage is never touched. */
    public void setActive(UUID homeId, boolean active) {
        StorageHome home = homes.get(homeId);
        if (home != null && home.active() != active) {
            homes.put(homeId, home.withActive(active));
            setDirty();
        }
    }

    /** O(1), so open menus can check it every tick. */
    public boolean isActive(UUID homeId) {
        StorageHome home = homes.get(homeId);
        return home != null && home.active();
    }

    /** Called when a Home Chest disappears: the Home it anchored becomes inactive (returned if there was one). */
    public Optional<StorageHome> deactivateAt(ResourceKey<Level> dimension, BlockPos chestPos) {
        Optional<StorageHome> home = findByChest(dimension, chestPos).filter(StorageHome::active);
        home.ifPresent(h -> setActive(h.id(), false));
        return home;
    }

    public Optional<StorageHome> findByChest(ResourceKey<Level> dimension, BlockPos chestPos) {
        return homes.values().stream()
                .filter(home -> home.dimension().equals(dimension) && home.chestPos().equals(chestPos))
                .findFirst();
    }

    public Collection<StorageHome> all() {
        return new ArrayList<>(homes.values());
    }
}
