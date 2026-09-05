package dev.xyat.kineticrecipe.recipehud.client.gui;

import dev.xyat.kineticcore.api.client.AdaptiveItemGridRenderer;
import dev.xyat.kineticcore.api.client.AdvancedSearchUtil;
import dev.xyat.kineticcore.api.client.GuiRenderUtil;
import dev.xyat.kineticcore.api.client.GuiToastUtil;
import dev.xyat.kineticcore.api.client.PinyinUtil;
import dev.xyat.kineticcore.api.client.ScaledScreen;
import dev.xyat.kineticcore.api.client.gui.GridScrollController;
import dev.xyat.kineticrecipe.recipehud.RecipeDatabase;
import dev.xyat.kineticrecipe.recipehud.RecipeRecord;
import dev.xyat.kineticrecipe.recipehud.RecipeRegistry;
import dev.xyat.kineticrecipe.recipehud.network.RecipeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecipePreviewScreen extends ScaledScreen {
    private static final int SLOT_SIZE = 22;
    private static final int SLOT_GAP = 1;
    private static final int CELL_SIZE = SLOT_SIZE + SLOT_GAP;
    private static final float ITEM_SCALE = 1.2F;
    private static final int BORDER_COLOR = 0xFFFFFFFF;
    private static final int HOVER_BORDER_COLOR = 0xFF66CCFF;
    private static final int INVALID_BORDER_COLOR = 0xFFFF5555;
    private static final int COUNT_COLOR = 0xFF55FF55;
    private static final int SCISSOR_MARGIN = 2;

    private final Screen parent;
    private EditBox searchBox;
    private int gridX;
    private int gridY;
    private int gridW;
    private int columns = 1;
    private int visibleRows = 1;
    private int gridH;

    private final GridScrollController gridScroll =
            new GridScrollController();

    private boolean compactToolbar;
    private final List<RecipeRecord> displayRecords = new ArrayList<>();

    public RecipePreviewScreen(Screen parent) {
        super(Component.translatable("gui.kineticrecipe.recipehud.manage.title"));
        this.parent = parent;

        configureResponsiveCanvas(
                640f,
                360f,
                6
        );

    }

    public void showToast(Component msg) {
        GuiToastUtil.showToast(msg);
    }

    public void refreshFromServer() {
        if (searchBox != null) {
            onSearchUpdate(searchBox.getValue());
        }
    }

    @Override
    protected void initScaled() {
        RecipePreviewState.returnToPreview = true;

        int sidePadding = 12;

        compactToolbar = false;

        int buttonY = 5;
        int backWidth = 60;
        int refreshWidth = 80;
        int toolbarGap = 6;

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "gui.kineticrecipe.recipehud.back"
                                ),
                                button -> {
                                    if (minecraft == null) {
                                        return;
                                    }

                                    if (parent != null) {
                                        minecraft.setScreen(parent);
                                    } else if (minecraft.player != null) {
                                        RecipeNetwork.requestOpenHub();
                                    }
                                }
                        )
                        .bounds(
                                sidePadding,
                                buttonY,
                                backWidth,
                                20
                        )
                        .build()
        );

        int refreshX =
                vWidth
                        - sidePadding
                        - refreshWidth;

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "gui.kineticrecipe.recipehud.preview.refresh"
                                ),
                                button -> RecipeNetwork.requestRecipeRecords()
                        )
                        .bounds(
                                refreshX,
                                buttonY,
                                refreshWidth,
                                20
                        )
                        .build()
        );

        int searchY;
        int searchX;
        int searchWidth;

        if (compactToolbar) {
            searchY = 31;
            searchX = sidePadding;
            searchWidth =
                    Math.max(
                            80,
                            vWidth
                                    - sidePadding * 2
                    );
            gridY = 59;
        } else {
            searchY = 5;

            int searchAreaStart =
                    sidePadding
                            + backWidth
                            + toolbarGap;

            int searchAreaEnd =
                    refreshX
                            - toolbarGap;

            int availableSearchWidth =
                    Math.max(
                            100,
                            searchAreaEnd - searchAreaStart
                    );

            searchWidth =
                    Math.max(
                            100,
                            Math.min(
                                    320,
                                    availableSearchWidth
                            )
                    );

            searchX =
                    searchAreaStart
                            + Math.max(
                                    0,
                                    (availableSearchWidth - searchWidth) / 2
                            );

            gridY = 35;
        }

        searchBox =
                new EditBox(
                        font,
                        searchX,
                        searchY,
                        searchWidth,
                        20,
                        Component.empty()
                );

        searchBox.setResponder(
                this::onSearchUpdate
        );

        searchBox.setValue(
                RecipePreviewState.searchQuery
        );

        addRenderableWidget(searchBox);

        int scrollbarReserve = 10;

        int availableGridWidth =
                Math.max(
                        SLOT_SIZE,
                        vWidth
                                - sidePadding * 2
                                - scrollbarReserve
                );

        columns =
                Math.max(
                        1,
                        (availableGridWidth + SLOT_GAP) / CELL_SIZE
                );

        gridW =
                columns * SLOT_SIZE
                        + Math.max(0, columns - 1) * SLOT_GAP;

        gridX =
                Math.max(
                        sidePadding,
                        (
                                vWidth
                                        - gridW
                                        - scrollbarReserve
                        ) / 2
                );

        int bottomPadding = 8;

        int availableGridHeight =
                Math.max(
                        SLOT_SIZE,
                        vHeight
                                - gridY
                                - bottomPadding
                );

        visibleRows =
                Math.max(
                        1,
                        (availableGridHeight + SLOT_GAP) / CELL_SIZE
                );

        gridH =
                visibleRows * SLOT_SIZE
                        + Math.max(0, visibleRows - 1) * SLOT_GAP;

        onSearchUpdate(
                searchBox.getValue()
        );

        gridScroll.restoreOffset(
                RecipePreviewState.scrollOffset
        );

        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );
    }

    @Override
    public void removed() {
        RecipePreviewState.scrollOffset =
                gridScroll.offset();

        RecipePreviewState.searchQuery =
                searchBox == null
                        ? ""
                        : searchBox.getValue();

        super.removed();
    }

    private void onSearchUpdate(String query) {
        displayRecords.clear();
        String lowerQuery = query.toLowerCase(Locale.ROOT).trim();
        List<RecipeRecord> validRecords = new ArrayList<>();
        List<RecipeRecord> invalidRecords = new ArrayList<>();

        for (RecipeRecord record : RecipeDatabase.records) {
            if (isDisplayableRecord(record) && matchesSearch(record, lowerQuery)) {
                validRecords.add(record);
            }
        }
        for (RecipeRecord record : RecipeDatabase.invalidRecords) {
            if (isDisplayableRecord(record) && matchesSearch(record, lowerQuery)) {
                invalidRecords.add(record);
            }
        }

        displayRecords.addAll(validRecords);
        displayRecords.addAll(invalidRecords);

        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );
    }

    private boolean matchesSearch(RecipeRecord record, String lowerQuery) {
        if (lowerQuery.isEmpty()) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(record.output.getItem());
        if (id == null) {
            return false;
        }
        if (lowerQuery.startsWith("@")) {
            return id.getNamespace().contains(lowerQuery.substring(1));
        }
        if (lowerQuery.startsWith("#")) {
            String tagQuery = lowerQuery.substring(1);
            return record.output.getTags().anyMatch(tag -> tag.location().toString().contains(tagQuery));
        }
        String name = record.output.getHoverName().getString().toLowerCase(Locale.ROOT);
        String searchStr = id + " " + name + " " + PinyinUtil.getSearchData(name);
        return AdvancedSearchUtil.match(searchStr, lowerQuery);
    }


    private boolean isDisplayableRecord(RecipeRecord record) {
        if (record == null || record.output == null || record.output.isEmpty()) {
            return false;
        }
        try {
            RecipeRegistry.EditorType.valueOf(record.editorType);
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(record.output.getItem());
            return id != null && ForgeRegistries.ITEMS.containsKey(id);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void renderScaledBackground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        graphics.fill(
                0,
                0,
                vWidth,
                vHeight,
                0xBB222222
        );

        if (gridW <= 0 || gridH <= 0) {
            return;
        }

        GuiRenderUtil.drawPanel(
                graphics,
                gridX - 2,
                gridY - 2,
                gridW + 4,
                gridH + 4,
                0x55000000,
                0xFF555555
        );

        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );

        int startIndex =
                gridScroll.offset()
                        * safeColumns();

        int endIndex =
                Math.min(
                        startIndex
                                + safeVisibleRows() * safeColumns(),
                        displayRecords.size()
                );

        enableVirtualScissor(
                graphics,
                gridX - SCISSOR_MARGIN,
                gridY - SCISSOR_MARGIN,
                gridX + gridW + SCISSOR_MARGIN,
                gridY + gridH + SCISSOR_MARGIN
        );

        for (int i = startIndex;
             i < endIndex;
             i++) {
            int localIndex =
                    i - startIndex;

            int column =
                    localIndex % safeColumns();

            int row =
                    localIndex / safeColumns();

            int x =
                    gridX
                            + column * CELL_SIZE;

            int y =
                    gridY
                            + row * CELL_SIZE;

            boolean hovered = mouseX >= x
                    && mouseX < x + SLOT_SIZE
                    && mouseY >= y
                    && mouseY < y + SLOT_SIZE;

            RecipeRecord record =
                    displayRecords.get(i);

            AdaptiveItemGridRenderer.drawSlot(
                    graphics,
                    record.output,
                    x,
                    y,
                    SLOT_SIZE,
                    4,
                    false
            );

            graphics.renderOutline(
                    x,
                    y,
                    SLOT_SIZE,
                    SLOT_SIZE,
                    record.invalidConfig
                            ? INVALID_BORDER_COLOR
                            : (hovered ? HOVER_BORDER_COLOR : BORDER_COLOR)
            );

            AdaptiveItemGridRenderer.renderItem(
                    graphics,
                    font,
                    record.output,
                    x,
                    y,
                    SLOT_SIZE,
                    ITEM_SCALE,
                    false
            );

            renderGreenCount(
                    graphics,
                    record.output,
                    x,
                    y
            );

        }

        graphics.disableScissor();

        gridScroll.render(
                graphics,
                mouseX,
                mouseY,
                gridX + gridW + 4,
                gridY,
                6,
                gridH,
                20,
                0xFF222222,
                0xFF555555,
                0xFF777777
        );
    }

    @Override
    protected void renderScaledForeground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (searchBox != null
                && !searchBox.isFocused()
                && searchBox.getValue().isEmpty()) {
            graphics.drawString(
                    font,
                    Component.translatable(
                            "gui.kineticrecipe.recipehud.search_hint"
                    ),
                    searchBox.getX() + 6,
                    searchBox.getY() + 6,
                    0xFFAAAAAA,
                    false
            );
        }
    }

    @Override
    protected void renderTooltips(
            GuiGraphics graphics,
            int scaledMouseX,
            int scaledMouseY,
            int mouseX,
            int mouseY
    ) {
        if (gridW <= 0 || gridH <= 0) {
            return;
        }

        if (scaledMouseX < gridX
                || scaledMouseX >= gridX + gridW
                || scaledMouseY < gridY
                || scaledMouseY >= gridY + gridH) {
            return;
        }

        int index =
                recordIndexAt(
                        scaledMouseX,
                        scaledMouseY
                );

        if (index < 0
                || index >= displayRecords.size()) {
            return;
        }

        RecipeRecord record =
                displayRecords.get(index);

        RecipeRegistry.EditorType editorType =
                RecipeRegistry.EditorType.valueOf(
                        record.editorType
                );

        List<Component> tooltip =
                new ArrayList<>();

        tooltip.add(
                Component.literal("[")
                        .append(
                                editorType.getTitle()
                        )
                        .append("] ")
                        .append(
                                record.output.getHoverName()
                        )
        );

        tooltip.add(
                Component.empty()
        );

        if (record.invalidConfig) {
            tooltip.add(
                    Component.translatable(
                            "gui.kineticrecipe.recipehud.tooltip.invalid_recipe.colored"
                    )
            );
        }

        tooltip.add(
                Component.translatable(
                        "gui.kineticrecipe.recipehud.tooltip.left_edit.colored"
                )
        );

        tooltip.add(
                Component.translatable(
                        "gui.kineticrecipe.recipehud.tooltip.right_delete.colored"
                )
        );

        graphics.pose().pushPose();
        graphics.pose().translate(
                mouseX,
                mouseY,
                500
        );
        graphics.pose().scale(
                guiScale,
                guiScale,
                1.0F
        );
        graphics.pose().translate(
                -mouseX,
                -mouseY,
                0
        );
        graphics.renderComponentTooltip(
                font,
                tooltip,
                mouseX,
                mouseY
        );
        graphics.pose().popPose();
    }

    private boolean sameRecord(RecipeRecord left, RecipeRecord right) {
        if (left == null || right == null) {
            return false;
        }
        if (right.configIndex >= 0) {
            return left.configIndex == right.configIndex;
        }
        return left.uuid != null && left.uuid.equals(right.uuid);
    }

    private void renderGreenCount(
            GuiGraphics graphics,
            net.minecraft.world.item.ItemStack stack,
            int x,
            int y
    ) {
        if (stack == null || stack.isEmpty() || stack.getCount() <= 1) {
            return;
        }

        String countText = String.valueOf(stack.getCount());
        int textX = x + SLOT_SIZE - font.width(countText) - 1;
        int textY = y + SLOT_SIZE - font.lineHeight;

        graphics.pose().pushPose();
        graphics.pose().translate(
                0,
                0,
                250
        );
        graphics.drawString(
                font,
                countText,
                textX,
                textY,
                COUNT_COLOR,
                true
        );
        graphics.pose().popPose();
    }

    private int recordIndexAt(double mouseX, double mouseY) {
        if (gridW <= 0 || gridH <= 0
                || mouseX < gridX
                || mouseX >= gridX + gridW
                || mouseY < gridY
                || mouseY >= gridY + gridH) {
            return -1;
        }

        int relativeX = (int) Math.floor(mouseX - gridX);
        int relativeY = (int) Math.floor(mouseY - gridY);
        int column = relativeX / CELL_SIZE;
        int row = relativeY / CELL_SIZE;

        if (column < 0 || column >= safeColumns()
                || row < 0 || row >= safeVisibleRows()
                || relativeX % CELL_SIZE >= SLOT_SIZE
                || relativeY % CELL_SIZE >= SLOT_SIZE) {
            return -1;
        }

        return gridScroll.offset()
                * safeColumns()
                + row * safeColumns()
                + column;
    }

    private int totalRows() {
        int activeColumns = safeColumns();
        return (
                displayRecords.size()
                        + activeColumns
                        - 1
        ) / activeColumns;
    }

    private int safeColumns() {
        return Math.max(1, columns);
    }

    private int safeVisibleRows() {
        return Math.max(1, visibleRows);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        RecipeEditSessionState.applyPendingAndClear();
    }

    @Override
    protected boolean universalMouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (searchBox != null
                && !searchBox.isMouseOver(
                        mouseX,
                        mouseY
                )) {
            searchBox.setFocused(false);
        }

        if (super.universalMouseClicked(
                mouseX,
                mouseY,
                button
        )) {
            return true;
        }

        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );

        if (button == 0
                && gridScroll.beginDrag(
                        mouseX,
                        mouseY,
                        gridX + gridW + 4,
                        gridY,
                        6,
                        gridH,
                        20,
                        0
                )) {
            return true;
        }

        if ((button != 0 && button != 1)
                || mouseX < gridX
                || mouseX >= gridX + gridW
                || mouseY < gridY
                || mouseY >= gridY + gridH) {
            return false;
        }

        int index =
                recordIndexAt(
                        mouseX,
                        mouseY
                );

        if (index < 0
                || index >= displayRecords.size()) {
            return false;
        }

        RecipeRecord record =
                displayRecords.get(index);

        if (button == 0) {
            RecipeNetwork.CHANNEL.sendToServer(
                    new RecipeNetwork.RequestEditPacket(
                            record.uuid,
                            record.editorType,
                            record.configIndex
                    )
            );

            return true;
        }

        RecipeNetwork.sendRecipeChange(
                new RecipeNetwork.RecipeChangePacket(
                        record.uuid,
                        record.configIndex,
                        record.editorType,
                        record.isShapeless,
                        record.inputModes,
                        record.outputUseNbt,
                        1,
                        record.inputs,
                        record.output
                )
        );

        RecipeEditSessionState.markPendingRecipeApply();

        RecipeDatabase.records.removeIf(
                value -> sameRecord(value, record)
        );
        RecipeDatabase.invalidRecords.removeIf(
                value -> sameRecord(value, record)
        );

        displayRecords.remove(index);

        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );

        return true;
    }

    @Override
    protected boolean universalMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (gridScroll.drag(
                mouseY,
                gridY,
                gridH,
                20
        )) {
            return true;
        }

        return super.universalMouseDragged(
                mouseX,
                mouseY,
                button,
                dragX,
                dragY
        );
    }

    @Override
    protected boolean universalMouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (gridScroll.release(button)) {
            return true;
        }

        return super.universalMouseReleased(
                mouseX,
                mouseY,
                button
        );
    }

    @Override
    protected boolean universalMouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );

        return gridScroll.scroll(delta)
                || super.universalMouseScrolled(
                        mouseX,
                        mouseY,
                        delta
                );
    }
}
