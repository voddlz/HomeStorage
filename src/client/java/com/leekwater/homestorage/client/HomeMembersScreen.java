package com.leekwater.homestorage.client;

import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;

import com.leekwater.homestorage.network.AddMemberPayload;
import com.leekwater.homestorage.network.DeleteHomePayload;
import com.leekwater.homestorage.network.HomeMembersPayload;
import com.leekwater.homestorage.network.PickablePlayer;
import com.leekwater.homestorage.network.RemoveMemberPayload;
import com.leekwater.homestorage.storage.HomeMember;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The owner's screen for a Home: its members on the left (x removes), the online players who can be added on the
 * right (+ adds), and a delete button. It only shows what the server last sent and sends requests; the server
 * decides and replies with the new state, which replaces what is shown here.
 */
public class HomeMembersScreen extends Screen {
    private static final int WIDTH = 240;
    private static final int HEIGHT = 194;

    private static final int ROWS = 6;
    private static final int ROW_HEIGHT = 14;
    private static final int LIST_TOP = 44;
    private static final int LIST_WIDTH = 110;
    private static final int LEFT_LIST_X = 7;
    private static final int RIGHT_LIST_X = 123;
    private static final int NAME_MAX_WIDTH = 84;   // leaves room for the button at the end of each row
    private static final int ROW_BUTTON_WIDTH = 16;
    private static final int PAGER_Y = 132;
    private static final int STATUS_Y = 147;    // room for two lines (STATUS_LINES) above the delete button
    private static final int DELETE_Y = 172;
    private static final int STATUS_LINES = 2;

    private static final int TEXT_COLOR = 0xFF404040;   // the dark gray vanilla uses for labels
    private static final int ERROR_COLOR = 0xFFAA0000;
    private static final int LIST_TEXT_COLOR = 0xFFFFFFFF;

    private final UUID homeId;
    private String ownerName;
    private List<HomeMember> members;
    private List<PickablePlayer> candidates;
    private String status;

    private int memberPage;
    private int candidatePage;
    /** True after the first click on Delete; the second click really deletes. */
    private boolean confirmDelete;

    private int left;
    private int top;

    public HomeMembersScreen(HomeMembersPayload data) {
        super(Component.translatable("gui.homestorage.members.title"));
        this.homeId = data.homeId();
        apply(data);
    }

    public UUID homeId() {
        return homeId;
    }

    /** The server sent a newer state for this Home. */
    public void update(HomeMembersPayload data) {
        apply(data);
        rebuildWidgets();
    }

    private void apply(HomeMembersPayload data) {
        this.ownerName = data.ownerName();
        this.members = data.members();
        this.candidates = data.candidates();
        this.status = data.status();
        this.confirmDelete = false; // any new state cancels a pending delete
        this.memberPage = Math.clamp(memberPage, 0, pageCount(members.size()) - 1);
        this.candidatePage = Math.clamp(candidatePage, 0, pageCount(candidates.size()) - 1);
    }

    private static int pageCount(int entries) {
        return Math.max(1, (entries + ROWS - 1) / ROWS);
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;

        // members: x removes
        int firstMember = memberPage * ROWS;
        for (int i = 0; i < ROWS && firstMember + i < members.size(); i++) {
            HomeMember member = members.get(firstMember + i);
            Button remove = Button.builder(Component.literal("x"), button -> removeMember(member))
                    .bounds(left + LEFT_LIST_X + LIST_WIDTH - ROW_BUTTON_WIDTH - 2, rowY(i), ROW_BUTTON_WIDTH, 12)
                    .build();
            remove.setTooltip(Tooltip.create(Component.translatable("gui.homestorage.members.remove", member.name())));
            addRenderableWidget(remove);
        }

        // online players who can be added: + adds
        int firstCandidate = candidatePage * ROWS;
        for (int i = 0; i < ROWS && firstCandidate + i < candidates.size(); i++) {
            PickablePlayer candidate = candidates.get(firstCandidate + i);
            Button add = Button.builder(Component.literal("+"), button -> addMember(candidate))
                    .bounds(left + RIGHT_LIST_X + LIST_WIDTH - ROW_BUTTON_WIDTH - 2, rowY(i), ROW_BUTTON_WIDTH, 12)
                    .build();
            add.setTooltip(Tooltip.create(Component.translatable("gui.homestorage.members.add_player", candidate.name())));
            addRenderableWidget(add);
        }

        addPager(LEFT_LIST_X, memberPage, pageCount(members.size()), page -> {
            memberPage = page;
            confirmDelete = false;
            rebuildWidgets();
        });
        addPager(RIGHT_LIST_X, candidatePage, pageCount(candidates.size()), page -> {
            candidatePage = page;
            confirmDelete = false;
            rebuildWidgets();
        });

        Component deleteLabel = confirmDelete
                ? Component.translatable("gui.homestorage.members.delete_confirm").withStyle(ChatFormatting.RED)
                : Component.translatable("gui.homestorage.members.delete");
        addRenderableWidget(Button.builder(deleteLabel, button -> onDeleteClicked())
                .bounds(left + 8, top + DELETE_Y, WIDTH - 16, 16)
                .build());
    }

    private int rowY(int row) {
        return top + LIST_TOP + row * ROW_HEIGHT;
    }

    /** Small "< >" buttons under a list; only shown when the list has more than one page. */
    private void addPager(int listX, int page, int pages, IntConsumer changePage) {
        if (pages <= 1) {
            return;
        }
        Button prev = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage.accept(page - 1))
                .bounds(left + listX + 1, top + PAGER_Y, 14, 12).build());
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage.accept(page + 1))
                .bounds(left + listX + LIST_WIDTH - 15, top + PAGER_Y, 14, 12).build());
        prev.active = page > 0;
        next.active = page < pages - 1;
    }

    private void addMember(PickablePlayer candidate) {
        confirmDelete = false;
        ClientPlayNetworking.send(new AddMemberPayload(homeId, candidate.id()));
    }

    private void removeMember(HomeMember member) {
        confirmDelete = false;
        ClientPlayNetworking.send(new RemoveMemberPayload(homeId, member.id()));
    }

    private void onDeleteClicked() {
        if (!confirmDelete) {
            confirmDelete = true;
            rebuildWidgets(); // the button now asks for a second click
        } else {
            ClientPlayNetworking.send(new DeleteHomePayload(homeId));
        }
    }

    /** A plain info screen: the game keeps running behind it (matters on a server, and in singleplayer). */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        // A vanilla-looking gray panel: black outline, light top/left edge, dark bottom/right edge.
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, 0xFF000000);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xFFC6C6C6);
        graphics.fill(left, top, left + WIDTH - 1, top + 1, 0xFFFFFFFF);
        graphics.fill(left, top, left + 1, top + HEIGHT - 1, 0xFFFFFFFF);
        graphics.fill(left + 1, top + HEIGHT - 1, left + WIDTH, top + HEIGHT, 0xFF555555);
        graphics.fill(left + WIDTH - 1, top + 1, left + WIDTH, top + HEIGHT, 0xFF555555);
        // the two inset areas the names sit on
        int listBottom = top + LIST_TOP + ROWS * ROW_HEIGHT + 1;
        graphics.fill(left + LEFT_LIST_X, top + LIST_TOP - 2, left + LEFT_LIST_X + LIST_WIDTH, listBottom, 0xFF8B8B8B);
        graphics.fill(left + RIGHT_LIST_X, top + LIST_TOP - 2, left + RIGHT_LIST_X + LIST_WIDTH, listBottom, 0xFF8B8B8B);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // Every text is drawn through drawClipped/drawWrapped, so nothing can ever run out of its space.
        graphics.centeredText(font, title, left + WIDTH / 2, top + 6, TEXT_COLOR);
        drawClipped(graphics, Component.translatable("gui.homestorage.members.owner", ownerName), left + 8, top + 19, WIDTH - 16, TEXT_COLOR);

        // column headers
        drawClipped(graphics, Component.translatable("gui.homestorage.members.list", members.size()),
                left + LEFT_LIST_X + 1, top + 32, LIST_WIDTH - 2, TEXT_COLOR);
        drawClipped(graphics, Component.translatable("gui.homestorage.members.online"),
                left + RIGHT_LIST_X + 1, top + 32, LIST_WIDTH - 2, TEXT_COLOR);

        drawNames(graphics, LEFT_LIST_X, memberPage, members.stream().map(HomeMember::name).toList(),
                Component.translatable("gui.homestorage.members.empty"));
        drawNames(graphics, RIGHT_LIST_X, candidatePage, candidates.stream().map(PickablePlayer::name).toList(),
                Component.translatable("gui.homestorage.members.none_online"));

        drawPageLabel(graphics, LEFT_LIST_X, memberPage, pageCount(members.size()));
        drawPageLabel(graphics, RIGHT_LIST_X, candidatePage, pageCount(candidates.size()));

        if (!status.isEmpty()) {
            drawWrapped(graphics, Component.translatable(status), left + 8, top + STATUS_Y, WIDTH - 16, STATUS_LINES, ERROR_COLOR, false);
        }
    }

    /** One line that is cut off at {@code maxWidth} instead of running past it. */
    private void drawClipped(GuiGraphicsExtractor graphics, Component text, int x, int y, int maxWidth, int color) {
        graphics.text(font, font.plainSubstrByWidth(text.getString(), maxWidth), x, y, color, false);
    }

    /** Text that wraps onto more lines inside {@code maxWidth}, up to {@code maxLines}; the rest is dropped. */
    private void drawWrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int maxWidth, int maxLines,
                             int color, boolean shadow) {
        int lineY = y;
        int drawn = 0;
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            if (drawn++ >= maxLines) {
                break;
            }
            graphics.text(font, line, x, lineY, color, shadow);
            lineY += font.lineHeight;
        }
    }

    private void drawNames(GuiGraphicsExtractor graphics, int listX, int page, List<String> names, Component emptyText) {
        if (names.isEmpty()) {
            // Wrapped to the list width so a longer text (or another language) can never spill out of the box.
            int y = rowY(0) + 2;
            for (FormattedCharSequence line : font.split(emptyText, LIST_WIDTH - 6)) {
                graphics.text(font, line, left + listX + 3, y, LIST_TEXT_COLOR, true);
                y += font.lineHeight;
            }
            return;
        }
        int first = page * ROWS;
        for (int i = 0; i < ROWS && first + i < names.size(); i++) {
            String name = font.plainSubstrByWidth(names.get(first + i), NAME_MAX_WIDTH);
            graphics.text(font, name, left + listX + 3, rowY(i) + 2, LIST_TEXT_COLOR, true);
        }
    }

    private void drawPageLabel(GuiGraphicsExtractor graphics, int listX, int page, int pages) {
        if (pages > 1) {
            graphics.centeredText(font, (page + 1) + "/" + pages, left + listX + LIST_WIDTH / 2, top + PAGER_Y + 2, TEXT_COLOR);
        }
    }
}
