package com.leekwater.homestorage.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * The virtual inventory of one Home: item identity -> quantity.
 * Only ever touched from the server thread, so it needs no locking.
 */
public class InfinityStorage {
    /** One row of the storage, also the saved form. */
    public record Entry(ItemStackTemplate item, long count) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStackTemplate.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.LONG.fieldOf("count").forGetter(Entry::count)
        ).apply(instance, Entry::new));

        /** Network form. VAR_LONG keeps small counts small on the wire. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemStackTemplate.STREAM_CODEC, Entry::item,
                ByteBufCodecs.VAR_LONG, Entry::count,
                Entry::new);
    }

    private final Map<ItemStackTemplate, Long> counts = new HashMap<>();
    private final Runnable onChange;
    /** Goes up on every change; open menus compare it to know when their view is out of date. Not saved. */
    private long version;

    /** @param onChange called after every modification so the owner can mark the world data dirty */
    public InfinityStorage(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * Identity of a stack: the item plus its component patch, with the count stripped (always 1).
     * Two stacks with different components (name, enchantments, ...) are different keys.
     */
    public static ItemStackTemplate keyOf(ItemStack stack) {
        return ItemStackTemplate.fromNonEmptyStack(stack, 1);
    }

    /**
     * Adds the whole stack. Does not modify {@code stack}; the caller shrinks it by the returned amount.
     * The only limit is Long.MAX_VALUE per item type, which is unreachable in normal play.
     *
     * @return how many items were actually stored
     */
    public long insert(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ItemStackTemplate key = keyOf(stack);
        long current = counts.getOrDefault(key, 0L);
        long inserted = Math.min(stack.getCount(), Long.MAX_VALUE - current);
        if (inserted <= 0) {
            return 0;
        }
        counts.put(key, current + inserted);
        changed();
        return inserted;
    }

    /** @return how many items were actually removed (less than requested if the storage has fewer) */
    public long extract(ItemStackTemplate key, long amount) {
        long current = counts.getOrDefault(key, 0L);
        long extracted = Math.min(Math.max(amount, 0), current);
        if (extracted <= 0) {
            return 0;
        }
        if (extracted == current) {
            counts.remove(key); // don't keep empty rows around
        } else {
            counts.put(key, current - extracted);
        }
        changed();
        return extracted;
    }

    public long version() {
        return version;
    }

    private void changed() {
        version++;
        onChange.run();
    }

    /** Number of different item types stored (not the number of items). */
    public int typeCount() {
        return counts.size();
    }

    public long count(ItemStackTemplate key) {
        return counts.getOrDefault(key, 0L);
    }

    /** A snapshot; safe to iterate while the storage changes. */
    public List<Entry> entries() {
        List<Entry> result = new ArrayList<>(counts.size());
        counts.forEach((item, count) -> result.add(new Entry(item, count)));
        return result;
    }

    /**
     * The rows a menu should show: filtered by the search text, then sorted. Row N always means the same
     * thing for the same inputs, which paging and click handling rely on (a HashMap gives no such guarantee).
     */
    public List<Entry> query(String search, SortMode mode) {
        String needle = search.trim().toLowerCase(Locale.ROOT);
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries()) {
            if (matches(entry, needle)) {
                result.add(entry);
            }
        }

        Comparator<Entry> byName = Comparator
                .comparing((Entry e) -> BuiltInRegistries.ITEM.getKey(e.item().item().value()).toString())
                .thenComparing(e -> e.item().components().toString());
        Comparator<Entry> order = switch (mode) {
            case NAME -> byName;
            case COUNT_DESC -> Comparator.comparingLong(Entry::count).reversed().thenComparing(byName);
            case COUNT_ASC -> Comparator.comparingLong(Entry::count).thenComparing(byName);
        };
        result.sort(order);
        return result;
    }

    private static boolean matches(Entry entry, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        // "iron_ingot" is searchable as "iron ingot"
        String id = BuiltInRegistries.ITEM.getKey(entry.item().item().value()).getPath().replace('_', ' ');
        if (id.contains(needle)) {
            return true;
        }
        // also the display name, which covers renamed items (resolved in the server's language)
        return entry.item().create().getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle);
    }

    /** Loads saved rows without triggering {@code onChange}, since nothing is being modified. */
    void restore(List<Entry> saved) {
        for (Entry entry : saved) {
            counts.put(entry.item(), entry.count());
        }
    }
}
