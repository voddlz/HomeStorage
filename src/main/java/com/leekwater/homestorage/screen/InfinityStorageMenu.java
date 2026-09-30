package com.leekwater.homestorage.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import com.leekwater.homestorage.block.ModBlocks;
import com.leekwater.homestorage.blockentity.InfinityAccessChestBlockEntity;
import com.leekwater.homestorage.network.StorageContentsPayload;
import com.leekwater.homestorage.storage.InfinityStorage;
import com.leekwater.homestorage.storage.SortMode;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * The storage GUI's menu. It exists on both sides: on the server it holds the real storage and is the
 * authority; on the client it only holds a display copy of the page the server sent.
 */
public class InfinityStorageMenu extends AbstractContainerMenu {
    /** Rows of the (virtual) storage area drawn above the player inventory. */
    public static final int ROWS = 6;
    public static final int COLUMNS = 9;
    public static final int PAGE_SIZE = ROWS * COLUMNS;

    private static final int SLOT_LEFT = 8;
    private static final int PLAYER_INVENTORY_TOP = 18 + ROWS * 18 + 13;

    /** One drawn cell of the storage area; the stack has count 1, the real amount is {@code count}. */
    public record DisplayEntry(ItemStack stack, long count) {}

    private final BlockPos chestPos;
    private final BooleanSupplier homeActive;
    private final ContainerLevelAccess access;
    private final Player player;
    /** Server only: the chest whose lid this menu opens; null on the client. */
    private final @Nullable InfinityAccessChestBlockEntity chest;

    /** Server only (null on the client): where items really live. */
    private final @Nullable InfinityStorage storage;
    /** Server only: exactly what was last sent, so later clicks are checked against the server's own view. */
    private List<InfinityStorage.Entry> sentPage = List.of();
    /** Server only: the storage version that {@link #sentPage} was built from. */
    private long sentVersion = -1;

    /** Server only: what this player is currently looking at (the request, clamped when sent). */
    private String search = "";
    private SortMode sortMode = SortMode.NAME;

    /** Used on both sides: the server's current page, and on the client the page being displayed. */
    private int page;

    /** Client only: what to draw. */
    private List<DisplayEntry> display = List.of();
    private int totalMatches;

    /** Client-side constructor (used by the menu type when the server opens a menu). */
    public InfinityStorageMenu(int containerId, Inventory inventory, BlockPos chestPos) {
        this(containerId, inventory, chestPos, null, null, () -> true);
    }

    /**
     * Server-side constructor.
     *
     * @param chest      the chest block entity to open the lid of, or null if it isn't available
     * @param homeActive asked every tick; when it turns false (the Home Chest was removed) the menu closes itself
     */
    public InfinityStorageMenu(int containerId, Inventory inventory, BlockPos chestPos, @Nullable InfinityStorage storage,
                               @Nullable InfinityAccessChestBlockEntity chest, BooleanSupplier homeActive) {
        super(ModMenus.INFINITY_STORAGE, containerId);
        this.homeActive = homeActive;
        this.chestPos = chestPos;
        this.access = ContainerLevelAccess.create(inventory.player.level(), chestPos);
        this.player = inventory.player;
        this.storage = storage;
        this.chest = chest;
        addStandardInventorySlots(inventory, SLOT_LEFT, PLAYER_INVENTORY_TOP);
        if (chest != null) {
            chest.startOpen(inventory.player); // opens the lid and plays the sound
        }
    }

    /** Called when the player closes the menu (or disconnects). */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (chest != null) {
            chest.stopOpen(player);
        }
    }

    public BlockPos getChestPos() {
        return chestPos;
    }

    /** Called by vanilla when the menu opens (and on full resyncs), so this is where the first page goes out. */
    @Override
    public void sendAllDataToRemote() {
        super.sendAllDataToRemote();
        sendContents();
    }

    /** Runs every tick for an open menu; this is what makes other players' changes show up live. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (storage != null && storage.version() != sentVersion) {
            sendContents();
        }
    }

    private void sendContents() {
        if (storage == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        sentVersion = storage.version();
        List<InfinityStorage.Entry> matches = storage.query(search, sortMode);

        int pageCount = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.clamp(page, 0, pageCount - 1); // the request may be out of range, e.g. after items were removed
        int from = page * PAGE_SIZE;
        sentPage = List.copyOf(matches.subList(from, Math.min(from + PAGE_SIZE, matches.size())));

        ServerPlayNetworking.send(serverPlayer, new StorageContentsPayload(containerId, page, matches.size(), sentPage));
    }

    /** Server only: the player changed page, search text or sort mode. */
    public void handleViewChange(int page, String search, SortMode sortMode) {
        if (storage == null) {
            return;
        }
        this.page = page;
        this.search = search;
        this.sortMode = sortMode;
        sendContents();
    }

    /**
     * Server only: a click in the storage area, already routed here by the network handler.
     * Nothing from the client is trusted beyond "which cell"; amounts are computed here.
     */
    public void handleStorageClick(int cell, boolean rightClick, boolean shift) {
        if (storage == null) {
            return;
        }
        ItemStack carried = getCarried();
        if (!carried.isEmpty()) {
            insertCarried(carried, rightClick ? 1 : carried.getCount());
        } else if (cell >= 0 && cell < sentPage.size()) {
            // Looked up in what the server sent, not what the client claims to see.
            ItemStackTemplate key = sentPage.get(cell).item();
            if (shift) {
                moveStackToInventory(key);
            } else {
                takeIntoCursor(key, rightClick);
            }
        }
    }

    private void insertCarried(ItemStack carried, int amount) {
        long stored = storage.insert(carried.copyWithCount(Math.min(amount, carried.getCount())));
        carried.shrink((int) stored);
        setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
    }

    /** A full stack, or half of it on right-click (like vanilla), capped by what is actually stored. */
    private int stackSizeFor(ItemStackTemplate key, boolean half) {
        int maxStack = key.create().getMaxStackSize();
        int amount = (int) Math.min(storage.count(key), maxStack);
        return half ? (amount + 1) / 2 : amount;
    }

    private void takeIntoCursor(ItemStackTemplate key, boolean half) {
        int wanted = stackSizeFor(key, half);
        long taken = storage.extract(key, wanted);
        if (taken > 0) {
            setCarried(key.withCount((int) taken).create());
        }
    }

    private void moveStackToInventory(ItemStackTemplate key) {
        int wanted = stackSizeFor(key, false);
        if (wanted <= 0) {
            return;
        }
        // Inventory.add shrinks the stack it is given to whatever did not fit, so the difference is what moved.
        ItemStack offered = key.withCount(wanted).create();
        player.getInventory().add(offered);
        storage.extract(key, wanted - offered.getCount());
    }

    /** Client only: replaces what is drawn with the page the server sent. */
    public void applyContents(int page, int totalMatches, List<InfinityStorage.Entry> entries) {
        this.page = page;
        this.totalMatches = totalMatches;
        List<DisplayEntry> rows = new ArrayList<>(entries.size());
        for (InfinityStorage.Entry entry : entries) {
            ItemStack stack = entry.item().create();
            if (!stack.isEmpty()) { // create() gives EMPTY for an item this client can't build; just skip it
                rows.add(new DisplayEntry(stack, entry.count()));
            }
        }
        this.display = rows;
    }

    public List<DisplayEntry> getDisplay() {
        return display;
    }

    public int getPage() {
        return page;
    }

    public int getPageCount() {
        return Math.max(1, (totalMatches + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override
    public boolean stillValid(Player player) {
        // Closes the screen if the chest is broken, the player walks away, or the Home Chest is removed.
        return homeActive.getAsBoolean() && stillValid(access, player, ModBlocks.INFINITY_ACCESS_CHEST);
    }

    /** Shift-click on an inventory slot: store the whole stack. Returning EMPTY tells vanilla we are done. */
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (storage == null) {
            return ItemStack.EMPTY; // client side: the server's result arrives through normal syncing
        }
        Slot slot = slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            long stored = storage.insert(stack);
            stack.shrink((int) stored);
            slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
            slot.setChanged();
        }
        return ItemStack.EMPTY;
    }
}
