package com.leekwater.homestorage.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.leekwater.homestorage.network.StorageClickPayload;
import com.leekwater.homestorage.network.StorageViewPayload;
import com.leekwater.homestorage.screen.InfinityStorageMenu;
import com.leekwater.homestorage.screen.InfinityStorageMenu.DisplayEntry;
import com.leekwater.homestorage.storage.SortMode;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class InfinityStorageScreen extends AbstractContainerScreen<InfinityStorageMenu> {
    // Vanilla's chest texture; only the artwork is reused, none of the chest logic.
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

    // Position of the first storage cell relative to the screen's top-left, and the cell pitch (vanilla chest layout).
    private static final int GRID_LEFT = 8;
    private static final int GRID_TOP = 18;
    private static final int CELL = 18;
    private static final int ITEM_SIZE = 16;
    private static final int HOVER_HIGHLIGHT = 0x80FFFFFF;

    // Since the game moved to SDL, mouse buttons are numbered left = 1, right = 3 (not GLFW's 0 and 1).
    // Vanilla's own container screen uses the same numbers.
    private static final int MOUSE_LEFT = 1;
    private static final int MOUSE_RIGHT = 3;

    // The controls row lives in the 17px title bar. All x values are relative to the screen's left edge.
    private static final int CONTROLS_Y = 4;
    private static final int CONTROLS_HEIGHT = 12;
    private static final int SEARCH_X = 8;
    private static final int SEARCH_WIDTH = 68;
    private static final int SORT_X = 79;
    private static final int SORT_WIDTH = 36;
    private static final int PAGE_BUTTON_WIDTH = 12;
    private static final int PREV_X = 118;
    private static final int PAGE_LABEL_CENTER_X = 143;
    private static final int NEXT_X = 156;
    private static final int LABEL_COLOR = 0xFF404040; // the dark gray vanilla uses for labels

    private EditBox searchBox;
    private Button sortButton;
    private Button prevButton;
    private Button nextButton;

    // What this client last asked the server to show. They live here (not in the widgets) so they
    // survive init() running again when the window is resized.
    private String search = "";
    private SortMode sortMode = SortMode.NAME;

    public InfinityStorageScreen(InfinityStorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 114 + InfinityStorageMenu.ROWS * 18);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    /** Runs when the screen opens and whenever the window is resized, so widgets are rebuilt from the fields above. */
    @Override
    protected void init() {
        super.init(); // sets leftPos/topPos, which the positions below depend on

        searchBox = new EditBox(this.font, leftPos + SEARCH_X, topPos + CONTROLS_Y, SEARCH_WIDTH, CONTROLS_HEIGHT,
                Component.translatable("gui.homestorage.search"));
        searchBox.setMaxLength(StorageViewPayload.MAX_SEARCH_LENGTH);
        searchBox.setHint(Component.translatable("gui.homestorage.search"));
        searchBox.setValue(search); // before the responder exists, so restoring the text doesn't send a request
        searchBox.setResponder(text -> {
            search = text;
            requestView(0); // a new search starts from the first page
        });
        addRenderableWidget(searchBox);

        sortButton = addRenderableWidget(Button.builder(sortLabel(), button -> {
                    sortMode = sortMode.next();
                    updateSortButton();
                    requestView(0);
                })
                .bounds(leftPos + SORT_X, topPos + CONTROLS_Y, SORT_WIDTH, CONTROLS_HEIGHT)
                .build());
        updateSortButton();

        prevButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> requestView(menu.getPage() - 1))
                .bounds(leftPos + PREV_X, topPos + CONTROLS_Y, PAGE_BUTTON_WIDTH, CONTROLS_HEIGHT)
                .build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> requestView(menu.getPage() + 1))
                .bounds(leftPos + NEXT_X, topPos + CONTROLS_Y, PAGE_BUTTON_WIDTH, CONTROLS_HEIGHT)
                .build());
    }

    /** Asks the server for a page. It clamps out-of-range numbers, so no bounds checks are needed here. */
    private void requestView(int page) {
        ClientPlayNetworking.send(new StorageViewPayload(menu.containerId, page, search, sortMode));
    }

    private Component sortLabel() {
        return Component.translatable("gui.homestorage.sort." + sortMode.name().toLowerCase(Locale.ROOT));
    }

    private void updateSortButton() {
        sortButton.setMessage(sortLabel());
        sortButton.setTooltip(Tooltip.create(
                Component.translatable("gui.homestorage.sort." + sortMode.name().toLowerCase(Locale.ROOT) + ".tooltip")));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            return super.keyPressed(event); // Escape must still close the screen while typing
        }
        // While the search box has focus it gets the keys first. Without this, typing the inventory key
        // ("e" by default) would close the screen instead of typing a letter.
        return searchBox.keyPressed(event) || searchBox.canConsumeInput() || super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        int storageHeight = InfinityStorageMenu.ROWS * 18 + 17;
        // top part: title bar + storage slots, then the player inventory part from further down the texture
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 0.0F, 0.0F, this.imageWidth, storageHeight, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top + storageHeight, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // the title is replaced by the controls row, so only the inventory label is drawn
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOR, false);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        prevButton.active = menu.getPage() > 0;
        nextButton.active = menu.getPage() < menu.getPageCount() - 1;

        super.extractContents(graphics, mouseX, mouseY, partialTick);

        String pageText = (menu.getPage() + 1) + "/" + menu.getPageCount();
        graphics.centeredText(this.font, pageText, leftPos + PAGE_LABEL_CENTER_X, topPos + CONTROLS_Y + 2, LABEL_COLOR);

        // The storage cells are not real Slots (a real stack can't show "1.2M"), so they are drawn by hand.
        List<DisplayEntry> rows = menu.getDisplay();
        for (int i = 0; i < rows.size(); i++) {
            DisplayEntry row = rows.get(i);
            int x = cellX(i);
            int y = cellY(i);
            graphics.item(row.stack(), x, y);
            // null lets vanilla draw nothing for a single item instead of a "1"
            String countText = row.count() == 1 ? null : CountFormat.abbreviate(row.count());
            graphics.itemDecorations(this.font, row.stack(), x, y, countText);
        }

        int hovered = hoveredRow(mouseX, mouseY);
        if (hovered >= 0 && hovered < rows.size()) {
            graphics.fill(cellX(hovered), cellY(hovered), cellX(hovered) + ITEM_SIZE, cellY(hovered) + ITEM_SIZE, HOVER_HIGHLIGHT);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int hovered = hoveredRow(mouseX, mouseY);
        List<DisplayEntry> rows = menu.getDisplay();
        if (hovered >= 0 && hovered < rows.size() && menu.getCarried().isEmpty()) {
            DisplayEntry row = rows.get(hovered);
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(row.stack()));
            // the exact number, since the slot only shows the abbreviated one
            lines.add(Component.translatable("gui.homestorage.stored", String.format("%,d", row.count()))
                    .withStyle(ChatFormatting.GRAY));
            graphics.setTooltipForNextFrame(this.font, lines, row.stack().getTooltipImage(), mouseX, mouseY,
                    row.stack().get(DataComponents.TOOLTIP_STYLE), true);
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int cell = hoveredRow((int) event.x(), (int) event.y());
        if (cell >= 0) {
            boolean left = event.button() == MOUSE_LEFT;
            boolean right = event.button() == MOUSE_RIGHT;
            if (left || right) {
                // Only a request; the server decides what happens and sends the result back.
                ClientPlayNetworking.send(new StorageClickPayload(menu.containerId, cell, right, event.hasShiftDown()));
            }
            return true; // consumed, so vanilla doesn't treat it as a click outside every slot
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int cellX(int index) {
        return leftPos + GRID_LEFT + (index % InfinityStorageMenu.COLUMNS) * CELL;
    }

    private int cellY(int index) {
        return topPos + GRID_TOP + (index / InfinityStorageMenu.COLUMNS) * CELL;
    }

    /** Index of the storage cell under the mouse, or -1. The +1 covers the slot frame around each 16px item. */
    private int hoveredRow(int mouseX, int mouseY) {
        int relX = mouseX - leftPos - GRID_LEFT + 1;
        int relY = mouseY - topPos - GRID_TOP + 1;
        if (relX < 0 || relY < 0
                || relX >= InfinityStorageMenu.COLUMNS * CELL
                || relY >= InfinityStorageMenu.ROWS * CELL) {
            return -1;
        }
        return (relY / CELL) * InfinityStorageMenu.COLUMNS + relX / CELL;
    }
}
